package com.minibank.backend.saving.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.minibank.backend.saving.entity.Saving;

@Repository
public interface SavingRepository extends JpaRepository<Saving, Long> {
	List<Saving> findByUserId(long userId);

	@EntityGraph(attributePaths = {"user", "savingProduct", "sourceAccount", "settlementAccount"})
	List<Saving> findByUserIdOrderByCreatedAtDesc(long userId);

	@EntityGraph(attributePaths = {"user", "savingProduct", "sourceAccount", "settlementAccount"})
	List<Saving> findByStatusOrderByCreatedAtDesc(String status);

	Optional<Saving> findByCode(String code);
	Optional<Saving> findByIdAndUserId(long id, long userId);

	@EntityGraph(attributePaths = {"user", "savingProduct", "sourceAccount", "settlementAccount"})
	Optional<Saving> findWithDetailsByIdAndUserId(Long id, Long userId);

	@EntityGraph(attributePaths = {"user", "savingProduct", "sourceAccount", "settlementAccount"})
	List<Saving> findAllByOrderByCreatedAtDesc();

	@EntityGraph(attributePaths = {"user", "savingProduct", "sourceAccount", "settlementAccount"})
	Optional<Saving> findWithDetailsById(Long id);
}
