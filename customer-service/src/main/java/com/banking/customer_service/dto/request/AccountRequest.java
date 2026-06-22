package com.banking.customer_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountRequest {
    private Long id;
    private String accountNumber;
    private Long clientId;
    private String accountType;
    private BigDecimal balance;
    private String currency;
    private String status;
}
