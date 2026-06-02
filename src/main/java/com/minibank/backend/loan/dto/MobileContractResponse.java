package com.minibank.backend.loan.dto;

import java.time.Instant;

public record MobileContractResponse(
    Long id,
    String ownerType,
    Long ownerId,
    String contractCode,
    String status,
    Instant signedAt,
    Instant createdAt
) {}