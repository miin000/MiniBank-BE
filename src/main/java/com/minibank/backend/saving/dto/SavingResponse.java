package com.minibank.backend.saving.dto;

import java.math.BigDecimal;

import com.minibank.backend.saving.entity.Saving;

public record SavingResponse(
    Long id,
    String code,
    String status,
    BigDecimal principalAmount,
    String productName,
    String sourceAccountNumber,
    String sourceAccountName,
    boolean autoRenew,
    String openDate,
    String maturityDate
) {
    public static SavingResponse from(Saving s) {
        return new SavingResponse(
            s.getId(),
            s.getCode(),
            s.getStatus(),
            s.getPrincipalAmount(),
            s.getSavingProduct() != null ? s.getSavingProduct().getName() : null,
            s.getSourceAccount() != null ? s.getSourceAccount().getAccountNumber() : null,
            s.getSourceAccount() != null ? s.getSourceAccount().getAccountName() : null,
            s.isAutoRenew(),
            s.getOpenDate() != null ? s.getOpenDate().toString() : null,
            s.getMaturityDate() != null ? s.getMaturityDate().toString() : null
        );
    }
}