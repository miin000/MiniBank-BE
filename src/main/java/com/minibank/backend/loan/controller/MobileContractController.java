package com.minibank.backend.loan.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.backend.account.entity.Account;
import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.common.security.CurrentJwt;
import com.minibank.backend.contract.entity.Contract;
import com.minibank.backend.contract.repository.ContractRepository;
import com.minibank.backend.loan.dto.MobileContractResponse;
import com.minibank.backend.loan.entity.Loan;
import com.minibank.backend.loan.entity.LoanRepaymentSchedule;
import com.minibank.backend.loan.repository.LoanApplicationRepository;
import com.minibank.backend.loan.repository.LoanRepaymentScheduleRepository;
import com.minibank.backend.loan.repository.LoanRepository;
import com.minibank.backend.saving.entity.Saving;
import com.minibank.backend.saving.repository.SavingRepository;
import com.minibank.backend.transaction.entity.Transaction;
import com.minibank.backend.transaction.repository.TransactionRepository;

@RestController
@RequestMapping("/api/mobile/contracts")
public class MobileContractController {
    private final ContractRepository contractRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final SavingRepository savingRepository;
    private final LoanRepository loanRepository;
    private final LoanRepaymentScheduleRepository repaymentScheduleRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public MobileContractController(
        ContractRepository contractRepository,
        LoanApplicationRepository loanApplicationRepository,
        SavingRepository savingRepository,
        LoanRepository loanRepository,
        LoanRepaymentScheduleRepository repaymentScheduleRepository,
        TransactionRepository transactionRepository,
        AccountRepository accountRepository
    ) {
        this.contractRepository = contractRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.savingRepository = savingRepository;
        this.loanRepository = loanRepository;
        this.repaymentScheduleRepository = repaymentScheduleRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<Contract> list() {
        long userId = CurrentJwt.requireUserId();
        List<Contract> result = new ArrayList<>();

        // loan_application owned by user
        loanApplicationRepository.findByUserId(userId)
            .forEach(app -> result.addAll(contractRepository.findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc("loan_application", app.getId())));

        // savings owned by user
        savingRepository.findByUserId(userId).forEach(s -> result.addAll(contractRepository.findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc("saving", s.getId())));

        return result;
    }

    @GetMapping
    public List<MobileContractResponse> list() {
        long userId = CurrentJwt.requireUserId();
        List<MobileContractResponse> result = new ArrayList<>();

        loanApplicationRepository.findByUserId(userId).forEach(app ->
            contractRepository.findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc("loan_application", app.getId())
                .forEach(c -> result.add(toDto(c))));

        savingRepository.findByUserId(userId).forEach(s ->
            contractRepository.findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc("saving", s.getId())
                .forEach(c -> result.add(toDto(c))));

        return result;
    }

    private MobileContractResponse toDto(Contract c) {
        return new MobileContractResponse(
            c.getId(), c.getOwnerType(), c.getOwnerId(),
            c.getContractCode(), c.getStatus(), c.getSignedAt(), c.getCreatedAt()
        );
    }

    @PostMapping("/{id}/sign")
    public Contract sign(@PathVariable Long id) {
        long userId = CurrentJwt.requireUserId();
        Contract c = contractRepository.findById(id).orElseThrow();

        // Mark signed
        c.setStatus("SIGNED");
        c.setSignedAt(Instant.now());
        contractRepository.save(c);

        // Finalize flows depending on owner type
        if ("saving".equalsIgnoreCase(c.getOwnerType())) {
            Saving s = savingRepository.findById(c.getOwnerId()).orElseThrow();
            // ensure owner is the current user
            // reuse userId from method scope
            if (!s.getUser().getId().equals(userId)) throw new RuntimeException("Not allowed");
            if (!"pending_contract".equalsIgnoreCase(s.getStatus())) throw new RuntimeException("Saving not pending");

            // Debit source account
            Account from = accountRepository.findById(s.getSourceAccount().getId()).orElseThrow();
            BigDecimal amt = s.getPrincipalAmount();
            from.setAvailableBalance(from.getAvailableBalance().subtract(amt));
            from.setCurrentBalance(from.getCurrentBalance().subtract(amt));
            accountRepository.save(from);

            // Create transaction record
            Transaction tx = Transaction.builder()
                .transactionCode("TXN-" + java.util.UUID.randomUUID().toString().substring(0,8).toUpperCase())
                .transactionType("saving_open")
                .amount(amt)
                .feeAmount(BigDecimal.ZERO)
                .status("completed")
                .fromAccount(from)
                .toAccount(s.getSettlementAccount() != null ? s.getSettlementAccount() : from)
                .initiatedByUser(null)
                .build();
            transactionRepository.save(tx);

            // Activate saving
            s.setStatus("active");
            Instant now = Instant.now();
            s.setOpenDate(now);
            if (s.getMaturityDate() == null) {
                ZonedDateTime base = now.atZone(ZoneId.systemDefault());
                ZonedDateTime maturity = "MONTH".equalsIgnoreCase(s.getTermUnit())
                    ? base.plusMonths(s.getTermValue())
                    : base.plusYears(s.getTermValue());
                s.setMaturityDate(maturity.toInstant());
            }
            savingRepository.save(s);
        }

        if ("loan_application".equalsIgnoreCase(c.getOwnerType())) {
            var app = loanApplicationRepository.findById(c.getOwnerId()).orElseThrow();
            if (!app.getUser().getId().equals(userId)) throw new RuntimeException("Not allowed");

            // Create Loan
            Loan loan = Loan.builder()
                .code("LN-" + java.util.UUID.randomUUID().toString().substring(0,8).toUpperCase())
                .loanApplication(app)
                .user(app.getUser())
                .approvedAmount(app.getRequestedAmount())
                .disbursedAmount(app.getRequestedAmount())
                .interestRateType("FIXED")
                .actualInterestRate(new BigDecimal("0.11"))
                .repaymentFrequency("monthly")
                .termMonths(app.getRequestedTermMonths())
                .outstandingPrincipal(app.getRequestedAmount())
                .outstandingInterest(BigDecimal.ZERO)
                .overduePrincipal(BigDecimal.ZERO)
                .overdueInterest(BigDecimal.ZERO)
                .status("active")
                .disbursementAccount(app.getDisbursementAccount())
                .repaymentAccount(app.getRepaymentAccount())
                .disbursedAt(Instant.now())
                .nextDueDate(java.util.Date.from(Instant.now()).toInstant())
                .build();
            loan = loanRepository.save(loan);

            // Generate simple monthly schedule
            BigDecimal principal = loan.getApprovedAmount();
            int months = loan.getTermMonths();
            BigDecimal monthlyPrincipal = principal.divide(BigDecimal.valueOf(months), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal monthlyRate = loan.getActualInterestRate().divide(BigDecimal.valueOf(12), 8, java.math.RoundingMode.HALF_UP);
            BigDecimal remaining = principal;
            LocalDate due = LocalDate.now().plusMonths(1);
            for (int i = 1; i <= months; i++) {
                BigDecimal interest = remaining.multiply(monthlyRate).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal principalDue = i == months ? remaining : monthlyPrincipal;
                BigDecimal totalDue = principalDue.add(interest);

                LoanRepaymentSchedule sch = LoanRepaymentSchedule.builder()
                    .loan(loan)
                    .installmentNo(i)
                    .dueDate(due)
                    .openingPrincipalBalance(remaining)
                    .principalDue(principalDue)
                    .interestRate(loan.getActualInterestRate())
                    .interestDue(interest)
                    .penaltyInterestDue(BigDecimal.ZERO)
                    .feeDue(BigDecimal.ZERO)
                    .totalDue(totalDue)
                    .principalPaid(BigDecimal.ZERO)
                    .interestPaid(BigDecimal.ZERO)
                    .penaltyInterestPaid(BigDecimal.ZERO)
                    .feePaid(BigDecimal.ZERO)
                    .status("unpaid")
                    .build();
                repaymentScheduleRepository.save(sch);

                remaining = remaining.subtract(principalDue);
                due = due.plusMonths(1);
            }

            // Disburse: create transaction to user's disbursement account if exists
            Account to = null;
            if (loan.getDisbursementAccount() != null) {
                Account to = loan.getDisbursementAccount();
                to.setAvailableBalance(to.getAvailableBalance().add(loan.getDisbursedAmount()));
                to.setCurrentBalance(to.getCurrentBalance().add(loan.getDisbursedAmount()));
                accountRepository.save(to);
            }
            Transaction tx = Transaction.builder()
                .transactionCode("TXN-" + java.util.UUID.randomUUID().toString().substring(0,8).toUpperCase())
                .transactionType("loan_disbursement")
                .amount(loan.getDisbursedAmount())
                .feeAmount(BigDecimal.ZERO)
                .status("completed")
                .fromAccount(null)
                .toAccount(to)
                .initiatedByUser(null)
                .build();
            transactionRepository.save(tx);
        }

        return c;
    }
}
