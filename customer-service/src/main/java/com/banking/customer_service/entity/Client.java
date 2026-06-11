package com.banking.customer_service.entity;

import com.banking.customer_service.enums.StatutClient;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long clientId;

    @Column(unique = true, nullable = false)
    private String numeroClient;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    private LocalDate dateNaissance;

    private Character genre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutClient statut;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    // Relation avec User (auth)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Adresse embarquée (1-1 simple)
    @OneToOne(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Adresse adresse;

    // Contact embarqué (1-1 simple)
    @OneToOne(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Contact contact;

    // Dossiers KYC
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DossierKYC> dossiers = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        dateCreation = LocalDateTime.now();
        statut = StatutClient.EN_ATTENTE_KYC;
        numeroClient = "CLI-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}