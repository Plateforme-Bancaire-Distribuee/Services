package com.banking.customer_service.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("Email déjà utilisé : " + email);
    }
}