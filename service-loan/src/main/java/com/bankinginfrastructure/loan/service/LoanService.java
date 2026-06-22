package com.bankinginfrastructure.loan.service;

import com.bankinginfrastructure.loan.dto.LoanDecisionRequest;
import com.bankinginfrastructure.loan.dto.LoanResponse;
import com.bankinginfrastructure.loan.dto.RepaymentRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface LoanService {

    LoanResponse requestLoan(Long clientId, BigDecimal amount, Integer durationMonths, String purpose, MultipartFile salarySlip);

    LoanResponse processDecision(LoanDecisionRequest request);

    LoanResponse registerRepayment(RepaymentRequest request);

    List<LoanResponse> getLoansByClientId(Long clientId);
}
