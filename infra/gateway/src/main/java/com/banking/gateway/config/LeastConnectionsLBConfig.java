package com.banking.gateway.config;

import com.banking.gateway.lb.LeastConnectionsLB;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

/**
 * ================================================================
 *  Configuration du Least Connections LB pour Transaction Service
 * ================================================================
 *
 *  @LoadBalancerClient(name = "transaction-service", ...)
 *  → Active ce LB UNIQUEMENT pour "transaction-service".
 *  → Tous les autres services continuent d'utiliser le
 *    Round-Robin par défaut de Spring Cloud LoadBalancer.
 *
 *  Si tu veux l'activer pour plusieurs services :
 *
 *  @LoadBalancerClients({
 *      @LoadBalancerClient(name = "transaction-service", configuration = LeastConnectionsLBConfig.class),
 *      @LoadBalancerClient(name = "loan-service",        configuration = LeastConnectionsLBConfig.class)
 *  })
 */
@Configuration
@LoadBalancerClient(name = "transaction-service", configuration = LeastConnectionsLBConfig.class)
public class LeastConnectionsLBConfig {

    /**
     * Déclare le bean LeastConnectionsLB pour "transaction-service".
     *
     * Spring injecte automatiquement :
     *  - ObjectProvider<ServiceInstanceListSupplier> : accès lazy à Eureka
     *  - serviceId : le nom du service ("transaction-service")
     *
     * ObjectProvider est préféré à une injection directe car il évite
     * les problèmes de dépendances circulaires au démarrage du contexte
     * Spring Cloud LoadBalancer.
     */
    @Bean
    public LeastConnectionsLB leastConnectionsLoadBalancer(
            ObjectProvider<ServiceInstanceListSupplier> supplierObjectProvider) {
        return new LeastConnectionsLB(supplierObjectProvider, "transaction-service");
    }
}