package com.bankinginfrastructure.transaction.service;

import com.bankinginfrastructure.transaction.dto.DepositRequest;
import com.bankinginfrastructure.transaction.dto.TransactionResponse;
import com.bankinginfrastructure.transaction.dto.TransferRequest;
import com.bankinginfrastructure.transaction.dto.WithdrawalRequest;
import java.util.List;

public interface TransactionService {

    TransactionResponse deposit(DepositRequest request);

    TransactionResponse withdraw(WithdrawalRequest request);

    TransactionResponse transfer(TransferRequest request);

    TransactionResponse getTransactionByReference(String reference);

    List<TransactionResponse> getTransactionsByAccount(Long accountId);

    List<TransactionResponse> getAllTransactions();
}
