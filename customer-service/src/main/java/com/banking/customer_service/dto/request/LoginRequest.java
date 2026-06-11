package com.banking.customer_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "L'email est obligatoire")
        @Email
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        String password
) {}