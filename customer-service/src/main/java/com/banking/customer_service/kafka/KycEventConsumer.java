package com.banking.customer_service.kafka;

import com.banking.customer_service.config.KafkaConfig;
import com.banking.customer_service.enums.StatutClient;
import com.banking.customer_service.repository.ClientRepository;
import com.banking.customer_service.repository.DossierKYCRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class KycEventConsumer {

    private final DossierKYCRepository dossierRepo;
    private final ClientRepository clientRepository;

    /**
     * Reçoit le résultat OCR publié par le document-service
     * Payload attendu : { dossierId, coherent, commentaire }
     */
    @KafkaListener(topics = KafkaConfig.TOPIC_KYC_PROCESSED,
            groupId = "customer-service-group")
    @Transactional
    public void onKycDocumentProcessed(Map<String, Object> payload) {
        log.info("[KAFKA] kyc.document.processed reçu : {}", payload);

        Long dossierId = Long.valueOf(payload.get("dossier_id").toString());
        boolean coherent = Boolean.parseBoolean(payload.get("coherent").toString());
        String commentaire = payload.getOrDefault("commentaire", "").toString();

        var dossier = dossierRepo.findById(dossierId).orElse(null);
        if (dossier == null) {
            log.error("Dossier KYC {} introuvable", dossierId);
            return;
        }

        if (coherent) {
            dossier.approuver();
            // Activer le client
            var client = dossier.getClient();
            client.setStatut(StatutClient.ACTIF);
            clientRepository.save(client);
            log.info("Client {} activé après KYC approuvé", client.getNumeroClient());
        } else {
            dossier.rejeter(commentaire);
            log.warn("Dossier KYC {} rejeté : {}", dossierId, commentaire);
        }

        dossierRepo.save(dossier);
    }
}