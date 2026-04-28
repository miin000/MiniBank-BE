package com.minibank.backend.admin.dto;

import java.time.Instant;
import java.time.LocalDate;

public record KycRequestSummary(
	Long id,
	Long userId,
	String phone,
	String email,
	String fullName,
	LocalDate dob,
	String citizenId,
	String address,
	String status,
	Instant submittedAt
) {}
