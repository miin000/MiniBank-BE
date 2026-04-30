package com.minibank.backend.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.backend.auth.dto.AuthResponse;
import com.minibank.backend.auth.dto.LoginRequest;
import com.minibank.backend.auth.dto.UserRegisterRequest;
import com.minibank.backend.auth.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {

	private final AuthService authService;

	public MobileAuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(@Valid @RequestBody UserRegisterRequest request) {
		return authService.registerUser(request);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.loginUser(request);
	}

}
