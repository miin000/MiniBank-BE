package com.minibank.backend.loan.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateLoanRequest(
	@NotNull(message = "Loan product ID is required")
	Long loanProductId,

	@NotNull(message = "Disbursement account ID is required")
	Long disbursementAccountId,

	@NotNull(message = "Repayment account ID is required")
	Long repaymentAccountId,

	@NotNull(message = "Amount is required")
	@Positive(message = "Amount must be positive")
	BigDecimal amount,

	@NotBlank(message = "Loan purpose is required")
	String purpose
) {}
