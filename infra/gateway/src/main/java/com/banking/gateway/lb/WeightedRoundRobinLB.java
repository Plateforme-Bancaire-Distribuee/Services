package com.banking.gateway.lb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ================================================================
 *  Weighted Round Robin Load Balancer — custom pour Customer Service
 * ================================================================
 *
 *  POURQUOI ce LB ?
 *  ----------------
 *  Le Round-Robin standard traite toutes les instances comme égales.
 *  Or dans un cluster Kubernetes, les pods n'ont pas forcément les
 *  mêmes ressources allouées (CPU, RAM) — certains pods peuvent être
 *  sur des nodes plus puissants.
 *
 *  Le Weighted Round Robin permet d'affecter un POIDS à chaque instance.
 *  Une instance de poids 3 reçoit 3× plus de requêtes qu'une de poids 1.
 *
 *  Exemple concret :
 *    Pod A (node puissant) → poids 3 → reçoit 3 requêtes sur 4
 *    Pod B (node standard) → poids 1 → reçoit 1 requête sur 4
 *    Séquence : A, A, A, B, A, A, A, B, ...
 *
 *  CONFIGURATION DU POIDS :
 *  ------------------------
 *  Le poids est lu depuis les métadonnées Eureka de chaque instance.
 *  Dans application.yml du microservice cible :
 *
 *    eureka:
 *      instance:
 *        metadata-map:
 *          weight: 3     ← pod puissant
 *
 *  Si la métadonnée est absente → poids par défaut = 1.
 *
 *  ALGORITHME :
 *  ------------
 *  1. Construire un "pool pondéré" : une liste où chaque instance
 *     apparaît autant de fois que son poids.
 *     Ex: [A, A, A, B] pour poids A=3, B=1
 *  2. Utiliser un compteur atomique (position) pour tourner dans ce pool.
 *  3. idx = position % taille_du_pool → distribue uniformément.
 *
 *  THREAD SAFETY :
 *  ---------------
 *  AtomicInteger.getAndIncrement() est atomique → pas de race condition
 *  même sous forte charge concurrente.
 *  ConcurrentHashMap pour le cache des poids → lectures/écritures sûres.
 *
 *  LIMITES :
 *  ---------
 *  Le pool pondéré est reconstruit à chaque requête si les instances
 *  changent (scale up/down Kubernetes). Pour optimiser, on pourrait
 *  mettre le pool en cache et l'invalider sur changement d'instances.
 */
