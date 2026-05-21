package com.minibank.backend.contract.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.backend.admin.entity.AdminUser;
import com.minibank.backend.contract.dto.ContractDetail;
import com.minibank.backend.contract.dto.ContractGenerateRequest;
import com.minibank.backend.contract.entity.ContractTemplate;
import com.minibank.backend.contract.repository.ContractTemplateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tự động sinh hợp đồng khi:
 *  - Admin phê duyệt đơn vay  → ownerType = LOAN_APPLICATION
 *  - Admin phê duyệt sổ tiết kiệm → ownerType = SAVING
 *
 * Gọi từ LoanService / SavingService sau khi persist entity.
 * Dùng REQUIRES_NEW để lỗi sinh HĐ không rollback giao dịch chính.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractAutoTriggerService {

    private final ContractTemplateRepository templateRepo;
    private final ContractService contractService;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Gọi sau khi duyệt đơn vay (loan_application).
     * @param loanApplicationId ID của loan_application vừa được duyệt
     * @param approvedBy        Admin đã phê duyệt
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void triggerForLoan(Long loanApplicationId, AdminUser approvedBy) {
        generate("loan", "LOAN_APPLICATION", loanApplicationId, approvedBy);
    }

    /**
     * Gọi sau khi duyệt sổ tiết kiệm.
     * @param savingId   ID của saving vừa được kích hoạt
     * @param approvedBy Admin đã phê duyệt
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void triggerForSaving(Long savingId, AdminUser approvedBy) {
        generate("saving", "SAVING", savingId, approvedBy);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void generate(String service, String ownerType, Long ownerId, AdminUser admin) {
        try {
            // Tìm mẫu active phù hợp với dịch vụ
            List<ContractTemplate> templates = templateRepo.findActiveByService(service);
            if (templates.isEmpty()) {
                log.warn("[ContractAutoTrigger] Không tìm thấy mẫu active cho service='{}' — bỏ qua", service);
                return;
            }
            // Lấy mẫu ưu tiên nhất (đầu danh sách, mới nhất)
            ContractTemplate tpl = templates.get(0);

            ContractGenerateRequest req = new ContractGenerateRequest(
                    tpl.getId(), ownerType, ownerId, null, null // contractNumber tự sinh
            );
            ContractDetail result = contractService.generate(req, admin.getId());
            log.info("[ContractAutoTrigger] Đã sinh hợp đồng #{} ({}) cho {}#{}",
                    result.id(), result.contractNumber(), ownerType, ownerId);

        } catch (Exception ex) {
            // Không throw để không ảnh hưởng luồng chính
            log.error("[ContractAutoTrigger] Lỗi khi sinh hợp đồng cho {}#{}: {}",
                    ownerType, ownerId, ex.getMessage(), ex);
        }
    }
}