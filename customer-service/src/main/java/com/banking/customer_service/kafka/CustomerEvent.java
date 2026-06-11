package com.banking.customer_service.kafka;

import java.time.LocalDateTime;

public sealed interface CustomerEvent permits
        CustomerEvent.CustomerRegistered,
        CustomerEvent.KycSubmitted,
        CustomerEvent.CustomerStatusChanged {

    record CustomerRegistered(
            Long clientId,
            String numeroClient,
            String email,
            String nom,
            String prenom,
            LocalDateTime timestamp
    ) implements CustomerEvent {}

    record KycSubmitted(
            Long dossierId,
            Long clientId,
            String clientNom,
            String clientPrenom,
            java.time.LocalDate clientDateNaissance,
            java.util.List<Long> documentIds,
            LocalDateTime timestamp
    ) implements CustomerEvent {}

    record CustomerStatusChanged(
            Long clientId,
            String ancienStatut,
            String nouveauStatut,
            String raison,
            LocalDateTime timestamp
    ) implements CustomerEvent {}
}