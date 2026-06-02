package com.minibank.backend.contract.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.minibank.backend.contract.dto.ContractAcceptRequest;
import com.minibank.backend.contract.dto.ContractAcceptResult;
import com.minibank.backend.contract.entity.Contract;
import com.minibank.backend.contract.entity.ContractTemplate;
import com.minibank.backend.contract.repository.ContractRepository;
import com.minibank.backend.saving.entity.Saving;
import com.minibank.backend.saving.repository.SavingRepository;
import com.minibank.backend.loan.entity.LoanApplication;
import com.minibank.backend.loan.repository.LoanApplicationRepository;
import com.minibank.backend.common.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Xử lý nghiệp vụ ký hợp đồng phía mobile.
 *
 * Các loại hợp đồng:
 *  - SAVING_AGREEMENT : Thỏa thuận tiết kiệm — lưu vào saving.agreementAcceptedAt
 *  - LOAN_CREDIT      : Hợp đồng vay tín dụng — tạo Contract entity
 *  - LOAN_MORTGAGE    : Hợp đồng vay thế chấp — tạo Contract entity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileContractService {

    private final ContractTemplateService templateService;
    private final ContractRepository contractRepo;
    private final SavingRepository savingRepo;
    private final LoanApplicationRepository loanApplicationRepo;
    private final ContractDataResolver dataResolver;
    private final DocxParserService docxParser;
    private final StorageService storageService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_INSTANT;

    // ── Accept (ký) ──────────────────────────────────────────────────────────

    @Transactional
    public ContractAcceptResult accept(Long userId, ContractAcceptRequest req) {
        // 1. Kiểm tra template đang active
        ContractTemplate tpl = templateService.findActiveTemplateByCodeOrAlias(req.templateCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy template active: " + req.templateCode()));

        // 2. Điều phối theo loại
        return switch (req.templateCode().toUpperCase()) {
            case "SAVING_AGREEMENT" -> acceptSaving(userId, req, tpl);
            case "LOAN_CREDIT", "LOAN_MORTGAGE", "UNSECURED_LOAN_CONTRACT", "SECURED_LOAN_CONTRACT" -> acceptLoan(userId, req, tpl);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "templateCode không hợp lệ: " + req.templateCode());
        };
    }

    // ── Decline (từ chối) ────────────────────────────────────────────────────

    @Transactional
    public void decline(Long userId, ContractAcceptRequest req) {
        // Ghi log từ chối — có thể mở rộng lưu vào bảng contract_decline_logs
        log.info("User {} declined contract template={} referenceType={} referenceId={}",
                userId, req.templateCode(), req.referenceType(), req.referenceId());
    }

    // ── Private: Saving ───────────────────────────────────────────────────────

    /**
     * Ký thỏa thuận tiết kiệm:
     * Đánh dấu saving.agreementAcceptedAt và agreementVersion.
     * Không tạo Contract entity (thỏa thuận tiết kiệm dùng bản cố định, không sinh PDF riêng).
     */
    private ContractAcceptResult acceptSaving(Long userId, ContractAcceptRequest req, ContractTemplate tpl) {
        Saving saving = savingRepo.findById(req.referenceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy sổ tiết kiệm #" + req.referenceId()));

        // Xác nhận sổ thuộc về user đang đăng nhập
        if (!saving.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền ký thỏa thuận này");
        }

        // Idempotent: nếu đã ký rồi thì trả về kết quả cũ
        if (saving.getAgreementAcceptedAt() != null) {
            return new ContractAcceptResult(
                    saving.getCode(),
                    "signed",
                    null,
                    FMT.format(saving.getAgreementAcceptedAt())
            );
        }

        Instant now = Instant.now();
        saving.setAgreementAcceptedAt(now);
        saving.setAgreementVersion(tpl.getCode() + "_" + tpl.getId());
        savingRepo.save(saving);

        return new ContractAcceptResult(
                saving.getCode(),
                "signed",
                null,
                FMT.format(now)
        );
    }

    // ── Private: Loan ─────────────────────────────────────────────────────────

    /**
     * Ký hợp đồng vay (tín dụng hoặc thế chấp):
     * 1. Kiểm tra quyền sở hữu loan application.
     * 2. Resolve dữ liệu và điền vào templateBody.
     * 3. Tạo Contract entity với status = SIGNED.
     * 4. Upload rendered body lên storage.
     */
    private ContractAcceptResult acceptLoan(Long userId, ContractAcceptRequest req, ContractTemplate tpl) {
        LoanApplication loan = loanApplicationRepo.findById(req.referenceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy hồ sơ vay #" + req.referenceId()));

        if (!loan.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền ký hợp đồng này");
        }

        // Idempotent: nếu đã có hợp đồng SIGNED cho loan này thì trả về
        contractRepo.findFirstByOwnerTypeAndOwnerIdAndStatus("loan_application", req.referenceId(), "SIGNED")
                .ifPresent(existing -> {
                    throw new AlreadySignedException(existing.getContractNumber(),
                            existing.getFileUrl(), FMT.format(existing.getSignedAt()));
                });

        // 1. Resolve + render
        Map<String, String> data = dataResolver.resolveForLoanApplication(req.referenceId());
        String rendered = docxParser.fillTemplate(tpl.getTemplateBody(), data);

        // 2. Số hợp đồng
        String contractNumber = generateContractNumber(req.templateCode());

        // 3. Tạo entity
        Instant now = Instant.now();
        Contract contract = Contract.builder()
                .contractNumber(contractNumber)
                .template(tpl)
                .ownerType("loan_application")
                .ownerId(req.referenceId())
                .renderedBody(rendered)
                .status("SIGNED")
                .signedAt(now)
                .build();
        contractRepo.save(contract);

        // 4. Upload file
        String fileUrl = null;
        try {
            String html = wrapHtml(rendered, contractNumber, loan.getUser().getFullName());
            fileUrl = storageService.uploadText(html, "contract_" + contract.getId() + ".html", "contracts");
            contract.setFileUrl(fileUrl);
            contractRepo.save(contract);
        } catch (Exception ex) {
            log.warn("Upload contract file failed for contract #{}: {}", contract.getId(), ex.getMessage());
        }

        return new ContractAcceptResult(contractNumber, "signed", fileUrl, FMT.format(now));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateContractNumber(String templateCode) {
        String prefix = switch (templateCode.toUpperCase()) {
            case "LOAN_CREDIT", "UNSECURED_LOAN_CONTRACT" -> "HD-TD";
            case "LOAN_MORTGAGE", "SECURED_LOAN_CONTRACT" -> "HD-TC";
            default              -> "HD";
        };
        String year = String.valueOf(java.time.LocalDate.now().getYear());
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return prefix + "-" + year + "-" + suffix;
    }

    private String wrapHtml(String body, String contractNumber, String customerName) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8">
                  <title>%s</title>
                  <style>
                    body { font-family: 'Times New Roman', serif; max-width: 800px; margin: 40px auto;
                           padding: 40px; font-size: 14px; line-height: 1.8; color: #1a1a1a; }
                    .header { text-align: center; margin-bottom: 32px; }
                    .header h2 { font-size: 18px; font-weight: bold; }
                    .signed-by { margin-top: 48px; text-align: right; font-style: italic; color: #555; }
                    pre { white-space: pre-wrap; font-family: inherit; }
                  </style>
                </head>
                <body>
                  <div class="header"><h2>%s</h2></div>
                  <pre>%s</pre>
                  <div class="signed-by">Đã ký điện tử bởi: %s</div>
                </body>
                </html>
                """.formatted(contractNumber, contractNumber, body, customerName);
    }

    // Dùng để xử lý trường hợp đã ký rồi (idempotent)
    static class AlreadySignedException extends RuntimeException {
        final String contractNumber;
        final String fileUrl;
        final String signedAt;

        AlreadySignedException(String contractNumber, String fileUrl, String signedAt) {
            super("Already signed");
            this.contractNumber = contractNumber;
            this.fileUrl = fileUrl;
            this.signedAt = signedAt;
        }
    }
}
