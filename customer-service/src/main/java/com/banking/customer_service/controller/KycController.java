package com.banking.customer_service.controller;

import com.banking.customer_service.dto.request.KycSubmitRequest;
import com.banking.customer_service.dto.response.ApiResponse;
import com.banking.customer_service.dto.response.KycResponse;
import com.banking.customer_service.entity.User;
import com.banking.customer_service.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "Vérification documentaire Know Your Customer")
@SecurityRequirement(name = "bearerAuth")
public class KycController {

    private final KycService kycService;

    @PostMapping("/dossier")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ouvrir un nouveau dossier KYC")
    public ResponseEntity<ApiResponse<KycResponse>> ouvrirDossier(
            @AuthenticationPrincipal User currentUser) {
        var response = kycService.ouvrirDossier(currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Dossier KYC ouvert", response));
    }

    @PostMapping("/dossier/{dossierId}/soumettre")
    @Operation(summary = "Soumettre les documents d'un dossier KYC pour vérification OCR")
    public ResponseEntity<ApiResponse<KycResponse>> soumettreDossier(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long dossierId,
            @Valid @RequestBody KycSubmitRequest request) {
        var response = kycService.soumettreDossier(currentUser, dossierId, request);
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("Dossier soumis, vérification en cours", response));
    }

    @GetMapping("/dossiers")
    @Operation(summary = "Lister tous mes dossiers KYC")
    public ResponseEntity<ApiResponse<List<KycResponse>>> getDossiers(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                ApiResponse.ok(kycService.getDossiers(currentUser))
        );
    }

    @GetMapping("/dossier/actuel")
    @Operation(summary = "Récupérer mon dossier KYC le plus récent")
    public ResponseEntity<ApiResponse<KycResponse>> getDossierActuel(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                ApiResponse.ok(kycService.getDossierActuel(currentUser))
        );
    }
}