package com.minibank.backend.support.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.minibank.backend.support.entity.ServiceRequest;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
	List<ServiceRequest> findByUserId(long userId);
	Optional<ServiceRequest> findByIdAndUserId(long id, long userId);
	List<ServiceRequest> findByUserIdOrderBySubmittedAtDesc(long userId);
}
