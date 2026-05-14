package com.minibank.backend.loan.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanResponse(
	Long id,
	String code,
	BigDecimal approvedAmount,
	BigDecimal disbursedAmount,
	BigDecimal outstandingPrincipal,
	BigDecimal outstandingInterest,
	String status,
	String repaymentFrequency,
	int termMonths,
	Instant nextDueDate,
	Instant maturityDate,
	Instant createdAt
) {}
