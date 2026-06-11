package com.banking.customer_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_CUSTOMER_REGISTERED = "customer.registered";
    public static final String TOPIC_KYC_SUBMITTED       = "kyc.submitted";
    public static final String TOPIC_KYC_PROCESSED       = "kyc.document.processed";
    public static final String TOPIC_CUSTOMER_SUSPENDED  = "customer.suspended";

    @Bean
    public NewTopic customerRegisteredTopic() {
        return TopicBuilder.name(TOPIC_CUSTOMER_REGISTERED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic kycSubmittedTopic() {
        return TopicBuilder.name(TOPIC_KYC_SUBMITTED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic kycProcessedTopic() {
        return TopicBuilder.name(TOPIC_KYC_PROCESSED)
                .partitions(3).replicas(1).build();
    }
}