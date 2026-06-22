package com.banking.gateway.lb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ================================================================
 *  Least Connections Load Balancer — custom pour Transaction Service
 * ================================================================
 *
 *  POURQUOI ce LB et pas le Round-Robin par défaut ?
 *  -------------------------------------------------
 *  Le Round-Robin distribue les requêtes de façon égale (1, 2, 3, 1, 2, 3...).
 *  C'est parfait quand toutes les requêtes durent à peu près le même temps.
 *
 *  Mais pour le Transfer Service, les durées varient énormément :
 *    - Un transfert intra-opérateur  → ~50ms
 *    - Un transfert inter-opérateurs → ~3-8s (saga pattern, Kafka, compensation)
 *
 *  Avec Round-Robin, une instance peut se retrouver avec 20 transferts
 *  inter-opérateurs lents pendant qu'une autre est idle.
 *
 *  Least Connections résout ça : chaque nouvelle requête va toujours
 *  vers l'instance qui a LE MOINS de travail en cours, quelle que
 *  soit la durée de ce travail.
 *
 *  PRINCIPE DE FONCTIONNEMENT :
 *  ----------------------------
 *  1. Eureka fournit la liste des instances disponibles du service.
 *  2. Pour chaque instance, on maintient un compteur de connexions actives.
 *  3. On choisit l'instance avec le compteur le plus bas.
 *  4. On incrémente son compteur (+1) quand la requête commence.
 *  5. On décrémente son compteur (-1) quand la requête se termine.
 *
 *  THREAD SAFETY :
 *  ---------------
 *  ConcurrentHashMap + AtomicInteger garantissent que les compteurs
 *  sont corrects même si des milliers de requêtes arrivent en parallèle.
 *  Aucun bloc synchronized nécessaire.
 */
