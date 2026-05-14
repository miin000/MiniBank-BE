package com.minibank.backend.loan.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.backend.common.security.CurrentJwt;
import com.minibank.backend.loan.dto.CreateLoanRequest;
import com.minibank.backend.loan.dto.LoanResponse;
import com.minibank.backend.loan.service.LoanService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mobile/loans")
public class MobileLoanController {
	private final LoanService loanService;

	public MobileLoanController(LoanService loanService) {
		this.loanService = loanService;
	}

	@GetMapping
	public List<LoanResponse> getLoans() {
		long userId = CurrentJwt.requireUserId();
		return loanService.getLoans(userId);
	}

	@GetMapping("/{id}")
	public LoanResponse getLoan(@PathVariable Long id) {
		long userId = CurrentJwt.requireUserId();
		return loanService.getLoan(userId, id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LoanResponse createLoan(@Valid @RequestBody CreateLoanRequest request) {
		long userId = CurrentJwt.requireUserId();
		return loanService.createLoan(userId, request);
	}
}
