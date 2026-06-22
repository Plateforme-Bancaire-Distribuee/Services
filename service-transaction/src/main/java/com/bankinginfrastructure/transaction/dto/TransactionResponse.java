package com.bankinginfrastructure.transaction.dto;

import com.bankinginfrastructure.transaction.entity.TransactionStatus;
import com.bankinginfrastructure.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionResponse {

    private Long id;
    private String reference;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal fees;
    private BigDecimal netAmount;
    private TransactionStatus status;
    private LocalDateTime createdAt;
}
