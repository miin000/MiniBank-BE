package com.minibank.backend.loan.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.minibank.backend.account.entity.Account;
import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.loan.dto.CreateLoanRequest;
import com.minibank.backend.loan.dto.LoanResponse;
import com.minibank.backend.loan.entity.Loan;
import com.minibank.backend.loan.entity.LoanApplication;
import com.minibank.backend.loan.entity.LoanProduct;
import com.minibank.backend.loan.repository.LoanRepository;
import com.minibank.backend.user.entity.User;
import com.minibank.backend.user.repository.UserRepository;

@Service
public class LoanService {
	private final LoanRepository loanRepository;
	private final AccountRepository accountRepository;
	private final UserRepository userRepository;

	public LoanService(
		LoanRepository loanRepository,
		AccountRepository accountRepository,
		UserRepository userRepository
	) {
		this.loanRepository = loanRepository;
		this.accountRepository = accountRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<LoanResponse> getLoans(long userId) {
		return loanRepository.findByUserId(userId)
			.stream()
			.map(this::toLoanResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public LoanResponse getLoan(long userId, long loanId) {
		Loan loan = loanRepository.findByIdAndUserId(loanId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found"));
		return toLoanResponse(loan);
	}

	@Transactional
	public LoanResponse createLoan(long userId, CreateLoanRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

		Account disbursementAccount = accountRepository.findById(request.disbursementAccountId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Disbursement account not found"));

		Account repaymentAccount = accountRepository.findById(request.repaymentAccountId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repayment account not found"));

		// Verify user owns both accounts
		if (!disbursementAccount.getUser().getId().equals(userId) || !repaymentAccount.getUser().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to user");
		}

		// Create loan application (would be created by user workflow)
		LoanApplication loanApplication = LoanApplication.builder()
			.user(user)
			.status("PENDING")
			.build();

		// Create loan
		Loan loan = Loan.builder()
			.user(user)
			.loanApplication(loanApplication)
			.disbursementAccount(disbursementAccount)
			.repaymentAccount(repaymentAccount)
			.approvedAmount(request.amount())
			.disbursedAmount(request.amount())
			.status("ACTIVE")
			.build();

		loan = loanRepository.save(loan);
		return toLoanResponse(loan);
	}

	private LoanResponse toLoanResponse(Loan loan) {
		return new LoanResponse(
			loan.getId(),
			loan.getCode(),
			loan.getApprovedAmount(),
			loan.getDisbursedAmount(),
			loan.getOutstandingPrincipal(),
			loan.getOutstandingInterest(),
			loan.getStatus(),
			loan.getRepaymentFrequency(),
			loan.getTermMonths(),
			loan.getNextDueDate(),
			loan.getClosedAt(),
			loan.getCreatedAt()
		);
	}
}
