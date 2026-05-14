package com.minibank.backend.saving.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.backend.account.entity.Account;
import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.admin.entity.AdminUser;
import com.minibank.backend.admin.repository.AdminUserRepository;
import com.minibank.backend.saving.dto.CreateSavingRequest;
import com.minibank.backend.saving.dto.SavingProductResponse;
import com.minibank.backend.saving.dto.SavingResponse;
import com.minibank.backend.saving.entity.Saving;
import com.minibank.backend.saving.entity.SavingProduct;
import com.minibank.backend.saving.repository.SavingProductRepository;
import com.minibank.backend.saving.repository.SavingRepository;
import com.minibank.backend.user.entity.User;
import com.minibank.backend.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Core business logic for savings (sổ tiết kiệm).
 *
 * Create flow:
 *  1. Validate product is active
 *  2. Validate principal within product min/max
 *  3. Validate source account belongs to user and has sufficient balance
 *  4. Debit source account (available_balance)
 *  5. Persist Saving entity
 *  6. Return SavingResponse DTO
 *
 * Status lifecycle: PENDING_APPROVAL → ACTIVE (after admin approval)
 * This service handles PENDING_APPROVAL creation only.
 */
@Service
@RequiredArgsConstructor
public class SavingService {

    private final SavingRepository savingRepository;
    private final SavingProductRepository savingProductRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;

    // ─── Public API ─────────────────────────────────────────────────────────

    public List<SavingResponse> getSavings(long userId) {
        return savingRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(SavingResponse::from)
                .toList();
    }

