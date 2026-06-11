package com.banking.customer_service.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record KycSubmitRequest(

        @NotEmpty(message = "Au moins un document est requis")
        List<Long> documentIds
) {}