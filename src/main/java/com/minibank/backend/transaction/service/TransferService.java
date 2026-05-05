package com.minibank.backend.transaction.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.minibank.backend.account.entity.Account;
import com.minibank.backend.account.entity.AccountBalanceLedger;
import com.minibank.backend.account.repository.AccountBalanceLedgerRepository;
import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.transaction.dto.TransferConfirmRequest;
import com.minibank.backend.transaction.dto.TransferConfirmResponse;
import com.minibank.backend.transaction.dto.TransferInitiateRequest;
import com.minibank.backend.transaction.dto.TransferInitiateResponse;
import com.minibank.backend.transaction.entity.Transaction;
import com.minibank.backend.transaction.entity.TransactionAuthentication;
import com.minibank.backend.transaction.repository.TransactionAuthenticationRepository;
import com.minibank.backend.transaction.repository.TransactionRepository;
import com.minibank.backend.user.entity.User;
import com.minibank.backend.user.repository.UserRepository;

@Service
public class TransferService {
	private static final SecureRandom RNG = new SecureRandom();

	private final UserRepository userRepository;
	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final TransactionAuthenticationRepository transactionAuthenticationRepository;
	private final AccountBalanceLedgerRepository ledgerRepository;
	private final PasswordEncoder passwordEncoder;
	private final RsaSignatureService rsaSignatureService;
	private final boolean debugReturnOtp;

	public TransferService(
		UserRepository userRepository,
		AccountRepository accountRepository,
		TransactionRepository transactionRepository,
		TransactionAuthenticationRepository transactionAuthenticationRepository,
		AccountBalanceLedgerRepository ledgerRepository,
		PasswordEncoder passwordEncoder,
		RsaSignatureService rsaSignatureService,
		@Value("${app.otp.debug-return:false}") boolean debugReturnOtp
	) {
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
		this.transactionAuthenticationRepository = transactionAuthenticationRepository;
		this.ledgerRepository = ledgerRepository;
		this.passwordEncoder = passwordEncoder;
		this.rsaSignatureService = rsaSignatureService;
		this.debugReturnOtp = debugReturnOtp;
	}

	@Transactional
	public TransferInitiateResponse initiate(long userId, TransferInitiateRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
		if (!"active".equalsIgnoreCase(user.getStatus())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
		}

		Account fromAccount = resolveFromAccount(userId, request.fromAccountNumber());
		Account toAccount = accountRepository.findByAccountNumber(request.toAccountNumber().trim())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient account not found"));
		if (!"active".equalsIgnoreCase(toAccount.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient account is not active");
		}

		BigDecimal amount = normalizeAmount(request.amount());
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
		}

		if (fromAccount.getAvailableBalance().compareTo(amount) < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
		}

		BigDecimal dailyLimit = fromAccount.getDailyTransferLimit() == null ? BigDecimal.ZERO : fromAccount.getDailyTransferLimit();
		if (dailyLimit.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(dailyLimit) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds daily transfer limit");
		}

		String canonical = canonicalPayload(fromAccount.getAccountNumber(), toAccount.getAccountNumber(), amount, request.description(), request.idempotencyKey());
		rsaSignatureService.verifyOrThrow(user.getPublicKey(), canonical, request.signature());

		if (user.getTransactionPinHash() == null || user.getTransactionPinHash().isBlank()) {
			throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Transaction PIN is not set");
		}
		if (!passwordEncoder.matches(request.pin(), user.getTransactionPinHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PIN");
		}

		Transaction tx = null;
		if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
			Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey().trim());
			if (existing.isPresent()) {
				tx = existing.get();
				if (tx.getInitiatedByUser() == null || tx.getInitiatedByUser().getId() == null || !tx.getInitiatedByUser().getId().equals(user.getId())) {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used");
				}
			}
		}

		String otp = generateOtp();
		String otpHash = passwordEncoder.encode(otp);

		if (tx == null) {
			tx = Transaction.builder()
				.transactionCode(generateTransactionCode())
				.idempotencyKey(request.idempotencyKey() == null ? null : request.idempotencyKey().trim())
				.fromAccount(fromAccount)
				.toAccount(toAccount)
				.transactionType("transfer")
				.amount(amount)
				.feeAmount(BigDecimal.ZERO)
				.description(request.description() == null ? null : request.description().trim())
				.status("pending")
				.initiatedByUser(user)
				.build();
			tx = transactionRepository.save(tx);

			TransactionAuthentication auth = TransactionAuthentication.builder()
				.transaction(tx)
				.pinVerified(true)
				.otpVerified(false)
				.otpCodeHash(otpHash)
				.digitalSignature(request.signature())
				.authStatus("pin_verified")
				.build();
			transactionAuthenticationRepository.save(auth);
		} else {
			if (!"pending".equalsIgnoreCase(tx.getStatus())) {
				return new TransferInitiateResponse(
					tx.getId(),
					tx.getTransactionCode(),
					tx.getStatus(),
					fromAccount.getAccountNumber(),
					toAccount.getAccountNumber(),
					toAccount.getAccountName(),
					tx.getAmount(),
					true,
					true,
					null
				);
			}

			TransactionAuthentication auth = transactionAuthenticationRepository.findByTransactionId(tx.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing transaction authentication"));
			auth.setOtpCodeHash(otpHash);
			auth.setOtpVerified(false);
			auth.setPinVerified(true);
			auth.setDigitalSignature(request.signature());
			auth.setAuthStatus("pin_verified");
			transactionAuthenticationRepository.save(auth);
		}

		return new TransferInitiateResponse(
			tx.getId(),
			tx.getTransactionCode(),
			tx.getStatus(),
			fromAccount.getAccountNumber(),
			toAccount.getAccountNumber(),
			toAccount.getAccountName(),
			amount,
			true,
			true,
			debugReturnOtp ? otp : null
		);
	}

