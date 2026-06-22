package com.bankinginfrastructure.loan.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanDecisionRequest {

    private Long loanId;
    private LoanDecision decision;
    private String reason;
}
