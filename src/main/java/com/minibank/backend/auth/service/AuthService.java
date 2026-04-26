package com.minibank.backend.auth.service;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.minibank.backend.admin.entity.AdminUser;
import com.minibank.backend.admin.repository.AdminUserRepository;
import com.minibank.backend.admin.repository.AdminUserRoleRepository;
import com.minibank.backend.auth.dto.AdminRegisterRequest;
import com.minibank.backend.auth.dto.AuthResponse;
import com.minibank.backend.auth.dto.LoginRequest;
import com.minibank.backend.auth.dto.UserRegisterRequest;
import com.minibank.backend.user.entity.User;
import com.minibank.backend.user.repository.UserRepository;

@Service
public class AuthService {

	private final ObjectProvider<AdminUserRepository> adminUserRepository;
	private final ObjectProvider<AdminUserRoleRepository> adminUserRoleRepository;
	private final ObjectProvider<UserRepository> userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;

	public AuthService(
		ObjectProvider<AdminUserRepository> adminUserRepository,
		ObjectProvider<AdminUserRoleRepository> adminUserRoleRepository,
		ObjectProvider<UserRepository> userRepository,
		PasswordEncoder passwordEncoder,
		JwtTokenService jwtTokenService
	) {
		this.adminUserRepository = adminUserRepository;
		this.adminUserRoleRepository = adminUserRoleRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
	}

	private AdminUserRepository adminUsers() {
		AdminUserRepository repo = adminUserRepository.getIfAvailable();
		if (repo == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Auth is not available (database disabled)");
		}
		return repo;
	}

	private AdminUserRoleRepository adminUserRoles() {
		AdminUserRoleRepository repo = adminUserRoleRepository.getIfAvailable();
		if (repo == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Auth is not available (database disabled)");
		}
		return repo;
	}

	private UserRepository users() {
		UserRepository repo = userRepository.getIfAvailable();
		if (repo == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Auth is not available (database disabled)");
		}
		return repo;
	}

	@Transactional
	public AuthResponse registerAdmin(AdminRegisterRequest request) {
		String username = request.username().trim();
		String email = request.email().trim().toLowerCase();

		if (adminUsers().existsByUsernameIgnoreCase(username)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
		}
		if (adminUsers().existsByEmailIgnoreCase(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
		}

		AdminUser adminUser = AdminUser.builder()
			.username(username)
			.email(email)
			.passwordHash(passwordEncoder.encode(request.password()))
			.fullName(request.fullName().trim())
			.status("active")
			.build();

		AdminUser saved = adminUsers().save(adminUser);

		List<String> roles = List.of("ADMIN");
		JwtTokenService.IssuedToken token = jwtTokenService.issueAccessToken(saved.getId(), "ADMIN", saved.getUsername(), roles);

		return new AuthResponse(
			"Bearer",
			token.token(),
			token.expiresInSeconds(),
			new AuthResponse.UserInfo(saved.getId(), "ADMIN", saved.getUsername(), null, roles)
		);
	}

	@Transactional(readOnly = true)
	public AuthResponse loginAdmin(LoginRequest request) {
		String username = request.identifier().trim();
		AdminUser adminUser = adminUsers().findByUsernameIgnoreCase(username)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

		if (!passwordEncoder.matches(request.password(), adminUser.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}
		if (adminUser.getStatus() != null && !adminUser.getStatus().equalsIgnoreCase("active")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
		}

		List<String> roles = adminUserRoles().findRoleCodesByAdminUserId(adminUser.getId());
		if (roles == null || roles.isEmpty()) {
			roles = List.of("ADMIN");
		}

		JwtTokenService.IssuedToken token = jwtTokenService.issueAccessToken(adminUser.getId(), "ADMIN", adminUser.getUsername(), roles);
		return new AuthResponse(
			"Bearer",
			token.token(),
			token.expiresInSeconds(),
			new AuthResponse.UserInfo(adminUser.getId(), "ADMIN", adminUser.getUsername(), null, roles)
		);
	}

	@Transactional
	public AuthResponse registerUser(UserRegisterRequest request) {
		String phone = request.phone().trim();
		String email = request.email().trim().toLowerCase();

		if (users().existsByPhone(phone)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone already exists");
		}
		if (users().existsByEmailIgnoreCase(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
		}

		User user = User.builder()
			.phone(phone)
			.email(email)
			.passwordHash(passwordEncoder.encode(request.password()))
			.fullName(request.fullName() == null ? null : request.fullName().trim())
			.status("pending")
			.customerRank("dong")
			.build();

		User saved = users().save(user);

		List<String> roles = List.of("USER");
		JwtTokenService.IssuedToken token = jwtTokenService.issueAccessToken(saved.getId(), "USER", saved.getPhone(), roles);
		return new AuthResponse(
			"Bearer",
			token.token(),
			token.expiresInSeconds(),
			new AuthResponse.UserInfo(saved.getId(), "USER", null, saved.getPhone(), roles)
		);
	}

	@Transactional(readOnly = true)
	public AuthResponse loginUser(LoginRequest request) {
		String phone = request.identifier().trim();
		User user = users().findByPhone(phone)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}
		if (user.getStatus() != null && user.getStatus().equalsIgnoreCase("blocked")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is blocked");
		}

		List<String> roles = List.of("USER");
		JwtTokenService.IssuedToken token = jwtTokenService.issueAccessToken(user.getId(), "USER", user.getPhone(), roles);
		return new AuthResponse(
			"Bearer",
			token.token(),
			token.expiresInSeconds(),
			new AuthResponse.UserInfo(user.getId(), "USER", null, user.getPhone(), roles)
		);
	}
}
