package com.minibank.backend.loan.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.backend.admin.service.AdminServiceRequestService;
import com.minibank.backend.common.security.CurrentJwt;
import com.minibank.backend.loan.entity.Loan;
import com.minibank.backend.loan.entity.LoanRepaymentSchedule;
import com.minibank.backend.loan.repository.LoanRepaymentScheduleRepository;
import com.minibank.backend.loan.repository.LoanRepository;

@RestController
@RequestMapping("/api/admin/loans")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLoanController {
    private final LoanRepository loanRepository;
    private final LoanRepaymentScheduleRepository repaymentScheduleRepository;
    private final AdminServiceRequestService adminServiceRequestService;

    public AdminLoanController(
        LoanRepository loanRepository,
        LoanRepaymentScheduleRepository repaymentScheduleRepository,
        AdminServiceRequestService adminServiceRequestService
    ) {
        this.loanRepository = loanRepository;
        this.repaymentScheduleRepository = repaymentScheduleRepository;
        this.adminServiceRequestService = adminServiceRequestService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminLoanItem> list(@RequestParam(required = false) String status) {
        String statusFilter = normalize(status);
        return loanRepository.findAll().stream()
            .filter(loan -> statusFilter == null || statusFilter.equalsIgnoreCase(loan.getStatus()))
            .sorted(Comparator.comparing(Loan::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .map(this::toLoanItem)
            .toList();
    }

    @GetMapping("/repayment-schedules")
    @Transactional(readOnly = true)
    public List<AdminLoanRepaymentItem> repaymentSchedules(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long loanId
    ) {
        String statusFilter = normalize(status);
        return repaymentScheduleRepository.findAll().stream()
            .filter(item -> loanId == null || item.getLoan().getId().equals(loanId))
            .filter(item -> statusFilter == null || statusFilter.equalsIgnoreCase(item.getStatus()))
            .sorted(Comparator.comparing(LoanRepaymentSchedule::getDueDate))
            .map(this::toRepaymentItem)
            .toList();
    }

    public record EarlySettlementRequest(Long repaymentAccountId, String note) {}

    @PostMapping("/{id}/early-settle")
    public void earlySettle(
        @PathVariable Long id,
        @RequestBody(required = false) EarlySettlementRequest body
    ) {
        long adminUserId = CurrentJwt.requireUserId();
        adminServiceRequestService.settleLoanEarlyManually(
            id,
            body != null ? body.repaymentAccountId() : null,
            adminUserId,
            body != null ? body.note() : null
        );
    }

    private AdminLoanItem toLoanItem(Loan loan) {
        return new AdminLoanItem(
            loan.getId(),
            loan.getCode(),
            loan.getUser() != null ? loan.getUser().getFullName() : null,
            loan.getUser() != null ? loan.getUser().getPhone() : null,
            loan.getDisbursementAccount() != null ? loan.getDisbursementAccount().getAccountNumber() : null,
            loan.getRepaymentAccount() != null ? loan.getRepaymentAccount().getId() : null,
            loan.getRepaymentAccount() != null ? loan.getRepaymentAccount().getAccountNumber() : null,
            loan.getApprovedAmount(),
            loan.getDisbursedAmount(),
            loan.getOutstandingPrincipal(),
            loan.getOutstandingInterest(),
            loan.getActualInterestRate(),
            loan.getTermMonths(),
            loan.getNextDueDate(),
            loan.getStatus(),
            loan.getCreatedAt(),
            loan.getClosedAt()
        );
    }

    private AdminLoanRepaymentItem toRepaymentItem(LoanRepaymentSchedule item) {
        Loan loan = item.getLoan();
        BigDecimal paidAmount = nullToZero(item.getPrincipalPaid())
            .add(nullToZero(item.getInterestPaid()))
            .add(nullToZero(item.getPenaltyInterestPaid()))
            .add(nullToZero(item.getFeePaid()));
        return new AdminLoanRepaymentItem(
            item.getId(),
            loan != null ? loan.getId() : null,
            loan != null ? loan.getCode() : null,
            loan != null && loan.getUser() != null ? loan.getUser().getFullName() : null,
            item.getInstallmentNo(),
            item.getDueDate(),
            item.getPrincipalDue(),
            item.getInterestDue(),
            item.getPenaltyInterestDue(),
            item.getFeeDue(),
            item.getTotalDue(),
            paidAmount,
            item.getStatus(),
            item.getPaidAt()
        );
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    public record AdminLoanItem(
        Long id,
        String code,
        String customerName,
        String customerPhone,
        String disbursementAccountNumber,
        Long repaymentAccountId,
        String repaymentAccountNumber,
        BigDecimal approvedAmount,
        BigDecimal disbursedAmount,
        BigDecimal outstandingPrincipal,
        BigDecimal outstandingInterest,
        BigDecimal actualInterestRate,
        int termMonths,
        Instant nextDueDate,
        String status,
        Instant createdAt,
        Instant closedAt
    ) {}

    public record AdminLoanRepaymentItem(
        Long id,
        Long loanId,
        String loanCode,
        String customerName,
        int installmentNo,
        LocalDate dueDate,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        BigDecimal penaltyAmount,
        BigDecimal feeAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        String status,
        Instant paidAt
    ) {}
}
