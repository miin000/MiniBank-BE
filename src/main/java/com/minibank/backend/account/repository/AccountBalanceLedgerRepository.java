package com.minibank.backend.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.minibank.backend.account.entity.AccountBalanceLedger;

public interface AccountBalanceLedgerRepository extends JpaRepository<AccountBalanceLedger, Long> {}
