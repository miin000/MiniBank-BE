package com.minibank.backend.admin.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.backend.admin.repository.AdminUserRepository;
import com.minibank.backend.contract.entity.Contract;
import com.minibank.backend.contract.entity.ContractTemplate;
import com.minibank.backend.contract.repository.ContractRepository;
import com.minibank.backend.contract.repository.ContractTemplateRepository;
import com.minibank.backend.loan.entity.LoanApplication;
import com.minibank.backend.loan.repository.LoanApplicationRepository;

@RestController
@RequestMapping("/api/admin/approvals")
public class AdminApprovalController {
    private final LoanApplicationRepository loanApplicationRepository;
    private final ContractTemplateRepository templateRepository;
    private final ContractRepository contractRepository;
    private final AdminUserRepository adminUserRepository;

    public AdminApprovalController(
        LoanApplicationRepository loanApplicationRepository,
        ContractTemplateRepository templateRepository,
        ContractRepository contractRepository,
        AdminUserRepository adminUserRepository
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.templateRepository = templateRepository;
        this.contractRepository = contractRepository;
        this.adminUserRepository = adminUserRepository;
    }

    @PostMapping("/loan-applications/{id}/approve")
    @ResponseStatus(HttpStatus.OK)
    public Contract approveLoanApplication(@PathVariable Long id, @RequestBody ApproveRequest req) {
        LoanApplication app = loanApplicationRepository.findById(id).orElseThrow();
        app.setStatus("approved");
        app.setReviewedAt(Instant.now());
        adminUserRepository.findByUsernameIgnoreCase("system").ifPresent(app::setReviewedBy);
        loanApplicationRepository.save(app);

        ContractTemplate template = templateRepository.findById(req.templateId).orElseThrow();
        Contract c = Contract.builder()
            .ownerType("loan_application")
            .ownerId(app.getId())
            .template(template)
            .contractNumber(req.contractNumber)
            .status("SENT")
            .build();

        return contractRepository.save(c);
    }

    public static class ApproveRequest {
        public Long templateId;
        public String contractNumber;
    }
}
