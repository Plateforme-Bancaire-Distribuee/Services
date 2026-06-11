package com.banking.customer_service.dto.response;

public record AdresseResponse(
        String rue,
        String ville,
        String codePostal,
        String pays,
        String region
) {}