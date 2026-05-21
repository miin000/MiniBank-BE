package com.minibank.backend.loan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.minibank.backend.loan.entity.LoanProductInterestTier;

@Repository
public interface LoanProductInterestTierRepository extends JpaRepository<LoanProductInterestTier, Long> {
    List<LoanProductInterestTier> findByLoanProductIdOrderByEffectiveFromDescMinAmountAsc(Long loanProductId);
}