	@Transactional
	public TransferConfirmResponse confirm(long userId, TransferConfirmRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
		if (!"active".equalsIgnoreCase(user.getStatus())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
		}

		Transaction tx = transactionRepository.findByIdAndInitiatedByUserId(request.transactionId(), user.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
		if ("completed".equalsIgnoreCase(tx.getStatus())) {
			Account fromAcc = tx.getFromAccount();
			return new TransferConfirmResponse(tx.getId(), tx.getStatus(), tx.getCompletedAt(), fromAcc.getAccountNumber(), tx.getToAccount().getAccountNumber(), tx.getAmount(), fromAcc.getAvailableBalance());
		}
		if (!"pending".equalsIgnoreCase(tx.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is not pending");
		}

		TransactionAuthentication auth = transactionAuthenticationRepository.findByTransactionId(tx.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing transaction authentication"));

		if (auth.getOtpCodeHash() == null || !passwordEncoder.matches(request.otpCode(), auth.getOtpCodeHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP");
		}
		auth.setOtpVerified(true);
		auth.setAuthStatus("verified");
		auth.setVerifiedAt(Instant.now());
		transactionAuthenticationRepository.save(auth);

		// ACID transfer: lock both accounts before updating balances.
		Account fromAccount = accountRepository.findByIdForUpdate(tx.getFromAccount().getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Sender account missing"));
		Account toAccount = accountRepository.findByIdForUpdate(tx.getToAccount().getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Recipient account missing"));

		BigDecimal amount = tx.getAmount();
		if (fromAccount.getAvailableBalance().compareTo(amount) < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
		}

		BigDecimal fromBefore = fromAccount.getAvailableBalance();
		BigDecimal toBefore = toAccount.getAvailableBalance();

		fromAccount.setAvailableBalance(fromBefore.subtract(amount));
		fromAccount.setCurrentBalance(fromAccount.getCurrentBalance().subtract(amount));
		toAccount.setAvailableBalance(toBefore.add(amount));
		toAccount.setCurrentBalance(toAccount.getCurrentBalance().add(amount));

		accountRepository.save(fromAccount);
		accountRepository.save(toAccount);

		ledgerRepository.save(AccountBalanceLedger.builder()
			.account(fromAccount)
			.transaction(tx)
			.entryType("debit")
			.amount(amount)
			.balanceBefore(fromBefore)
			.balanceAfter(fromAccount.getAvailableBalance())
			.build());

		ledgerRepository.save(AccountBalanceLedger.builder()
			.account(toAccount)
			.transaction(tx)
			.entryType("credit")
			.amount(amount)
			.balanceBefore(toBefore)
			.balanceAfter(toAccount.getAvailableBalance())
			.build());

		tx.setStatus("completed");
		tx.setCompletedAt(Instant.now());
		transactionRepository.save(tx);

		return new TransferConfirmResponse(
			tx.getId(),
			tx.getStatus(),
			tx.getCompletedAt(),
			fromAccount.getAccountNumber(),
			toAccount.getAccountNumber(),
			amount,
			fromAccount.getAvailableBalance()
		);
	}

	private Account resolveFromAccount(long userId, String fromAccountNumber) {
		if (fromAccountNumber != null && !fromAccountNumber.isBlank()) {
			Account acc = accountRepository.findByAccountNumber(fromAccountNumber.trim())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sender account not found"));
			if (acc.getUser() == null || acc.getUser().getId() == null || acc.getUser().getId() != userId) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sender account does not belong to user");
			}
			return acc;
		}

		return accountRepository.findByUserIdOrderByIdAsc(userId).stream()
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "No account is assigned"));
	}

	private static BigDecimal normalizeAmount(BigDecimal amount) {
		if (amount == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
		}
		return amount.setScale(2, RoundingMode.HALF_UP);
	}

	private static String generateOtp() {
		int v = RNG.nextInt(1_000_000);
		return String.format("%06d", v);
	}

	private static String generateTransactionCode() {
		long now = System.currentTimeMillis();
		int r = RNG.nextInt(900_000) + 100_000;
		return "TX" + now + r;
	}

	private static String canonicalPayload(String fromAcc, String toAcc, BigDecimal amount, String description, String idempotencyKey) {
		String desc = description == null ? "" : description.trim();
		String idem = idempotencyKey == null ? "" : idempotencyKey.trim();
		String amt = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
		return "from=" + fromAcc + "|to=" + toAcc + "|amount=" + amt + "|description=" + desc + "|idempotencyKey=" + idem;
	}
}
