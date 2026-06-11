package com.banking.customer_service.kafka;

import com.banking.customer_service.config.KafkaConfig;
import com.banking.customer_service.entity.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCustomerRegistered(Client client) {
        var event = new CustomerEvent.CustomerRegistered(
                client.getClientId(),
                client.getNumeroClient(),
                client.getContact().getEmail(),
                client.getNom(),
                client.getPrenom(),
                LocalDateTime.now()
        );
        kafkaTemplate.send(KafkaConfig.TOPIC_CUSTOMER_REGISTERED,
                client.getClientId().toString(), event);
        log.info("[KAFKA] customer.registered publié pour client {}", client.getNumeroClient());
    }

    public void publishKycSubmitted(Long dossierId, Client client, java.util.List<Long> documentIds) {
        var event = new CustomerEvent.KycSubmitted(
                dossierId,
                client.getClientId(),
                client.getNom(),
                client.getPrenom(),
                client.getDateNaissance(),
                documentIds,
                LocalDateTime.now()
        );
        kafkaTemplate.send(KafkaConfig.TOPIC_KYC_SUBMITTED,
                dossierId.toString(), event);
        log.info("[KAFKA] kyc.submitted publié pour dossier {}", dossierId);
    }

    public void publishCustomerStatusChanged(Client client, String ancienStatut, String raison) {
        var event = new CustomerEvent.CustomerStatusChanged(
                client.getClientId(),
                ancienStatut,
                client.getStatut().name(),
                raison,
                LocalDateTime.now()
        );
        kafkaTemplate.send(KafkaConfig.TOPIC_CUSTOMER_SUSPENDED,
                client.getClientId().toString(), event);
        log.info("[KAFKA] customer.suspended publié pour client {}", client.getClientId());
    }
}