package com.minibank.backend.transaction.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.minibank.backend.transaction.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
	Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

	Optional<Transaction> findByIdAndInitiatedByUserId(Long id, Long initiatedByUserId);

	long countByStatus(String status);
}
