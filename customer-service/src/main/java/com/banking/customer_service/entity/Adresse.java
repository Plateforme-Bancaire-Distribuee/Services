package com.banking.customer_service.entity;

import com.banking.customer_service.entity.Client;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "adresses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Adresse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String rue;

    @Column(nullable = false)
    private String ville;

    private String codePostal;

    @Column(nullable = false)
    private String pays;

    private String region;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    public boolean valider() {
        return rue != null && !rue.isBlank()
                && ville != null && !ville.isBlank()
                && pays != null && !pays.isBlank();
    }

    public String formater() {
        return rue + ", " + ville + (codePostal != null ? " " + codePostal : "") + ", " + pays;
    }
}