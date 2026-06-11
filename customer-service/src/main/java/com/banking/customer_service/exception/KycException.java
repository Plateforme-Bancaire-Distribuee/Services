package com.banking.customer_service.exception;

public class KycException extends RuntimeException {
    public KycException(String message) {
        super(message);
    }
}