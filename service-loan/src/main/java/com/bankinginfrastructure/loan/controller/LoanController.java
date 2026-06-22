package com.bankinginfrastructure.loan.controller;

import com.bankinginfrastructure.loan.dto.LoanDecisionRequest;
import com.bankinginfrastructure.loan.dto.LoanResponse;
import com.bankinginfrastructure.loan.dto.RepaymentRequest;
import com.bankinginfrastructure.loan.service.LoanService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/loans")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    @PostMapping(value = "/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LoanResponse requestLoan(
            @RequestParam Long clientId,
            @RequestParam BigDecimal amount,
            @RequestParam Integer durationMonths,
            @RequestParam String purpose,
            @RequestPart MultipartFile salarySlip) {
        return loanService.requestLoan(clientId, amount, durationMonths, purpose, salarySlip);
    }

    @PostMapping("/decision")
    public LoanResponse processDecision(@RequestBody LoanDecisionRequest request) {
        return loanService.processDecision(request);
    }

    @PostMapping("/repay")
    public LoanResponse registerRepayment(@RequestBody RepaymentRequest request) {
        return loanService.registerRepayment(request);
    }

    @GetMapping("/client/{clientId}")
    public List<LoanResponse> getLoansByClientId(@PathVariable Long clientId) {
        return loanService.getLoansByClientId(clientId);
    }
}
