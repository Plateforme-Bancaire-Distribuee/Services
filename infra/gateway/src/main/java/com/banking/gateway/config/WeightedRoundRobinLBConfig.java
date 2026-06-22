package com.banking.gateway.config;

import com.banking.gateway.lb.WeightedRoundRobinLB;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ================================================================
 *  Configuration du Weighted Round Robin LB — Customer Service
 * ================================================================
 *
 *  Active ce LB uniquement pour "customer-service".
 *  Tous les autres services continuent avec le Round-Robin par défaut.
 *
 *  Pour activer sur plusieurs services :
 *
 *  @LoadBalancerClients({
 *    @LoadBalancerClient(name = "customer-service",  configuration = WeightedRoundRobinLBConfig.class),
 *    @LoadBalancerClient(name = "operator-service",  configuration = WeightedRoundRobinLBConfig.class)
 *  })
 */
@Configuration
@LoadBalancerClient(name = "customer-service", configuration = WeightedRoundRobinLBConfig.class)
public class WeightedRoundRobinLBConfig {

    @Bean
    public WeightedRoundRobinLB weightedRoundRobinLoadBalancer(
            ObjectProvider<ServiceInstanceListSupplier> supplierObjectProvider) {
        return new WeightedRoundRobinLB(supplierObjectProvider, "customer-service");
    }
}