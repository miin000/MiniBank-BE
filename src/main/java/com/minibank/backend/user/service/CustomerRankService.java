package com.minibank.backend.user.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.user.entity.User;
import com.minibank.backend.user.repository.UserRepository;

@Service
public class CustomerRankService {
	private final AccountRepository accountRepository;
	private final UserRepository userRepository;

	public CustomerRankService(AccountRepository accountRepository, UserRepository userRepository) {
		this.accountRepository = accountRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public String refreshRank(User user) {
		BigDecimal totalBalance = accountRepository.findByUserIdOrderByIdAsc(user.getId()).stream()
			.map(account -> account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance())
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		String rank = rankForBalance(totalBalance);
		String scoreLevel = creditLevelForRank(rank);
		if (!rank.equalsIgnoreCase(nullToEmpty(user.getCustomerRank()))
			|| !scoreLevel.equalsIgnoreCase(nullToEmpty(user.getCreditScoreLevel()))) {
			user.setCustomerRank(rank);
			user.setCreditScoreLevel(scoreLevel);
			userRepository.save(user);
		}
		return rank;
	}

	public String rankForBalance(BigDecimal balance) {
		BigDecimal value = balance == null ? BigDecimal.ZERO : balance;
		if (value.compareTo(new BigDecimal("2000000000")) >= 0) return "kim_cuong";
		if (value.compareTo(new BigDecimal("1000000000")) >= 0) return "bach_kim";
		if (value.compareTo(new BigDecimal("200000000")) >= 0) return "vang";
		if (value.compareTo(new BigDecimal("50000000")) >= 0) return "bac";
		return "dong";
	}

	private String creditLevelForRank(String rank) {
		return switch (rank) {
			case "kim_cuong", "bach_kim" -> "A";
			case "vang" -> "B";
			case "bac" -> "C";
			default -> "D";
		};
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
