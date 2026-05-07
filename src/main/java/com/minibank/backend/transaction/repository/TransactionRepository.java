package com.minibank.backend.transaction.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.minibank.backend.transaction.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
	Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

	Optional<Transaction> findByIdAndInitiatedByUserId(Long id, Long initiatedByUserId);

	Optional<Transaction> findByQrTransferIntentId(Long qrTransferIntentId);

	long countByStatus(String status);

	@Query("select t from Transaction t where (t.fromAccount.user.id = :userId or t.toAccount.user.id = :userId) order by t.createdAt desc")
	List<Transaction> findRecentForUser(@Param("userId") Long userId, Pageable pageable);
}
