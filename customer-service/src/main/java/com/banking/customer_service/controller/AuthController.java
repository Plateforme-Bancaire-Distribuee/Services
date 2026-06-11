package com.banking.customer_service.controller;

import com.banking.customer_service.dto.request.LoginRequest;
import com.banking.customer_service.dto.request.RefreshTokenRequest;
import com.banking.customer_service.dto.request.RegisterRequest;
import com.banking.customer_service.dto.response.ApiResponse;
import com.banking.customer_service.dto.response.AuthResponse;
import com.banking.customer_service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Inscription, connexion et gestion des tokens")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inscription d'un nouveau client")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        var response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Compte créé avec succès", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Connexion d'un client")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        var response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Connexion réussie", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchissement du token d'accès")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        var response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.ok("Token rafraîchi", response));
    }
}