package com.bankinginfrastructure.loan.exception;

public class ActiveLoanException extends RuntimeException {

    public ActiveLoanException(String message) {
        super(message);
    }
}
