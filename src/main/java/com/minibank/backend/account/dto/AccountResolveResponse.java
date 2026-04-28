package com.minibank.backend.account.dto;

public record AccountResolveResponse(
	String accountNumber,
	String accountName
) {}
