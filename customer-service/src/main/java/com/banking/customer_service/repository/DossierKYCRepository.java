package com.banking.customer_service.repository;

import com.banking.customer_service.entity.DossierKYC;
import com.banking.customer_service.enums.StatutDossier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DossierKYCRepository extends JpaRepository<DossierKYC, Long> {

    List<DossierKYC> findByClient_ClientId(Long clientId);

    Optional<DossierKYC> findTopByClient_ClientIdOrderByDateOuvertureDesc(Long clientId);

    boolean existsByClient_ClientIdAndStatut(Long clientId, StatutDossier statut);
}