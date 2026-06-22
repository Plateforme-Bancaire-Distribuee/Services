package com.bankinginfrastructure.transaction.service;

import com.bankinginfrastructure.transaction.client.AccountClient;
import com.bankinginfrastructure.transaction.dto.DepositRequest;
import com.bankinginfrastructure.transaction.dto.TransactionResponse;
import com.bankinginfrastructure.transaction.dto.TransferRequest;
import com.bankinginfrastructure.transaction.dto.WithdrawalRequest;
import com.bankinginfrastructure.transaction.entity.Transaction;
import com.bankinginfrastructure.transaction.entity.TransactionStatus;
import com.bankinginfrastructure.transaction.entity.TransactionType;
import com.bankinginfrastructure.transaction.exception.ResourceNotFoundException;
import com.bankinginfrastructure.transaction.mapper.TransactionMapper;
import com.bankinginfrastructure.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final BigDecimal TRANSFER_FEE_RATE = new BigDecimal("0.01");

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final AccountClient accountClient;

    @Override
    @Transactional
    public TransactionResponse deposit(DepositRequest request) {
        Transaction transaction = Transaction.builder()
                .sourceAccountId(request.getAccountId())
                .amount(request.getAmount())
                .fees(BigDecimal.ZERO)
                .netAmount(request.getAmount())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .description(request.getDescription())
                .build();

        try {
            accountClient.credit(request.getAccountId(), amountBody(request.getAmount()));
            transaction.setStatus(TransactionStatus.SUCCESS);
        } catch (RuntimeException ex) {
            transaction.setStatus(TransactionStatus.FAILED);
        }

        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request) {
        Transaction transaction = Transaction.builder()
                .sourceAccountId(request.getAccountId())
                .amount(request.getAmount())
                .fees(BigDecimal.ZERO)
                .netAmount(request.getAmount())
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.PENDING)
                .description(request.getDescription())
                .build();

        try {
            accountClient.debit(request.getAccountId(), amountBody(request.getAmount()));
            transaction.setStatus(TransactionStatus.SUCCESS);
        } catch (RuntimeException ex) {
            transaction.setStatus(TransactionStatus.FAILED);
        }

        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        BigDecimal fees = request.getAmount().multiply(TRANSFER_FEE_RATE);
        BigDecimal netAmount = request.getAmount().subtract(fees);
        Transaction transaction = Transaction.builder()
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationAccountId())
                .amount(request.getAmount())
                .fees(fees)
                .netAmount(netAmount)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .description(request.getDescription())
                .build();

        try {
            accountClient.debit(request.getSourceAccountId(), amountBody(request.getAmount()));
            try {
                accountClient.credit(request.getDestinationAccountId(), amountBody(netAmount));
                transaction.setStatus(TransactionStatus.SUCCESS);
            } catch (RuntimeException creditException) {
                compensateSourceAccount(request.getSourceAccountId(), request.getAmount());
                transaction.setStatus(TransactionStatus.FAILED);
            }
        } catch (RuntimeException debitException) {
            transaction.setStatus(TransactionStatus.FAILED);
        }

        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByReference(String reference) {
        return transactionRepository.findByReference(reference)
                .map(transactionMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction introuvable avec la reference: " + reference));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccount(Long accountId) {
        List<Transaction> sourceTransactions = transactionRepository.findBySourceAccountId(accountId);
        List<Transaction> destinationTransactions = transactionRepository.findByDestinationAccountId(accountId);

        return Stream.concat(sourceTransactions.stream(), destinationTransactions.stream())
                .distinct()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAll().stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    private void compensateSourceAccount(Long sourceAccountId, BigDecimal amount) {
        try {
            accountClient.credit(sourceAccountId, amountBody(amount));
        } catch (RuntimeException ignored) {
            // The failed transaction is persisted; operational retry can be handled outside this service.
        }
    }

    private Map<String, BigDecimal> amountBody(BigDecimal amount) {
        Map<String, BigDecimal> body = new LinkedHashMap<>();
        body.put("amount", amount);
        return body;
    }
}
