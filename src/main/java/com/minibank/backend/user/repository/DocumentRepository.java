package com.minibank.backend.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.minibank.backend.user.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {

	@Query(
		"select d from Document d " +
		"where (:ownerType is null or d.ownerType = :ownerType) " +
		"and (:ownerId is null or d.ownerId = :ownerId) " +
		"and (:status is null or lower(d.verifiedStatus) = lower(:status)) " +
		"and (:documentType is null or lower(d.documentType) = lower(:documentType)) " +
		"order by d.uploadedAt desc"
	)
	Page<Document> search(
		@Param("ownerType") String ownerType,
		@Param("ownerId") Long ownerId,
		@Param("status") String status,
		@Param("documentType") String documentType,
		Pageable pageable
	);
}
