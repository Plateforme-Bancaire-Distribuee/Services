package com.bankinginfrastructure.loan.repository;

import com.bankinginfrastructure.loan.entity.Loan;
import com.bankinginfrastructure.loan.entity.LoanStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByClientId(Long clientId);

    boolean existsByClientIdAndStatus(Long clientId, LoanStatus status);
}
