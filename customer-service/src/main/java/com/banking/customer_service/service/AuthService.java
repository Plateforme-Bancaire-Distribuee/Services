package com.banking.customer_service.service;

import com.banking.customer_service.dto.request.LoginRequest;
import com.banking.customer_service.dto.request.RefreshTokenRequest;
import com.banking.customer_service.dto.request.RegisterRequest;
import com.banking.customer_service.dto.response.AuthResponse;
import com.banking.customer_service.dto.response.CustomerResponse;
import com.banking.customer_service.entity.*;
import com.banking.customer_service.enums.Role;
import com.banking.customer_service.exception.EmailAlreadyExistsException;
import com.banking.customer_service.kafka.CustomerEventPublisher;
import com.banking.customer_service.repository.ClientRepository;
import com.banking.customer_service.repository.RefreshTokenRepository;
import com.banking.customer_service.repository.UserRepository;
import com.banking.customer_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final CustomerEventPublisher eventPublisher;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        // 1. Vérification unicité email
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException(req.email());
        }

        // 2. Créer le User (auth)
        var user = User.builder()
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .role(Role.CLIENT)
                .enabled(true)
                .build();
        user = userRepository.save(user);

        // 3. Créer le Client (profil)
        var client = Client.builder()
                .user(user)
                .nom(req.nom())
                .prenom(req.prenom())
                .dateNaissance(req.dateNaissance())
                .genre(req.genre())
                .build();
        client = clientRepository.save(client);

        // 4. Adresse
        var adresse = Adresse.builder()
                .client(client)
                .rue(req.rue())
                .ville(req.ville())
                .codePostal(req.codePostal())
                .pays(req.pays())
                .region(req.region())
                .build();
        client.setAdresse(adresse);

        // 5. Contact
        var contact = Contact.builder()
                .client(client)
                .email(req.email())
                .telephone(req.telephone())
                .telephoneAlternatif(req.telephoneAlternatif())
                .emailVerifie(false)
                .telVerifie(false)
                .build();
        client.setContact(contact);
        client = clientRepository.save(client);

        // 6. Générer les tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = createRefreshToken(user);

        // 7. Publier l'event Kafka
        eventPublisher.publishCustomerRegistered(client);

        log.info("Nouveau client enregistré : {} ({})", client.getNumeroClient(), req.email());

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtService.getExpiration(),
                CustomerResponse.from(client)
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        // Authentification Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        var user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        var client = clientRepository.findByUserIdWithDetails(user.getId())
                .orElseThrow(() -> new RuntimeException("Profil client introuvable"));

        // Révoquer les anciens refresh tokens
        refreshTokenRepository.revokeAllByUserId(user.getId());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = createRefreshToken(user);

        log.info("Connexion réussie : {}", req.email());

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtService.getExpiration(),
                CustomerResponse.from(client)
        );
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest req) {
        var storedToken = refreshTokenRepository.findByToken(req.refreshToken())
                .orElseThrow(() -> new RuntimeException("Refresh token invalide"));

        if (storedToken.isRevoked() || storedToken.isExpired()) {
            throw new RuntimeException("Refresh token expiré ou révoqué");
        }

        var user = storedToken.getUser();
        var client = clientRepository.findByUserIdWithDetails(user.getId())
                .orElseThrow(() -> new RuntimeException("Profil client introuvable"));

        // Rotation du refresh token (sécurité)
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = createRefreshToken(user);

        return AuthResponse.of(
                newAccessToken,
                newRefreshToken,
                jwtService.getExpiration(),
                CustomerResponse.from(client)
        );
    }

    private String createRefreshToken(User user) {
        var token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(token);
        return token.getToken();
    }
}