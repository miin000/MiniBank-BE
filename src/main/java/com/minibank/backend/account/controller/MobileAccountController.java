package com.minibank.backend.account.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.minibank.backend.account.dto.AccountQrResponse;
import com.minibank.backend.account.dto.AccountResolveResponse;
import com.minibank.backend.account.dto.AccountSuggestionsResponse;
import com.minibank.backend.account.entity.Account;
import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.account.service.AccountNumberService;
import com.minibank.backend.account.service.AccountQrService;
import com.minibank.backend.common.security.CurrentJwt;

@RestController
@RequestMapping("/api/mobile/accounts")
public class MobileAccountController {
	private final AccountRepository accountRepository;
	private final AccountNumberService accountNumberService;
	private final AccountQrService accountQrService;

	public MobileAccountController(AccountRepository accountRepository, AccountNumberService accountNumberService, AccountQrService accountQrService) {
		this.accountRepository = accountRepository;
		this.accountNumberService = accountNumberService;
		this.accountQrService = accountQrService;
	}

	@GetMapping("/me")
	@Transactional(readOnly = true)
	public List<AccountResolveResponse> myAccounts() {
		long userId = CurrentJwt.requireUserId();
		return accountRepository.findByUserIdOrderByIdAsc(userId).stream()
			.map(a -> new AccountResolveResponse(a.getAccountNumber(), a.getAccountName()))
			.toList();
	}

	@GetMapping("/resolve")
	@Transactional(readOnly = true)
	public AccountResolveResponse resolve(@RequestParam("accountNumber") String accountNumber) {
		String accNo = accountNumber == null ? null : accountNumber.trim();
		if (accNo == null || accNo.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountNumber is required");
		}

		Account acc = accountRepository.findByAccountNumber(accNo)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
		return new AccountResolveResponse(acc.getAccountNumber(), acc.getAccountName());
	}

	@GetMapping("/suggestions")
	public AccountSuggestionsResponse suggestions(@RequestParam("desired") String desired, @RequestParam(value = "limit", required = false) Integer limit) {
		int finalLimit = limit == null ? 10 : limit;
		return new AccountSuggestionsResponse(desired, accountNumberService.suggestAccountNumbers(desired, finalLimit));
	}

	@GetMapping("/me/qr")
	@Transactional
	public AccountQrResponse myQr() {
		long userId = CurrentJwt.requireUserId();
		Account account = accountRepository.findByUserIdOrderByIdAsc(userId).stream()
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "No account is assigned"));

		String payload = accountQrService.getOrCreateActivePayload(account);
		return new AccountQrResponse(account.getAccountNumber(), account.getAccountName(), payload);
	}
}
