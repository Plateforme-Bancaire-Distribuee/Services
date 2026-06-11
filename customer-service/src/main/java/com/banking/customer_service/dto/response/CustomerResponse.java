package com.banking.customer_service.dto.response;


import com.banking.customer_service.entity.Client;
import com.banking.customer_service.enums.StatutClient;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerResponse(
        Long clientId,
        String numeroClient,
        String nom,
        String prenom,
        LocalDate dateNaissance,
        Character genre,
        StatutClient statut,
        LocalDateTime dateCreation,
        String email,
        AdresseResponse adresse,
        ContactResponse contact
) {
    public static CustomerResponse from(Client client) {
        AdresseResponse adresseResponse = null;
        if (client.getAdresse() != null) {
            var a = client.getAdresse();
            adresseResponse = new AdresseResponse(
                    a.getRue(), a.getVille(), a.getCodePostal(), a.getPays(), a.getRegion()
            );
        }

        ContactResponse contactResponse = null;
        if (client.getContact() != null) {
            var c = client.getContact();
            contactResponse = new ContactResponse(
                    c.getEmail(), c.getTelephone(), c.getTelephoneAlternatif(),
                    c.isEmailVerifie(), c.isTelVerifie()
            );
        }

        return new CustomerResponse(
                client.getClientId(),
                client.getNumeroClient(),
                client.getNom(),
                client.getPrenom(),
                client.getDateNaissance(),
                client.getGenre(),
                client.getStatut(),
                client.getDateCreation(),
                client.getContact() != null ? client.getContact().getEmail() : null,
                adresseResponse,
                contactResponse
        );
    }
}