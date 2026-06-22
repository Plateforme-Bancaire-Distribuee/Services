package com.bankinginfrastructure.loan.exception;

import java.time.LocalDateTime;

public record ErrorResponse(LocalDateTime timestamp, String message, int status) {
}
