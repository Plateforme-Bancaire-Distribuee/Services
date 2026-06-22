package com.bankinginfrastructure.transaction.mapper;

import com.bankinginfrastructure.transaction.dto.TransactionResponse;
import com.bankinginfrastructure.transaction.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .reference(transaction.getReference())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .fees(transaction.getFees())
                .netAmount(transaction.getNetAmount())
                .status(transaction.getStatus())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
