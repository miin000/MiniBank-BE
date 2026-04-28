package com.minibank.backend.admin.service;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.minibank.backend.account.entity.Account;
import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.account.service.AccountNumberService;
import com.minibank.backend.user.entity.KycRequest;
import com.minibank.backend.user.entity.User;
import com.minibank.backend.user.repository.KycRequestRepository;
import com.minibank.backend.user.repository.UserRepository;

@Service
public class AdminKycService {
	private final KycRequestRepository kycRequestRepository;
	private final UserRepository userRepository;
	private final AccountRepository accountRepository;
	private final AccountNumberService accountNumberService;

	public AdminKycService(
		KycRequestRepository kycRequestRepository,
		UserRepository userRepository,
		AccountRepository accountRepository,
		AccountNumberService accountNumberService
	) {
		this.kycRequestRepository = kycRequestRepository;
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
		this.accountNumberService = accountNumberService;
	}

	@Transactional
	public void approve(Long kycRequestId, Long adminUserId, String providedAccountNumber, String note) {
		KycRequest kyc = kycRequestRepository.findById(kycRequestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KYC request not found"));
		if (!"pending".equalsIgnoreCase(kyc.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KYC request is not pending");
		}

		User user = kyc.getUser();
		if (user == null || user.getId() == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "KYC request missing user");
		}

		String accountNumber = null;
		if (providedAccountNumber != null && !providedAccountNumber.isBlank()) {
			String trimmed = providedAccountNumber.trim();
			if (!trimmed.matches("^[0-9]{14}$")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountNumber must be 14 digits");
			}
			if (accountRepository.existsByAccountNumber(trimmed)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "accountNumber already exists");
			}
			accountNumber = trimmed;
		} else {
			accountNumber = accountNumberService.generateUniqueAccountNumber();
		}

		// Update user profile from KYC data
		user.setFullName(kyc.getFullName());
		user.setDob(kyc.getDob());
		user.setCitizenId(kyc.getCitizenId());
		user.setAddress(kyc.getAddress());
		user.setStatus("active");
		userRepository.save(user);

		// Create primary payment account if not exists
		boolean hasAccount = !accountRepository.findByUserIdOrderByIdAsc(user.getId()).isEmpty();
		if (!hasAccount) {
			Account account = Account.builder()
				.user(user)
				.accountNumber(accountNumber)
				.accountName(kyc.getFullName())
				.accountType("payment")
				.currency("VND")
				.availableBalance(BigDecimal.ZERO)
				.currentBalance(BigDecimal.ZERO)
				.dailyTransferLimit(new BigDecimal("50000000"))
				.dailyReceiveLimit(new BigDecimal("50000000"))
				.status("active")
				.openedAt(Instant.now())
				.build();
			accountRepository.save(account);
		}

		kyc.setStatus("approved");
		kyc.setReviewedAt(Instant.now());
		kyc.setReviewNote(note);
		// reviewedBy link omitted in MVP (adminUser entity lookup not mandatory)
		kycRequestRepository.save(kyc);
	}

	@Transactional
	public void reject(Long kycRequestId, Long adminUserId, String note) {
		KycRequest kyc = kycRequestRepository.findById(kycRequestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KYC request not found"));
		if (!"pending".equalsIgnoreCase(kyc.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KYC request is not pending");
		}

		kyc.setStatus("rejected");
		kyc.setReviewedAt(Instant.now());
		kyc.setReviewNote(note);
		kycRequestRepository.save(kyc);
	}
}
