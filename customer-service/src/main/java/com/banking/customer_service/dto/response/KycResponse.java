package com.banking.customer_service.dto.response;


import com.banking.customer_service.entity.DossierKYC;
import com.banking.customer_service.enums.StatutDossier;

import java.time.LocalDateTime;
import java.util.List;

public record KycResponse(
        Long dossierId,
        StatutDossier statut,
        Integer niveauVerif,
        LocalDateTime dateOuverture,
        LocalDateTime dateCloture,
        String commentaire,
        List<Long> documentIds
) {
    public static KycResponse from(DossierKYC dossier) {
        return new KycResponse(
                dossier.getDossierId(),
                dossier.getStatut(),
                dossier.getNiveauVerif(),
                dossier.getDateOuverture(),
                dossier.getDateCloture(),
                dossier.getCommentaire(),
                dossier.getDocumentIds()
        );
    }
}