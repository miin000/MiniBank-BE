package com.minibank.backend.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.minibank.backend.user.entity.KycRequest;

public interface KycRequestRepository extends JpaRepository<KycRequest, Long> {
	List<KycRequest> findByStatusOrderBySubmittedAtAsc(String status);

	Optional<KycRequest> findByIdAndUserId(Long id, Long userId);
}
