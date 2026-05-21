package com.minibank.backend.loan.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanApplicationResponse(
    Long id,
    String productName,
    BigDecimal requestedAmount,
    int termMonths,
    String purpose,
    String status,        // pending | approved | rejected
    String priorityTag,  // null hoặc "priority"
    Instant submittedAt
) {}