public class LeastConnectionsLB implements ReactorServiceInstanceLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(LeastConnectionsLB.class);

    /** Nom du service géré par ce LB (ex: "transaction-service"). */
    private final String serviceId;

    /**
     * Fournisseur de la liste d'instances.
     * C'est lui qui interroge Eureka pour obtenir les instances
     * disponibles du service à chaque requête.
     * ObjectProvider est l'API correcte de Spring pour l'injection
     * lazy — elle évite les dépendances circulaires au démarrage.
     */
    private final ObjectProvider<ServiceInstanceListSupplier> supplierObjectProvider;

    /**
     * Map thread-safe : instanceId → nombre de connexions actives.
     * Clé   : "host:port"  ex: "10.0.0.5:8083"
     * Valeur: AtomicInteger (compteur thread-safe sans synchronized)
     *
     * ConcurrentHashMap : lectures/écritures concurrentes sans verrou global.
     * AtomicInteger     : incréments/décréments atomiques (pas de race condition).
     */
    private final Map<String, AtomicInteger> activeConnections = new ConcurrentHashMap<>();

    /**
     * Constructeur — appelé par LeastConnectionsLBConfig.
     *
     * @param supplierObjectProvider fournisseur Spring des instances Eureka
     * @param serviceId              nom du service dans Eureka
     */
    public LeastConnectionsLB(
            ObjectProvider<ServiceInstanceListSupplier> supplierObjectProvider,
            String serviceId) {
        this.supplierObjectProvider = supplierObjectProvider;
        this.serviceId = serviceId;
    }

    /**
     * Point d'entrée du load balancer.
     * Appelé par Spring Cloud Gateway à chaque requête entrante.
     * Retourne un Mono<Response<ServiceInstance>> car Gateway est réactif (WebFlux).
     */
    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        // 1. Récupérer le fournisseur d'instances (ou lancer une exception claire)
        ServiceInstanceListSupplier supplier = supplierObjectProvider
                .getIfAvailable();

        if (supplier == null) {
            log.error("[LC][{}] Aucun ServiceInstanceListSupplier disponible.", serviceId);
            return Mono.just(new EmptyResponse());
        }

        // 2. Obtenir la liste des instances depuis Eureka (flux réactif)
        //    .next() prend le premier élément du flux (la liste d'instances)
        return supplier.get(request)
                .next()
                .map(this::chooseLeastLoaded)
                // Si Eureka renvoie une liste vide ou une erreur
                .defaultIfEmpty(new EmptyResponse());
    }

    /**
     * Algorithme de sélection : choisir l'instance la moins chargée.
     *
     * @param instances liste des instances disponibles fournie par Eureka
     * @return Response wrappant l'instance choisie, ou EmptyResponse si aucune
     */
    private Response<ServiceInstance> chooseLeastLoaded(List<ServiceInstance> instances) {

        // Cas défensif : liste vide (toutes les instances sont down)
        if (instances == null || instances.isEmpty()) {
            log.warn("[LC][{}] Aucune instance disponible dans Eureka.", serviceId);
            return new EmptyResponse();
        }

        // Parcourir toutes les instances pour trouver celle avec le moins de connexions
        ServiceInstance bestInstance = null;
        int minConnections = Integer.MAX_VALUE;

        for (ServiceInstance instance : instances) {
            String id = buildInstanceId(instance);
            int currentCount = getActiveConnectionCount(id);

            log.debug("[LC][{}] Instance {}:{} → {} connexions actives",
                    serviceId, instance.getHost(), instance.getPort(), currentCount);

            if (currentCount < minConnections) {
                minConnections = currentCount;
                bestInstance = instance;
            }
        }

        // Ne devrait pas arriver si la liste n'est pas vide, mais sécurité défensive
        if (bestInstance == null) {
            return new EmptyResponse();
        }

        // Incrémenter le compteur de l'instance choisie AVANT d'envoyer la requête.
        // computeIfAbsent : crée un AtomicInteger(0) si l'instance est vue pour
        // la première fois (nouveau pod Kubernetes démarré).
        String chosenId = buildInstanceId(bestInstance);
        activeConnections
                .computeIfAbsent(chosenId, k -> new AtomicInteger(0))
                .incrementAndGet();

        log.info("[LC][{}] → Choix: {}:{} (connexions actives avant: {})",
                serviceId,
                bestInstance.getHost(),
                bestInstance.getPort(),
                minConnections);

        // Retourner une Response personnalisée qui décrémentera
        // automatiquement le compteur quand la requête sera terminée.
        return new LeastConnectionsResponse(bestInstance, chosenId, activeConnections);
    }

    /**
     * Retourne le nombre de connexions actives pour une instance.
     * Retourne 0 si l'instance n'a jamais été vue (nouveau pod).
     */
    private int getActiveConnectionCount(String instanceId) {
        AtomicInteger counter = activeConnections.get(instanceId);
        return (counter != null) ? counter.get() : 0;
    }

    /**
     * Construit un identifiant unique pour une instance.
     * Format : "host:port"  ex: "10.0.0.5:8083"
     *
     * On n'utilise pas instanceId de ServiceInstance car il peut
     * être null selon l'implémentation du service registry.
     */
    private String buildInstanceId(ServiceInstance instance) {
        return instance.getHost() + ":" + instance.getPort();
    }


    // ════════════════════════════════════════════════════════
    //  INNER CLASS : LeastConnectionsResponse
    // ════════════════════════════════════════════════════════

    /**
     * Response personnalisée qui étend DefaultResponse.
     *
     * Son rôle crucial : décrémenter le compteur de connexions
     * actives quand la requête HTTP se termine (succès ou échec).
     *
     * Sans ce décrément, les compteurs ne feraient qu'augmenter
     * et l'algorithme deviendrait inutile après quelques minutes.
     *
     * IMPORTANT : onComplete() doit être appelé explicitement
     * par le filtre Gateway après que la réponse soit envoyée
     * au client. Voir LeastConnectionsFilter.java.
     */
    static class LeastConnectionsResponse extends DefaultResponse {

        private final String instanceId;

        /**
         * Référence à la map partagée du LB parent.
         * C'est la MÊME map — pas une copie.
         * Modifier connections ici modifie bien les compteurs du LB.
         */
        private final Map<String, AtomicInteger> connections;

        LeastConnectionsResponse(
                ServiceInstance instance,
                String instanceId,
                Map<String, AtomicInteger> connections) {
            super(instance);
            this.instanceId = instanceId;
            this.connections = connections;
        }

        /**
         * À appeler impérativement quand la requête est terminée.
         * Décrémente le compteur de l'instance pour libérer
         * le "slot" et permettre de nouvelles requêtes.
         *
         * La vérification (remaining < 0) est une sécurité :
         * si onComplete() est appelé deux fois par erreur,
         * on évite un compteur négatif qui fausserait l'algo.
         */
        public void onComplete() {
            AtomicInteger counter = connections.get(instanceId);
            if (counter != null) {
                int remaining = counter.decrementAndGet();
                if (remaining < 0) {
                    // Ne devrait jamais arriver en production.
                    // Si ça arrive, c'est un bug dans l'appelant.
                    log.warn("[LC] Compteur négatif détecté pour {}. Reset à 0.", instanceId);
                    counter.set(0);
                }
            }
        }

        // Logger statique accessible depuis la inner class
        private static final Logger log = LoggerFactory.getLogger(LeastConnectionsResponse.class);
    }
}