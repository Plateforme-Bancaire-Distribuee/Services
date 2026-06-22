package com.banking.customer_service.service;

import com.banking.customer_service.dto.request.AccountRequest;
import com.banking.customer_service.dto.request.UpdateProfileRequest;
import com.banking.customer_service.dto.response.CustomerResponse;
import com.banking.customer_service.entity.*;
import com.banking.customer_service.enums.StatutClient;
import com.banking.customer_service.exception.CustomerNotFoundException;
import com.banking.customer_service.feignClients.AccountClient;
import com.banking.customer_service.kafka.CustomerEventPublisher;
import com.banking.customer_service.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final ClientRepository clientRepository;
    private final CustomerEventPublisher eventPublisher;
    private final AccountClient accountClient;

    public CustomerResponse getProfile(User currentUser) {
        var client = clientRepository.findByUserIdWithDetails(currentUser.getId())
                .orElseThrow(() -> new CustomerNotFoundException("Profil client introuvable"));
        return CustomerResponse.from(client);
    }

    @Transactional
    public CustomerResponse updateProfile(User currentUser, UpdateProfileRequest req) {
        var client = clientRepository.findByUserIdWithDetails(currentUser.getId())
                .orElseThrow(() -> new CustomerNotFoundException("Profil client introuvable"));

        // Mise à jour adresse
        if (client.getAdresse() != null) {
            var adresse = client.getAdresse();
            if (req.rue() != null) adresse.setRue(req.rue());
            if (req.ville() != null) adresse.setVille(req.ville());
            if (req.codePostal() != null) adresse.setCodePostal(req.codePostal());
            if (req.pays() != null) adresse.setPays(req.pays());
            if (req.region() != null) adresse.setRegion(req.region());
        }

        // Mise à jour contact
        if (client.getContact() != null) {
            var contact = client.getContact();
            if (req.telephone() != null) contact.setTelephone(req.telephone());
            if (req.telephoneAlternatif() != null) contact.setTelephoneAlternatif(req.telephoneAlternatif());
        }

        client = clientRepository.save(client);
        log.info("Profil mis à jour pour client {}", client.getNumeroClient());
        return CustomerResponse.from(client);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public void suspendClient(Long clientId, String raison) {
        var client = clientRepository.findById(clientId)
                .orElseThrow(() -> new CustomerNotFoundException("Client " + clientId + " introuvable"));

        String ancienStatut = client.getStatut().name();
        client.setStatut(StatutClient.SUSPENDU);
        clientRepository.save(client);

        eventPublisher.publishCustomerStatusChanged(client, ancienStatut, raison);
        log.info("Client {} suspendu. Raison : {}", client.getNumeroClient(), raison);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public void cloturerClient(Long clientId, String raison) {
        var client = clientRepository.findById(clientId)
                .orElseThrow(() -> new CustomerNotFoundException("Client " + clientId + " introuvable"));

        String ancienStatut = client.getStatut().name();
        client.setStatut(StatutClient.CLOTURE);
        clientRepository.save(client);

        eventPublisher.publishCustomerStatusChanged(client, ancienStatut, raison);
        log.info("Client {} clôturé.", client.getNumeroClient());
    }

    public Client getCustomerById(Long clientId) {
        var client = clientRepository.findById(clientId).orElseThrow(() -> new CustomerNotFoundException("Client " + clientId + " introuvable"));
        return client;
    }

    public List<AccountRequest> getCustomerAccounts(Long clientId) {
        return accountClient.getAccountsByClientId(clientId);
    }
}