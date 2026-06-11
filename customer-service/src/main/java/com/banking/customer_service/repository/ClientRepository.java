package com.banking.customer_service.repository;

import com.banking.customer_service.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByUser_Id(Long userId);

    Optional<Client> findByUser_Email(String email);

    @Query("SELECT c FROM Client c LEFT JOIN FETCH c.adresse LEFT JOIN FETCH c.contact WHERE c.user.id = :userId")
    Optional<Client> findByUserIdWithDetails(Long userId);

    boolean existsByUser_Email(String email);
}