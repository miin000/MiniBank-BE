package com.minibank.backend.saving.dto;
 
import java.math.BigDecimal;
import java.time.Instant;

import com.minibank.backend.saving.entity.Saving;
 
/**
 * Response DTO for a Saving (sổ tiết kiệm).
 * Flat structure; Flutter models map 1-to-1.
 */
public record SavingResponse(
        Long id,
        String code,
        String productName,
        BigDecimal principalAmount,
        BigDecimal actualInterestRate,
        String interestRateType,
        String termUnit,
        int termValue,
        boolean capitalized,
        BigDecimal accruedInterestAmount,
        BigDecimal postedInterestAmount,
        BigDecimal projectedMaturityAmount,
        String status,
        Instant openDate,
        Instant maturityDate,
        Instant closeDate,
        boolean autoRenew,
        // Source account info
        Long sourceAccountId,
        String sourceAccountNumber,
        String sourceAccountName
) {
    public static SavingResponse from(Saving s) {
        return new SavingResponse(
                s.getId(),
                s.getCode(),
                s.getSavingProduct().getName(),
                s.getPrincipalAmount(),
                s.getActualInterestRate(),
                s.getInterestRateType(),
                s.getTermUnit(),
                s.getTermValue(),
                s.isCapitalized(),
                s.getAccruedInterestAmount(),
                s.getPostedInterestAmount(),
                s.getProjectedMaturityAmount(),
                s.getStatus(),
                s.getOpenDate(),
                s.getMaturityDate(),
                s.getCloseDate(),
                s.isAutoRenew(),
                s.getSourceAccount().getId(),
                s.getSourceAccount().getAccountNumber(),
                s.getSourceAccount().getAccountName()
        );
    }
}