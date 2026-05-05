package com.minibank.backend.transaction.controller;

import java.util.List;

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
}
