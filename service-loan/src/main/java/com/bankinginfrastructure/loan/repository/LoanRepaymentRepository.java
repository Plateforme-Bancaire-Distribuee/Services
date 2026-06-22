package com.bankinginfrastructure.loan.repository;

import com.bankinginfrastructure.loan.entity.LoanRepayment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {

    List<LoanRepayment> findByLoanId(Long loanId);
}
