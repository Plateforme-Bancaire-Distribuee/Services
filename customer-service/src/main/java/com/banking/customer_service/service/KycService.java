package com.banking.customer_service.service;

import com.banking.customer_service.config.DocumentServiceClient;
import com.banking.customer_service.dto.request.KycSubmitRequest;
import com.banking.customer_service.dto.response.KycResponse;
import com.banking.customer_service.entity.*;
import com.banking.customer_service.enums.StatutDossier;
import com.banking.customer_service.exception.CustomerNotFoundException;
import com.banking.customer_service.exception.KycException;
import com.banking.customer_service.kafka.CustomerEventPublisher;
import com.banking.customer_service.repository.ClientRepository;
import com.banking.customer_service.repository.DossierKYCRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    private final DossierKYCRepository dossierRepo;
    private final ClientRepository clientRepo;
    private final DocumentServiceClient documentServiceClient;
    private final CustomerEventPublisher eventPublisher;

    @Transactional
    public KycResponse ouvrirDossier(User currentUser) {
        var client = clientRepo.findByUserIdWithDetails(currentUser.getId())
                .orElseThrow(() -> new CustomerNotFoundException("Client introuvable"));

        // Un seul dossier EN_ATTENTE ou EN_COURS autorisé à la fois
        boolean dossierActif = dossierRepo.existsByClient_ClientIdAndStatut(
                client.getClientId(), StatutDossier.EN_ATTENTE);
        boolean dossierEnCours = dossierRepo.existsByClient_ClientIdAndStatut(
                client.getClientId(), StatutDossier.EN_COURS);

        if (dossierActif || dossierEnCours) {
            throw new KycException("Un dossier KYC est déjà en cours pour ce client");
        }

        var dossier = DossierKYC.builder().client(client).build();
        dossier = dossierRepo.save(dossier);

        log.info("Dossier KYC {} ouvert pour client {}", dossier.getDossierId(), client.getNumeroClient());
        return KycResponse.from(dossier);
    }

    @Transactional
    @CircuitBreaker(name = "documentService", fallbackMethod = "submitDossierFallback")
    public KycResponse soumettreDossier(User currentUser, Long dossierId, KycSubmitRequest req) {
        var client = clientRepo.findByUserIdWithDetails(currentUser.getId())
                .orElseThrow(() -> new CustomerNotFoundException("Client introuvable"));

        var dossier = dossierRepo.findById(dossierId)
                .orElseThrow(() -> new KycException("Dossier KYC " + dossierId + " introuvable"));

        // Vérifier que le dossier appartient bien au client connecté
        if (!dossier.getClient().getClientId().equals(client.getClientId())) {
            throw new KycException("Ce dossier ne vous appartient pas");
        }

        if (dossier.getStatut() != StatutDossier.EN_ATTENTE) {
            throw new KycException("Le dossier ne peut pas être soumis dans son état actuel : " + dossier.getStatut());
        }

        // Déclencher l'OCR sur chaque document via document-service
        req.documentIds().forEach(docId -> {
            try {
                var ocrReq = new DocumentServiceClient.OcrTriggerRequest(
                        client.getNom(),
                        client.getPrenom(),
                        client.getDateNaissance()
                );
                documentServiceClient.triggerOcr(docId, ocrReq);
                log.info("OCR déclenché pour document {}", docId);
            } catch (Exception e) {
                log.error("Erreur lors du déclenchement OCR pour document {} : {}", docId, e.getMessage());
                // On continue pour les autres documents même si un échoue
            }
        });

        dossier.setDocumentIds(req.documentIds());
        dossier.soumettre();
        dossier = dossierRepo.save(dossier);

        // Publier event Kafka (le résultat reviendra via kyc.document.processed)
        eventPublisher.publishKycSubmitted(dossierId, client, req.documentIds());

        log.info("Dossier KYC {} soumis avec {} documents", dossierId, req.documentIds().size());
        return KycResponse.from(dossier);
    }

    // Fallback si document-service est indisponible
    public KycResponse submitDossierFallback(User currentUser, Long dossierId,
                                             KycSubmitRequest req, Exception ex) {
        log.error("Document-service indisponible, dossier {} mis en attente : {}", dossierId, ex.getMessage());
        throw new KycException("Le service de vérification documentaire est temporairement indisponible. " +
                "Veuillez réessayer dans quelques instants.");
    }

    public List<KycResponse> getDossiers(User currentUser) {
        var client = clientRepo.findByUser_Id(currentUser.getId())
                .orElseThrow(() -> new CustomerNotFoundException("Client introuvable"));
        return dossierRepo.findByClient_ClientId(client.getClientId())
                .stream().map(KycResponse::from).toList();
    }

    public KycResponse getDossierActuel(User currentUser) {
        var client = clientRepo.findByUser_Id(currentUser.getId())
                .orElseThrow(() -> new CustomerNotFoundException("Client introuvable"));
        return dossierRepo.findTopByClient_ClientIdOrderByDateOuvertureDesc(client.getClientId())
                .map(KycResponse::from)
                .orElseThrow(() -> new KycException("Aucun dossier KYC trouvé"));
    }
}