package com.banking.customer_service.controller;

import com.banking.customer_service.dto.request.UpdateProfileRequest;
import com.banking.customer_service.dto.response.ApiResponse;
import com.banking.customer_service.dto.response.CustomerResponse;
import com.banking.customer_service.entity.User;
import com.banking.customer_service.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Profil Client", description = "Gestion du profil client")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/me")
    @Operation(summary = "Récupérer mon profil")
    public ResponseEntity<ApiResponse<CustomerResponse>> getProfile(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                ApiResponse.ok(customerService.getProfile(currentUser))
        );
    }

    @PutMapping("/me")
    @Operation(summary = "Mettre à jour mon profil (adresse et contact)")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok("Profil mis à jour", customerService.updateProfile(currentUser, request))
        );
    }

    // ---- Endpoints Agent/Admin ----

    @GetMapping("/{clientId}")
    @Operation(summary = "Récupérer un client par ID (Agent/Admin)")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(
            @PathVariable Long clientId,
            @AuthenticationPrincipal User currentUser) {
        // Délégué au service (qui vérifie le rôle via @PreAuthorize si besoin)
        // Pour simplifier, on retourne le profil de l'utilisateur connecté
        // À adapter pour un vrai endpoint admin
        return ResponseEntity.ok(
                ApiResponse.ok(customerService.getProfile(currentUser))
        );
    }

    @PatchMapping("/{clientId}/suspend")
    @Operation(summary = "Suspendre un compte client (Agent/Admin)")
    public ResponseEntity<ApiResponse<Void>> suspend(
            @PathVariable Long clientId,
            @RequestParam String raison) {
        customerService.suspendClient(clientId, raison);
        return ResponseEntity.ok(ApiResponse.ok("Client suspendu", null));
    }

    @PatchMapping("/{clientId}/cloturer")
    @Operation(summary = "Clôturer un compte client (Admin)")
    public ResponseEntity<ApiResponse<Void>> cloturer(
            @PathVariable Long clientId,
            @RequestParam String raison) {
        customerService.cloturerClient(clientId, raison);
        return ResponseEntity.ok(ApiResponse.ok("Client clôturé", null));
    }
}