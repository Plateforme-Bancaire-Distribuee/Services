package com.banking.customer_service.entity;

import com.banking.customer_service.enums.StatutDossier;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dossiers_kyc")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DossierKYC {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dossierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDossier statut;

    private Integer niveauVerif;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateOuverture;

    private LocalDateTime dateCloture;

    @Column(length = 1000)
    private String commentaire;

    // IDs des documents dans le document-service
    @ElementCollection
    @CollectionTable(name = "dossier_document_refs",
            joinColumns = @JoinColumn(name = "dossier_id"))
    @Column(name = "document_id")
    @Builder.Default
    private List<Long> documentIds = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        dateOuverture = LocalDateTime.now();
        statut = StatutDossier.EN_ATTENTE;
        niveauVerif = 0;
    }

    public void soumettre() {
        this.statut = StatutDossier.EN_COURS;
    }

    public void approuver() {
        this.statut = StatutDossier.APPROUVE;
        this.niveauVerif = 1;
        this.dateCloture = LocalDateTime.now();
    }

    public void rejeter(String commentaire) {
        this.statut = StatutDossier.REJETE;
        this.commentaire = commentaire;
        this.dateCloture = LocalDateTime.now();
    }
}