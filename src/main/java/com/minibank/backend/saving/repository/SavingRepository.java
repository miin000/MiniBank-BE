package com.minibank.backend.saving.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.minibank.backend.saving.entity.Saving;

@Repository
public interface SavingRepository extends JpaRepository<Saving, Long> {
	List<Saving> findByUserId(long userId);
	List<Saving> findByUserIdOrderByCreatedAtDesc(long userId);
	Optional<Saving> findByCode(String code);
	Optional<Saving> findByIdAndUserId(long id, long userId);
}
