package com.banking.customer_service.dto.response;

public record ContactResponse(
        String email,
        String telephone,
        String telephoneAlternatif,
        boolean emailVerifie,
        boolean telVerifie
) {}