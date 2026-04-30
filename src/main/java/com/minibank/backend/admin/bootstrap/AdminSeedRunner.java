package com.minibank.backend.admin.bootstrap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.backend.admin.entity.AdminUser;
import com.minibank.backend.admin.entity.AdminUserRole;
import com.minibank.backend.admin.entity.Role;
import com.minibank.backend.admin.repository.AdminUserRepository;
import com.minibank.backend.admin.repository.AdminUserRoleRepository;
import com.minibank.backend.admin.repository.RoleRepository;

@Component
public class AdminSeedRunner implements CommandLineRunner {
	private static final String DEFAULT_ADMIN_USERNAME = "admin@gmail.com";
	private static final String DEFAULT_ADMIN_EMAIL = "admin@gmail.com";
	private static final String DEFAULT_ADMIN_PASSWORD = "123456";
	private static final String DEFAULT_ADMIN_FULL_NAME = "System Admin";
	private static final String ADMIN_ROLE_CODE = "ADMIN";

	private final AdminUserRepository adminUserRepository;
	private final RoleRepository roleRepository;
	private final AdminUserRoleRepository adminUserRoleRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminSeedRunner(
		AdminUserRepository adminUserRepository,
		RoleRepository roleRepository,
		AdminUserRoleRepository adminUserRoleRepository,
		PasswordEncoder passwordEncoder
	) {
		this.adminUserRepository = adminUserRepository;
		this.roleRepository = roleRepository;
		this.adminUserRoleRepository = adminUserRoleRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(String... args) {
		Role adminRole = roleRepository.findByCode(ADMIN_ROLE_CODE)
			.orElseGet(() -> roleRepository.save(Role.builder()
				.code(ADMIN_ROLE_CODE)
				.name("Administrator")
				.description("Full system access")
				.build()));

		AdminUser adminUser = adminUserRepository.findByUsernameIgnoreCase(DEFAULT_ADMIN_USERNAME)
			.orElseGet(() -> adminUserRepository.save(AdminUser.builder()
				.username(DEFAULT_ADMIN_USERNAME)
				.email(DEFAULT_ADMIN_EMAIL)
				.passwordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
				.fullName(DEFAULT_ADMIN_FULL_NAME)
				.status("active")
				.build()));

		if (!adminUserRoleRepository.existsByAdminUserIdAndRoleId(adminUser.getId(), adminRole.getId())) {
			adminUserRoleRepository.save(AdminUserRole.builder()
				.adminUser(adminUser)
				.role(adminRole)
				.build());
		}
	}
}
