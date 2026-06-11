package com.banking.customer_service.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RegisterRequest(

        @NotBlank(message = "Le nom est obligatoire")
        String nom,

        @NotBlank(message = "Le prénom est obligatoire")
        String prenom,

        @NotNull(message = "La date de naissance est obligatoire")
        @Past(message = "La date de naissance doit être dans le passé")
        LocalDate dateNaissance,

        @NotNull(message = "Le genre est obligatoire")
        Character genre,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Format email invalide")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        String password,

        // Adresse
        @NotBlank(message = "La rue est obligatoire")
        String rue,

        @NotBlank(message = "La ville est obligatoire")
        String ville,

        String codePostal,

        @NotBlank(message = "Le pays est obligatoire")
        String pays,

        String region,

        // Contact
        @NotBlank(message = "Le téléphone est obligatoire")
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Format téléphone invalide")
        String telephone,

        String telephoneAlternatif
) {}