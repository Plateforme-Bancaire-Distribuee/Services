package com.bankinginfrastructure.loan.dto;

import com.bankinginfrastructure.loan.entity.LoanRepayment;
import com.bankinginfrastructure.loan.entity.LoanStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanResponse {

    private Long id;
    private Long clientId;
    private BigDecimal amount;
    private Double interestRate;
    private Integer durationMonths;
    private LoanStatus status;
    private BigDecimal monthlyPayment;
    private BigDecimal totalAmount;
    private String purpose;
    private String documentPath;
    private BigDecimal extractedSalary;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private List<LoanRepayment> repayments;
}