public class WeightedRoundRobinLB implements ReactorServiceInstanceLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(WeightedRoundRobinLB.class);

    /**
     * Poids par défaut si la métadonnée "weight" est absente.
     * Garantit que toutes les instances participent même sans config explicite.
     */
    private static final int DEFAULT_WEIGHT = 1;

    /** Nom du service géré (ex: "customer-service"). */
    private final String serviceId;

    /**
     * Fournisseur lazy des instances Eureka.
     * ObjectProvider est l'API Spring correcte pour éviter les
     * dépendances circulaires au démarrage du contexte LoadBalancer.
     *
     * CORRECTION : l'original utilisait
     * LazyLoadBalancerClientFactory.Lazy<> qui n'existe pas.
     * ObjectProvider<ServiceInstanceListSupplier> est l'équivalent
     * correct fourni par Spring Framework.
     */
    private final ObjectProvider<ServiceInstanceListSupplier> supplierObjectProvider;

    /**
     * Compteur global de requêtes — thread-safe via AtomicInteger.
     * Tourne en permanence : 0, 1, 2, ..., MAX_INT, puis repart à 0.
     * Math.abs() dans choose() évite les indices négatifs au wraparound.
     */
    private final AtomicInteger position = new AtomicInteger(0);

    /**
     * Cache des poids par instanceId pour éviter de re-parser
     * les métadonnées Eureka à chaque requête.
     * Clé   : "host:port"  ex: "10.0.0.3:8081"
     * Valeur: poids entier  ex: 3
     */
    private final Map<String, Integer> weightCache = new ConcurrentHashMap<>();

    /**
     * Constructeur — appelé par WeightedRoundRobinLBConfig.
     *
     * @param supplierObjectProvider fournisseur Spring des instances Eureka
     * @param serviceId              nom du service dans Eureka
     */
    public WeightedRoundRobinLB(
            ObjectProvider<ServiceInstanceListSupplier> supplierObjectProvider,
            String serviceId) {
        this.supplierObjectProvider = supplierObjectProvider;
        this.serviceId = serviceId;
    }

    /**
     * Point d'entrée du load balancer.
     * Appelé par Spring Cloud Gateway à chaque requête entrante.
     * Retourne un Mono car Gateway fonctionne en mode réactif (WebFlux).
     */
    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        // Récupérer le fournisseur d'instances (lazy, peut être null si non initialisé)
        ServiceInstanceListSupplier supplier = supplierObjectProvider.getIfAvailable();

        if (supplier == null) {
            log.error("[WRR][{}] Aucun ServiceInstanceListSupplier disponible.", serviceId);
            return Mono.just(new EmptyResponse());
        }

        // Obtenir la liste des instances depuis Eureka (flux réactif)
        // .next() prend le premier élément émis par le flux (la liste d'instances)
        return supplier.get(request)
                .next()
                .map(this::processAndChoose)
                // Sécurité : si le flux est vide (Eureka down), retourner EmptyResponse
                .defaultIfEmpty(new EmptyResponse());
    }

    /**
     * Construit le pool pondéré et sélectionne l'instance suivante.
     *
     * @param instances liste des instances disponibles fournie par Eureka
     * @return Response wrappant l'instance choisie, ou EmptyResponse si aucune
     */
    private Response<ServiceInstance> processAndChoose(List<ServiceInstance> instances) {

        // Cas défensif : aucune instance disponible (toutes en panne ou Eureka vide)
        if (instances == null || instances.isEmpty()) {
            log.warn("[WRR][{}] Aucune instance disponible dans Eureka.", serviceId);
            return new EmptyResponse();
        }

        // Étape 1 : construire le pool pondéré
        // [A, A, A, B] si poids(A)=3 et poids(B)=1
        List<ServiceInstance> weightedPool = buildWeightedPool(instances);

        // Étape 2 : Round Robin sur le pool pondéré
        // Math.abs() protège contre les valeurs négatives si position
        // dépasse Integer.MAX_VALUE et revient à négatif (wraparound).
        int idx = Math.abs(position.getAndIncrement() % weightedPool.size());
        ServiceInstance chosen = weightedPool.get(idx);

        log.debug("[WRR][{}] Choix: {}:{} (idx={}, pool={})",
                serviceId,
                chosen.getHost(), chosen.getPort(),
                idx,
                weightedPool.size());

        return new DefaultResponse(chosen);
    }

    /**
     * Construit la liste pondérée à partir des instances Eureka.
     *
     * Chaque instance est répétée autant de fois que son poids :
     *   poids 1 → 1 occurrence  (instance standard)
     *   poids 3 → 3 occurrences (instance puissante)
     *
     * Le Round Robin sur cette liste produit naturellement
     * la distribution souhaitée.
     *
     * @param instances instances disponibles depuis Eureka
     * @return pool pondéré (peut être plus grand que instances)
     */
    private List<ServiceInstance> buildWeightedPool(List<ServiceInstance> instances) {
        List<ServiceInstance> pool = new ArrayList<>();

        for (ServiceInstance instance : instances) {
            int weight = getWeight(instance);

            // Ajouter l'instance 'weight' fois dans le pool
            for (int i = 0; i < weight; i++) {
                pool.add(instance);
            }

            log.debug("[WRR][{}] Instance {}:{} → poids {}",
                    serviceId, instance.getHost(), instance.getPort(), weight);
        }

        return pool;
    }

    /**
     * Lit le poids d'une instance depuis ses métadonnées Eureka.
     *
     * Utilise le cache weightCache pour éviter de re-parser à chaque requête.
     * Le cache est invalidé implicitement si l'instance change (nouveau pod)
     * car la clé "host:port" sera différente.
     *
     * Métadonnée attendue dans le microservice cible :
     *   eureka.instance.metadata-map.weight=3
     *
     * @param instance instance Eureka
     * @return poids ≥ 1 (minimum garanti par Math.max)
     */
    private int getWeight(ServiceInstance instance) {
        // Clé de cache : "host:port" — unique par pod Kubernetes
        String cacheKey = instance.getHost() + ":" + instance.getPort();

        // computeIfAbsent : lire et parser la métadonnée UNE SEULE FOIS
        // puis mettre en cache pour les requêtes suivantes
        return weightCache.computeIfAbsent(cacheKey, k -> {
            String weightStr = instance.getMetadata()
                    .getOrDefault("weight", String.valueOf(DEFAULT_WEIGHT));
            try {
                int w = Integer.parseInt(weightStr.trim());
                // Math.max(1, w) : un poids de 0 ou négatif n'a pas de sens,
                // on force le minimum à 1 pour que l'instance soit quand même servie.
                return Math.max(1, w);
            } catch (NumberFormatException e) {
                // Métadonnée présente mais non parseable (ex: "heavy" au lieu de "3")
                log.warn("[WRR][{}] Métadonnée weight invalide '{}' pour {}. Utilisation du défaut={}.",
                        serviceId, weightStr, cacheKey, DEFAULT_WEIGHT);
                return DEFAULT_WEIGHT;
            }
        });
    }
}