package com.minibank.backend.admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.backend.admin.dto.KycDecisionRequest;
import com.minibank.backend.admin.dto.KycRequestSummary;
import com.minibank.backend.admin.service.AdminKycService;
import com.minibank.backend.common.security.CurrentJwt;
import com.minibank.backend.user.entity.KycRequest;
import com.minibank.backend.user.repository.KycRequestRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/kyc")
@PreAuthorize("hasRole('ADMIN')")
public class AdminKycController {
	private final KycRequestRepository kycRequestRepository;
	private final AdminKycService adminKycService;

	public AdminKycController(KycRequestRepository kycRequestRepository, AdminKycService adminKycService) {
		this.kycRequestRepository = kycRequestRepository;
		this.adminKycService = adminKycService;
	}

	@GetMapping("/pending")
	@Transactional(readOnly = true)
	public List<KycRequestSummary> pending() {
		return kycRequestRepository.findByStatusOrderBySubmittedAtAsc("pending").stream()
			.map(this::toSummary)
			.toList();
	}

	@PostMapping("/{kycRequestId}/approve")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void approve(@PathVariable Long kycRequestId, @Valid @RequestBody KycDecisionRequest request) {
		long adminUserId = CurrentJwt.requireUserId();
		adminKycService.approve(kycRequestId, adminUserId, request.accountNumber(), request.note());
	}

	@PostMapping("/{kycRequestId}/reject")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reject(@PathVariable Long kycRequestId, @Valid @RequestBody KycDecisionRequest request) {
		long adminUserId = CurrentJwt.requireUserId();
		adminKycService.reject(kycRequestId, adminUserId, request.note());
	}

	private KycRequestSummary toSummary(KycRequest k) {
		return new KycRequestSummary(
			k.getId(),
			k.getUser().getId(),
			k.getUser().getPhone(),
			k.getUser().getEmail(),
			k.getFullName(),
			k.getDob(),
			k.getCitizenId(),
			k.getAddress(),
			k.getStatus(),
			k.getSubmittedAt()
		);
	}
}
