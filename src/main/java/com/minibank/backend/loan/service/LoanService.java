package com.minibank.backend.loan.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.minibank.backend.account.entity.Account;
import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.loan.dto.CreateLoanRequest;
import com.minibank.backend.loan.dto.LoanApplicationResponse;
import com.minibank.backend.loan.dto.LoanResponse;
import com.minibank.backend.loan.entity.Loan;
import com.minibank.backend.loan.entity.LoanApplication;
import com.minibank.backend.loan.entity.LoanProduct;
import com.minibank.backend.loan.repository.LoanApplicationRepository;
import com.minibank.backend.loan.repository.LoanProductRepository;
import com.minibank.backend.loan.repository.LoanRepository;
import com.minibank.backend.user.entity.User;
import com.minibank.backend.user.repository.UserRepository;

@Service
public class LoanService {

    private final LoanRepository loanRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductRepository loanProductRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public LoanService(
        LoanRepository loanRepository,
        LoanApplicationRepository loanApplicationRepository,
        LoanProductRepository loanProductRepository,
        AccountRepository accountRepository,
        UserRepository userRepository
    ) {
        this.loanRepository = loanRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.loanProductRepository = loanProductRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    // ── Mobile: xem danh sách khoản vay đang active ──────────────────────────

    @Transactional(readOnly = true)
    public List<LoanResponse> getLoans(long userId) {
        return loanRepository.findByUserId(userId)
            .stream()
            .map(this::toLoanResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public LoanResponse getLoan(long userId, long loanId) {
        Loan loan = loanRepository.findByIdAndUserId(loanId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found"));
        return toLoanResponse(loan);
    }

    // ── Mobile: tạo đơn xin vay (LoanApplication) ────────────────────────────
    // Không tạo Loan ngay — flow đúng: Application → Admin duyệt → Contract ký → Loan

    @Transactional
    public LoanApplicationResponse applyForLoan(long userId, CreateLoanRequest req) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        LoanProduct product = loanProductRepository.findById(req.loanProductId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loan product not found"));

        if (!"active".equalsIgnoreCase(product.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loan product is not available");
        }

        // Validate amount & term trong phạm vi sản phẩm
        if (req.amount().compareTo(product.getMinAmount()) < 0
            || req.amount().compareTo(product.getMaxAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Amount must be between " + product.getMinAmount() + " and " + product.getMaxAmount());
        }
        if (req.termMonths() < product.getMinTermMonths()
            || req.termMonths() > product.getMaxTermMonths()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Term must be between " + product.getMinTermMonths()
                + " and " + product.getMaxTermMonths() + " months");
        }

        // Verify tài khoản giải ngân/hoàn trả thuộc về user
        Account disbursement = accountRepository.findById(req.disbursementAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Disbursement account not found"));
        Account repayment = accountRepository.findById(req.repaymentAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repayment account not found"));

        if (!disbursement.getUser().getId().equals(userId) || !repayment.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to user");
        }

        // Gắn tag ưu tiên nếu user VIP hoặc tín dụng hạng A
        String priorityTag = resolvePriorityTag(user);

        LoanApplication application = LoanApplication.builder()
            .user(user)
            .loanProduct(product)
            .requestedAmount(req.amount())
            .requestedTermMonths(req.termMonths())
            .monthlyIncome(req.monthlyIncome())
            .purpose(req.purpose())
            .collateralDescription(req.collateralDescription())
            .incomeProofUrl(req.incomeProofUrl())
            .collateralProofUrl(req.collateralProofUrl())
            .priorityTag(priorityTag)
            .status("pending")
            .build();

        application = loanApplicationRepository.save(application);
        return toLoanApplicationResponse(application);
    }

    @Transactional(readOnly = true)
    public List<LoanApplicationResponse> getApplications(long userId) {
        return loanApplicationRepository.findByUserId(userId)
            .stream()
            .map(this::toLoanApplicationResponse)
            .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolvePriorityTag(User user) {
        // VIP: số dư >= 2 tỷ hoặc rank gold/silver
        // Credit A: creditScoreLevel == "A"
        String rank = user.getCustomerRank();
        String creditLevel = user.getCreditScoreLevel();
        if ("vip".equalsIgnoreCase(rank) || "vang".equalsIgnoreCase(rank)
            || "A".equalsIgnoreCase(creditLevel)) {
            return "priority";
        }
        return null;
    }

    private LoanResponse toLoanResponse(Loan loan) {
        return new LoanResponse(
            loan.getId(),
            loan.getCode(),
            loan.getApprovedAmount(),
            loan.getDisbursedAmount(),
            loan.getOutstandingPrincipal(),
            loan.getOutstandingInterest(),
            loan.getStatus(),
            loan.getRepaymentFrequency(),
            loan.getTermMonths(),
            loan.getNextDueDate(),
            loan.getClosedAt(),
            loan.getCreatedAt()
        );
    }

    private LoanApplicationResponse toLoanApplicationResponse(LoanApplication app) {
        return new LoanApplicationResponse(
            app.getId(),
            app.getLoanProduct() != null ? app.getLoanProduct().getName() : null,
            app.getRequestedAmount(),
            app.getRequestedTermMonths(),
            app.getPurpose(),
            app.getStatus(),
            app.getPriorityTag(),
            app.getSubmittedAt()
        );
    }
}