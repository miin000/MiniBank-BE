package com.minibank.backend.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.common.security.CurrentJwt;
import com.minibank.backend.user.dto.ChangePasswordRequest;
import com.minibank.backend.user.dto.ProfileResponse;
import com.minibank.backend.user.dto.ProfileUpdateRequest;
import com.minibank.backend.user.dto.PublicKeyRequest;
import com.minibank.backend.user.dto.SetTransactionPinRequest;
import com.minibank.backend.user.entity.User;
import com.minibank.backend.user.repository.UserRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mobile/profile")
public class MobileProfileController {
	private final UserRepository userRepository;
	private final AccountRepository accountRepository;
	private final PasswordEncoder passwordEncoder;

	public MobileProfileController(UserRepository userRepository, AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping("/me")
	@Transactional(readOnly = true)
	public ProfileResponse me() {
		long userId = CurrentJwt.requireUserId();
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

		List<ProfileResponse.AccountSummary> accounts = accountRepository.findByUserIdOrderByIdAsc(user.getId()).stream()
			.map(a -> new ProfileResponse.AccountSummary(a.getId(), a.getAccountNumber(), a.getAccountName(), a.getStatus()))
			.toList();

		return new ProfileResponse(
			user.getId(),
			user.getPhone(),
			user.getEmail(),
			user.getFullName(),
			user.getDob(),
			user.getAddress(),
			user.getStatus(),
			user.getCustomerRank(),
			user.getTransactionPinHash() != null && !user.getTransactionPinHash().isBlank(),
			user.getPublicKey() != null && !user.getPublicKey().isBlank(),
			user.getDeviceId(),
			accounts
		);
	}

	@PutMapping("/me")
	@Transactional
	public ProfileResponse update(@Valid @RequestBody ProfileUpdateRequest request) {
		long userId = CurrentJwt.requireUserId();
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

		if (request.fullName() != null) {
			user.setFullName(request.fullName().trim());
		}
		if (request.dob() != null) {
			user.setDob(request.dob());
		}
		if (request.address() != null) {
			user.setAddress(request.address().trim());
		}

		userRepository.save(user);
		return me();
	}

	@PostMapping("/change-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		long userId = CurrentJwt.requireUserId();
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

		if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid old password");
		}
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
	}

	@PostMapping("/transaction-pin")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void setOrChangePin(@Valid @RequestBody SetTransactionPinRequest request) {
		long userId = CurrentJwt.requireUserId();
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

		if (!request.newPin().matches("^[0-9]{6}$")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PIN must be 6 digits");
		}

		boolean hasPin = user.getTransactionPinHash() != null && !user.getTransactionPinHash().isBlank();
		if (hasPin) {
			if (request.oldPin() == null || request.oldPin().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "oldPin is required");
			}
			if (!passwordEncoder.matches(request.oldPin(), user.getTransactionPinHash())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid old PIN");
			}
		}

		user.setTransactionPinHash(passwordEncoder.encode(request.newPin()));
		userRepository.save(user);
	}

	@PostMapping("/public-key")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void setPublicKey(@Valid @RequestBody PublicKeyRequest request) {
		long userId = CurrentJwt.requireUserId();
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

		String pem = request.publicKey().trim();
		if (!pem.contains("BEGIN PUBLIC KEY")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "publicKey must be PEM encoded");
		}

		user.setPublicKey(pem);
		userRepository.save(user);
	}

	@PostMapping("/reset-device")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void resetDevice() {
		long userId = CurrentJwt.requireUserId();
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

		user.setDeviceId(null);
		userRepository.save(user);
	}
}
