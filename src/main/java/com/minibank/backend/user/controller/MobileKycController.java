package com.minibank.backend.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.minibank.backend.common.security.CurrentJwt;
import com.minibank.backend.user.dto.KycSubmitRequest;
import com.minibank.backend.user.entity.KycRequest;
import com.minibank.backend.user.entity.User;
import com.minibank.backend.user.repository.KycRequestRepository;
import com.minibank.backend.user.repository.UserRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mobile/kyc")
public class MobileKycController {
	private final UserRepository userRepository;
	private final KycRequestRepository kycRequestRepository;

	public MobileKycController(UserRepository userRepository, KycRequestRepository kycRequestRepository) {
		this.userRepository = userRepository;
		this.kycRequestRepository = kycRequestRepository;
	}

	@PostMapping("/submit")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public void submit(@Valid @RequestBody KycSubmitRequest request) {
		long userId = CurrentJwt.requireUserId();
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

		KycRequest kyc = KycRequest.builder()
			.user(user)
			.fullName(request.fullName().trim())
			.dob(request.dob())
			.citizenId(request.citizenId().trim())
			.address(request.address().trim())
			.status("pending")
			.build();
		kycRequestRepository.save(kyc);

		if (user.getStatus() == null || user.getStatus().isBlank()) {
			user.setStatus("pending");
			userRepository.save(user);
		}
	}
}
