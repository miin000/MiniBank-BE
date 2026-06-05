package com.minibank.backend.admin.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.backend.account.entity.Account;
import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.admin.dto.AdminAccountOverview;
import com.minibank.backend.admin.dto.AdminAccountSummary;
import com.minibank.backend.user.entity.User;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.minibank.backend.admin.dto.UpdateAccountLimitRequest;

@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {
	private final AccountRepository accountRepository;

	public AdminAccountController(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<AdminAccountSummary> list(
			@RequestParam(value = "q", required = false) String q,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "type", required = false) String type) {
		String query = normalize(q);
		String statusFilter = normalize(status);
		String typeFilter = normalize(type);

		return accountRepository.findAll().stream()
				.filter(a -> statusFilter == null || statusFilter.equalsIgnoreCase(a.getStatus()))
				.filter(a -> typeFilter == null || typeFilter.equalsIgnoreCase(a.getAccountType()))
				.filter(a -> matchesQuery(a, query))
				.map(this::toSummary)
				.toList();
	}

	@GetMapping("/overview")
	@Transactional(readOnly = true)
	public AdminAccountOverview overview() {
		List<Account> accounts = accountRepository.findAll();
		long total = accounts.size();
		long active = accounts.stream().filter(a -> "active".equalsIgnoreCase(a.getStatus())).count();
		long locked = accounts.stream().filter(a -> "locked".equalsIgnoreCase(a.getStatus())).count();
		BigDecimal totalBalance = accounts.stream()
				.map(a -> a.getCurrentBalance() == null ? BigDecimal.ZERO : a.getCurrentBalance())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new AdminAccountOverview(total, active, locked, totalBalance);
	}

	@PutMapping("/{id}/limits")
	@Transactional
	public void updateLimits(
			@PathVariable Long id,
			@RequestBody UpdateAccountLimitRequest request) {

		Account account = accountRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Account not found"));

		account.setDailyTransferLimit(
				request.dailyTransferLimit());

		account.setDailyReceiveLimit(
				request.dailyReceiveLimit());

		accountRepository.save(account);
	}

	private AdminAccountSummary toSummary(Account account) {
		User user = account.getUser();
		return new AdminAccountSummary(
				account.getId(),
				account.getAccountNumber(),
				account.getAccountName(),
				account.getAccountType(),
				account.getCurrency(),
				account.getAvailableBalance(),
				account.getCurrentBalance(),
				account.getDailyTransferLimit(),
				account.getDailyReceiveLimit(),
				account.getStatus(),
				account.getOpenedAt(),
				user == null ? null : user.getId(),
				user == null ? null : user.getFullName(),
				user == null ? null : user.getPhone());
	}

	private boolean matchesQuery(Account account, String query) {
		if (query == null) {
			return true;
		}
		String accountNumber = safeLower(account.getAccountNumber());
		String accountName = safeLower(account.getAccountName());
		User user = account.getUser();
		String ownerName = user == null ? "" : safeLower(user.getFullName());
		String ownerPhone = user == null ? "" : safeLower(user.getPhone());
		return accountNumber.contains(query)
				|| accountName.contains(query)
				|| ownerName.contains(query)
				|| ownerPhone.contains(query);
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim().toLowerCase();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String safeLower(String value) {
		return value == null ? "" : value.toLowerCase();
	}
}