    public SavingResponse getSaving(long userId, long savingId) {
        Saving saving = savingRepository.findById(savingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy sổ tiết kiệm"));

        if (!saving.getUser().getId().equals(userId)) {
            throw new SecurityException("Không có quyền truy cập sổ này");
        }

        return SavingResponse.from(saving);
    }

    @Transactional
    public SavingResponse createSaving(long userId, CreateSavingRequest request) {
        // 1. Load entities
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Người dùng không tồn tại"));

        SavingProduct product = savingProductRepository.findById(request.savingProductId())
                .orElseThrow(() -> new EntityNotFoundException("Sản phẩm tiết kiệm không tồn tại"));

        Account sourceAccount = accountRepository.findById(request.sourceAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản nguồn không tồn tại"));
        Account settlementAccount = resolveSettlementAccount(request.settlementAccountId(), sourceAccount);

        // 2. Business validations
        validateProductActive(product);
        validateAccountOwner(sourceAccount, userId);
        validateAccountActive(sourceAccount);
        validateAccountOwner(settlementAccount, userId);
        validateAccountActive(settlementAccount);
        validatePrincipalRange(request.principalAmount(), product);
        validateSufficientBalance(sourceAccount, request.principalAmount());

        // 3. Debit source account
        sourceAccount.setAvailableBalance(
                sourceAccount.getAvailableBalance().subtract(request.principalAmount()));
        accountRepository.save(sourceAccount);

        // 4. Build and persist saving
        Saving saving = buildSaving(user, product, sourceAccount, settlementAccount, request.principalAmount(), request.autoRenew());
        saving = savingRepository.save(saving);

        return SavingResponse.from(saving);
    }

    public List<SavingProductResponse> getActiveSavingProducts() {
        return savingProductRepository.findByStatusOrderByBaseInterestRateDesc("active")
                .stream()
                .map(SavingProductResponse::from)
                .toList();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void validateProductActive(SavingProduct product) {
        if (!"active".equals(product.getStatus())) {
            throw new IllegalStateException("Sản phẩm tiết kiệm không còn khả dụng");
        }
    }

    private void validateAccountOwner(Account account, long userId) {
        if (!account.getUser().getId().equals(userId)) {
            throw new SecurityException("Tài khoản nguồn không thuộc về người dùng");
        }
    }

    private void validateAccountActive(Account account) {
        if (!"active".equals(account.getStatus())) {
            throw new IllegalStateException("Tài khoản nguồn không ở trạng thái hoạt động");
        }
    }

    private void validatePrincipalRange(BigDecimal principal, SavingProduct product) {
        if (principal.compareTo(product.getMinOpenAmount()) < 0) {
            throw new IllegalArgumentException(
                    "Số tiền tối thiểu là " + product.getMinOpenAmount() + " VND");
        }
        if (product.getMaxOpenAmount() != null
                && principal.compareTo(product.getMaxOpenAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Số tiền tối đa là " + product.getMaxOpenAmount() + " VND");
        }
    }

    private void validateSufficientBalance(Account account, BigDecimal amount) {
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Số dư khả dụng không đủ để mở sổ");
        }
    }

    /**
     * Builds the Saving entity by copying relevant fields from the product.
     * Status is PENDING_APPROVAL until an admin activates it.
     */
    private Saving buildSaving(User user, SavingProduct product,
                                Account sourceAccount, Account settlementAccount, BigDecimal principal, Boolean autoRenew) {
        Instant now = Instant.now();

        // Maturity date = now + term
        ChronoUnit chronoUnit = "MONTH".equalsIgnoreCase(product.getTermUnit())
                ? ChronoUnit.MONTHS
                : ChronoUnit.YEARS;
        Instant maturityDate = now.plus(product.getTermValue(), chronoUnit);

        return Saving.builder()
                // Identity
                .code(generateCode())
                .user(user)
                .savingProduct(product)
                // Accounts
                .sourceAccount(sourceAccount)
                .settlementAccount(settlementAccount)
                // Principal & rates
                .principalAmount(principal)
                .actualInterestRate(product.getBaseInterestRate())
                .interestRateType(product.getInterestRateType())
                .penaltyInterestRate(product.getPenaltyInterestRate())
                .bonusInterestRate(product.getBonusInterestRate())
                // Interest config
                .interestAccrualFrequency(product.getInterestAccrualFrequency())
                .interestPostingFrequency(product.getInterestPostingFrequency())
                .capitalized(product.isCapitalized())
                // Accumulated interest (start at 0)
                .accruedInterestAmount(BigDecimal.ZERO)
                .postedInterestAmount(BigDecimal.ZERO)
                // Fees (copied from product)
                .depositFeeRate(product.getDepositFeeRate())
                .depositFeeFlat(product.getDepositFeeFlat())
                .withdrawalFeeRate(product.getWithdrawalFeeRate())
                .withdrawalFeeFlat(product.getWithdrawalFeeFlat())
                .closeFeeRate(product.getCloseFeeRate())
                .closeFeeFlat(product.getCloseFeeFlat())
                .managementFeeRate(product.getManagementFeeRate())
                .managementFeeFlat(product.getManagementFeeFlat())
                .managementFeeFrequency(product.getManagementFeeFrequency())
                // Term
                .termUnit(product.getTermUnit())
                .termValue(product.getTermValue())
                // Status & dates
                .status("pending_approval")
                .openDate(now)
                .maturityDate(maturityDate)
                .autoRenew(Boolean.TRUE.equals(autoRenew))
                .locked(false)
                // Admin: system auto-creator placeholder; real admin set on approval
                .createdBy(getSystemAdmin())
                .build();
    }

        private Account resolveSettlementAccount(Long settlementAccountId, Account fallback) {
            if (settlementAccountId == null) {
                return fallback;
            }
            return accountRepository.findById(settlementAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản nhận tất toán không tồn tại"));
        }

    /** Returns a system admin placeholder used for auto-created savings. */
    private AdminUser getSystemAdmin() {
        return adminUserRepository.findByUsernameIgnoreCase("system")
                .orElseThrow(() -> new EntityNotFoundException(
                        "System admin not configured; cannot create saving"));
    }

    private String generateCode() {
        return "SAV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}