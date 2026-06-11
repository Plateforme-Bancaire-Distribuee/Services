package com.banking.customer_service.dto.request;

import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
        String rue,
        String ville,
        String codePostal,
        String pays,
        String region,

        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Format téléphone invalide")
        String telephone,

        String telephoneAlternatif
) {}