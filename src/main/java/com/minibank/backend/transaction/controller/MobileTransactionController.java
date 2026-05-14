package com.minibank.backend.transaction.controller;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.backend.account.entity.Account;
import com.minibank.backend.common.security.CurrentJwt;
import com.minibank.backend.transaction.dto.MobileTransactionSummary;
import com.minibank.backend.transaction.entity.Transaction;
import com.minibank.backend.transaction.repository.TransactionRepository;

@RestController
@RequestMapping("/api/mobile/transactions")
public class MobileTransactionController {
	private final TransactionRepository transactionRepository;

	public MobileTransactionController(TransactionRepository transactionRepository) {
		this.transactionRepository = transactionRepository;
	}

	@GetMapping("/recent")
	@Transactional(readOnly = true)
	public List<MobileTransactionSummary> recent(@RequestParam(value = "limit", required = false) Integer limit) {
		long userId = CurrentJwt.requireUserId();
		int finalLimit = limit == null ? 5 : Math.min(Math.max(limit, 1), 50);
		Pageable pageable = PageRequest.of(0, finalLimit);
		return transactionRepository.findRecentForUser(userId, pageable).stream()
			.map(tx -> toSummary(tx, userId))
			.toList();
	}

	@GetMapping("/history")
	@Transactional(readOnly = true)
	public List<MobileTransactionSummary> history(
		@RequestParam(value = "limit", required = false) Integer limit,
		@RequestParam(value = "page", required = false) Integer page,
		@RequestParam(value = "direction", required = false) String direction,
		@RequestParam(value = "status", required = false) String status,
		@RequestParam(value = "q", required = false) String query
	) {
		long userId = CurrentJwt.requireUserId();
		int finalLimit = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
		int finalPage = page == null ? 0 : Math.max(page, 0);
		String normalizedDirection = direction == null ? "all" : direction.toLowerCase(Locale.ROOT);
		String normalizedStatus = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
		String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		Pageable pageable = PageRequest.of(finalPage, finalLimit);

		return transactionRepository.findRecentForUser(userId, pageable).stream()
			.filter(tx -> matchesDirection(tx, userId, normalizedDirection))
			.filter(tx -> matchesStatus(tx, normalizedStatus))
			.filter(tx -> matchesQuery(tx, userId, normalizedQuery))
			.map(tx -> toSummary(tx, userId))
			.toList();
	}

	@GetMapping("/pending")
	@Transactional(readOnly = true)
	public List<MobileTransactionSummary> pending(
		@RequestParam(value = "limit", required = false) Integer limit
	) {
		long userId = CurrentJwt.requireUserId();
		int finalLimit = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
		Pageable pageable = PageRequest.of(0, finalLimit);
		List<String> statuses = List.of("pending", "pending_review", "pending_manager");
		return transactionRepository.findPendingForUser(userId, statuses, pageable).stream()
			.map(tx -> toSummary(tx, userId))
			.toList();
	}

	private static MobileTransactionSummary toSummary(Transaction tx, long userId) {
		Account from = tx.getFromAccount();
		Account to = tx.getToAccount();
		boolean incoming = to != null && to.getUser() != null && userId == to.getUser().getId();
		Account counterparty = incoming ? from : to;
		return new MobileTransactionSummary(
			tx.getId(),
			incoming ? "in" : "out",
			tx.getAmount(),
			tx.getDescription(),
			counterparty == null ? null : counterparty.getAccountNumber(),
			counterparty == null ? null : counterparty.getAccountName(),
			tx.getTransactionType(),
			tx.getStatus(),
			tx.getCreatedAt()
		);
	}

	private static boolean matchesDirection(Transaction tx, long userId, String direction) {
		if (direction == null || direction.isBlank() || direction.equals("all")) {
			return true;
		}
		boolean incoming = isIncoming(tx, userId);
		return direction.equals("in") ? incoming : !incoming;
	}

	private static boolean matchesStatus(Transaction tx, String status) {
		if (status == null || status.isBlank()) {
			return true;
		}
		String normalized = status.toLowerCase(Locale.ROOT);
		for (String token : normalized.split(",")) {
			String s = token.trim();
			if (!s.isEmpty() && s.equalsIgnoreCase(tx.getStatus())) {
				return true;
			}
		}
		return false;
	}

	private static boolean matchesQuery(Transaction tx, long userId, String query) {
		if (query == null || query.isBlank()) {
			return true;
		}
		Account counterparty = isIncoming(tx, userId) ? tx.getFromAccount() : tx.getToAccount();
		return containsIgnoreCase(tx.getDescription(), query)
			|| containsIgnoreCase(tx.getTransactionType(), query)
			|| containsIgnoreCase(counterparty == null ? null : counterparty.getAccountNumber(), query)
			|| containsIgnoreCase(counterparty == null ? null : counterparty.getAccountName(), query);
	}

	private static boolean containsIgnoreCase(String value, String query) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(query);
	}

	private static boolean isIncoming(Transaction tx, long userId) {
		Account to = tx.getToAccount();
		return to != null && to.getUser() != null && Objects.equals(to.getUser().getId(), userId);
	}
}
