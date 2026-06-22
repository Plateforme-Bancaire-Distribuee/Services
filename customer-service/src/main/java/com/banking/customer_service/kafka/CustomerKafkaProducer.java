package com.banking.customer_service.kafka;

import com.banking.customer_service.dto.request.CustomerCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishCustomerCreated(CustomerCreatedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("customer-created", message);
            log.info("Événement publié sur Kafka : {}", message);
        } catch (Exception e) {
            log.error("Erreur Kafka : {}", e.getMessage());
        }
    }
}
