package com.minibank.backend.loan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.minibank.backend.loan.entity.Loan;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
	List<Loan> findByUserId(long userId);
	Optional<Loan> findByCode(String code);
	Optional<Loan> findByIdAndUserId(long id, long userId);

	// find loan by loan application id (used by ContractDataResolver)
	Optional<Loan> findByLoanApplicationId(Long loanApplicationId);
}
