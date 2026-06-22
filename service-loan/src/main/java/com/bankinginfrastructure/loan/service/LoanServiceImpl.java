package com.bankinginfrastructure.loan.service;

import com.bankinginfrastructure.loan.client.AiOcrClient;
import com.bankinginfrastructure.loan.dto.LoanDecision;
import com.bankinginfrastructure.loan.dto.LoanDecisionRequest;
import com.bankinginfrastructure.loan.dto.LoanResponse;
import com.bankinginfrastructure.loan.dto.OcrSalaryResponse;
import com.bankinginfrastructure.loan.dto.RepaymentRequest;
import com.bankinginfrastructure.loan.entity.Loan;
import com.bankinginfrastructure.loan.entity.LoanRepayment;
import com.bankinginfrastructure.loan.entity.LoanStatus;
import com.bankinginfrastructure.loan.entity.RepaymentStatus;
import com.bankinginfrastructure.loan.exception.ActiveLoanException;
import com.bankinginfrastructure.loan.exception.LoanException;
import com.bankinginfrastructure.loan.exception.LoanNotFoundException;
import com.bankinginfrastructure.loan.exception.OcrException;
import com.bankinginfrastructure.loan.repository.LoanRepaymentRepository;
import com.bankinginfrastructure.loan.repository.LoanRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private static final double DEFAULT_ANNUAL_INTEREST_RATE = 8.0;
    private static final BigDecimal ELIGIBILITY_MONTH_MULTIPLIER = BigDecimal.valueOf(36);
    private static final Path UPLOAD_DIRECTORY = Paths.get("uploads", "loans");

    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final AiOcrClient aiOcrClient;

    @Override
    @Transactional
    public LoanResponse requestLoan(Long clientId, BigDecimal amount, Integer durationMonths, String purpose, MultipartFile salarySlip) {
        validateLoanRequest(clientId, amount, durationMonths, salarySlip);
        if (loanRepository.existsByClientIdAndStatus(clientId, LoanStatus.ACTIVE)) {
            throw new ActiveLoanException("Le client possede deja un pret actif.");
        }

        String documentPath = storeSalarySlip(salarySlip);
        BigDecimal extractedSalary = extractSalary(salarySlip);
        LoanAmounts loanAmounts = calculateLoanAmounts(amount, durationMonths);
        LoanStatus status = isEligible(amount, extractedSalary) ? LoanStatus.PENDING : LoanStatus.REJECTED;

        Loan loan = Loan.builder()
                .clientId(clientId)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .interestRate(DEFAULT_ANNUAL_INTEREST_RATE)
                .durationMonths(durationMonths)
                .status(status)
                .monthlyPayment(loanAmounts.monthlyPayment())
                .totalAmount(loanAmounts.totalAmount())
                .purpose(purpose)
                .documentPath(documentPath)
                .extractedSalary(extractedSalary.setScale(2, RoundingMode.HALF_UP))
                .build();

        return toResponse(loanRepository.save(loan));
    }

    @Override
    @Transactional
    public LoanResponse processDecision(LoanDecisionRequest request) {
        if (request == null || request.getLoanId() == null || request.getDecision() == null) {
            throw new LoanException("La decision de pret est invalide.");
        }

        Loan loan = getLoanOrThrow(request.getLoanId());
        if (request.getDecision() == LoanDecision.APPROVED) {
            loan.setStatus(LoanStatus.ACTIVE);
            loan.setApprovedAt(LocalDateTime.now());
            generateRepaymentScheduleIfNeeded(loan);
        } else if (request.getDecision() == LoanDecision.REJECTED) {
            loan.setStatus(LoanStatus.REJECTED);
        }

        return toResponse(loanRepository.save(loan));
    }

    @Override
    @Transactional
    public LoanResponse registerRepayment(RepaymentRequest request) {
        if (request == null || request.getLoanId() == null || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new LoanException("Le remboursement est invalide.");
        }

        Loan loan = getLoanOrThrow(request.getLoanId());
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new LoanException("Seul un pret actif peut recevoir un remboursement.");
        }

        BigDecimal remainingPayment = request.getAmount();
        List<LoanRepayment> repayments = sortedRepayments(loan.getId()).stream()
                .filter(repayment -> repayment.getStatus() != RepaymentStatus.PAID)
                .toList();

        if (repayments.isEmpty()) {
            throw new LoanException("Aucune echeance ouverte pour ce pret.");
        }

        for (LoanRepayment repayment : repayments) {
            if (remainingPayment.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal outstanding = repayment.getAmount().subtract(repayment.getPaidAmount());
            BigDecimal appliedAmount = remainingPayment.min(outstanding);
            repayment.setPaidAmount(repayment.getPaidAmount().add(appliedAmount).setScale(2, RoundingMode.HALF_UP));
            remainingPayment = remainingPayment.subtract(appliedAmount);

            if (repayment.getPaidAmount().compareTo(repayment.getAmount()) >= 0) {
                repayment.setPaidAmount(repayment.getAmount());
                repayment.setStatus(RepaymentStatus.PAID);
                repayment.setPaidAt(LocalDateTime.now());
            }
        }

        loanRepaymentRepository.saveAll(repayments);
        closeLoanIfFullyPaid(loan);
        return toResponse(loanRepository.save(loan));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanResponse> getLoansByClientId(Long clientId) {
        return loanRepository.findByClientId(clientId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateLoanRequest(Long clientId, BigDecimal amount, Integer durationMonths, MultipartFile salarySlip) {
        if (clientId == null) {
            throw new LoanException("L'identifiant client est obligatoire.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new LoanException("Le montant du pret doit etre positif.");
        }
        if (durationMonths == null || durationMonths <= 0) {
            throw new LoanException("La duree du pret doit etre positive.");
        }
        if (salarySlip == null || salarySlip.isEmpty()) {
            throw new OcrException("Le bulletin de paie est obligatoire.");
        }
    }

    private String storeSalarySlip(MultipartFile salarySlip) {
        try {
            Files.createDirectories(UPLOAD_DIRECTORY);
            String originalFilename = StringUtils.cleanPath(salarySlip.getOriginalFilename() == null ? "salary-slip" : salarySlip.getOriginalFilename());
            String storedFilename = UUID.randomUUID() + "-" + originalFilename;
            Path targetPath = UPLOAD_DIRECTORY.resolve(storedFilename).normalize();
            Files.copy(salarySlip.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath.toString();
        } catch (IOException ex) {
            throw new LoanException("Impossible de sauvegarder le bulletin de paie.", ex);
        }
    }

    private BigDecimal extractSalary(MultipartFile salarySlip) {
        try {
            OcrSalaryResponse response = aiOcrClient.extractSalary(salarySlip);
            if (response == null || response.getSalary() == null || response.getSalary().compareTo(BigDecimal.ZERO) <= 0) {
                throw new OcrException("Le service OCR n'a pas retourne de salaire valide.");
            }
            return response.getSalary();
        } catch (OcrException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OcrException("Erreur lors de l'extraction OCR du salaire.", ex);
        }
    }

    private boolean isEligible(BigDecimal amount, BigDecimal extractedSalary) {
        return amount.compareTo(extractedSalary.multiply(ELIGIBILITY_MONTH_MULTIPLIER)) <= 0;
    }

    private LoanAmounts calculateLoanAmounts(BigDecimal amount, Integer durationMonths) {
        BigDecimal annualRate = BigDecimal.valueOf(DEFAULT_ANNUAL_INTEREST_RATE)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        BigDecimal years = BigDecimal.valueOf(durationMonths)
                .divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);
        BigDecimal interestAmount = amount.multiply(annualRate).multiply(years);
        BigDecimal totalAmount = amount.add(interestAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyPayment = totalAmount.divide(BigDecimal.valueOf(durationMonths), 2, RoundingMode.HALF_UP);
        return new LoanAmounts(totalAmount, monthlyPayment);
    }

    private void generateRepaymentScheduleIfNeeded(Loan loan) {
        if (!loanRepaymentRepository.findByLoanId(loan.getId()).isEmpty()) {
            return;
        }

        LocalDate firstDueDate = LocalDate.now().plusMonths(1);
        List<LoanRepayment> repayments = java.util.stream.IntStream.rangeClosed(1, loan.getDurationMonths())
                .mapToObj(month -> LoanRepayment.builder()
                        .loanId(loan.getId())
                        .dueDate(firstDueDate.plusMonths(month - 1L))
                        .amount(loan.getMonthlyPayment())
                        .paidAmount(BigDecimal.ZERO)
                        .status(RepaymentStatus.PENDING)
                        .build())
                .toList();
        loanRepaymentRepository.saveAll(repayments);
    }

    private void closeLoanIfFullyPaid(Loan loan) {
        boolean fullyPaid = loanRepaymentRepository.findByLoanId(loan.getId()).stream()
                .allMatch(repayment -> repayment.getStatus() == RepaymentStatus.PAID);
        if (fullyPaid) {
            loan.setStatus(LoanStatus.CLOSED);
        }
    }

    private Loan getLoanOrThrow(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException("Pret introuvable avec l'identifiant: " + loanId));
    }

    private LoanResponse toResponse(Loan loan) {
        return LoanResponse.builder()
                .id(loan.getId())
                .clientId(loan.getClientId())
                .amount(loan.getAmount())
                .interestRate(loan.getInterestRate())
                .durationMonths(loan.getDurationMonths())
                .status(loan.getStatus())
                .monthlyPayment(loan.getMonthlyPayment())
                .totalAmount(loan.getTotalAmount())
                .purpose(loan.getPurpose())
                .documentPath(loan.getDocumentPath())
                .extractedSalary(loan.getExtractedSalary())
                .requestedAt(loan.getRequestedAt())
                .approvedAt(loan.getApprovedAt())
                .repayments(sortedRepayments(loan.getId()))
                .build();
    }

    private List<LoanRepayment> sortedRepayments(Long loanId) {
        if (loanId == null) {
            return List.of();
        }
        return loanRepaymentRepository.findByLoanId(loanId).stream()
                .sorted(Comparator.comparing(LoanRepayment::getDueDate))
                .toList();
    }

    private record LoanAmounts(BigDecimal totalAmount, BigDecimal monthlyPayment) {
    }
}
