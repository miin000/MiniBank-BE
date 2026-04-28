package com.minibank.backend.user.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KycSubmitRequest(
	@NotBlank @Size(max = 255) String fullName,
	@NotNull LocalDate dob,
	@NotBlank @Size(max = 50) String citizenId,
	@NotBlank @Size(max = 2000) String address
) {}
