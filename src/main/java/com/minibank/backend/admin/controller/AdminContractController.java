package com.minibank.backend.admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.backend.contract.entity.Contract;
import com.minibank.backend.contract.entity.ContractTemplate;
import com.minibank.backend.contract.repository.ContractRepository;
import com.minibank.backend.contract.repository.ContractTemplateRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/contracts")
public class AdminContractController {
    private final ContractTemplateRepository templateRepository;
    private final ContractRepository contractRepository;

    public AdminContractController(ContractTemplateRepository templateRepository, ContractRepository contractRepository) {
        this.templateRepository = templateRepository;
        this.contractRepository = contractRepository;
    }

    @GetMapping("/templates")
    public List<ContractTemplate> listTemplates() {
        return templateRepository.findAll();
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractTemplate createTemplate(@Valid @RequestBody ContractTemplate t) {
        return templateRepository.save(t);
    }

    @GetMapping
    public List<Contract> listContracts() {
        return contractRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Contract createContract(@Valid @RequestBody Contract c) {
        c.setStatus(c.getStatus() == null ? "DRAFT" : c.getStatus());
        return contractRepository.save(c);
    }

    @GetMapping("/{id}")
    public Contract get(@PathVariable Long id) {
        return contractRepository.findById(id).orElseThrow();
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public Contract generateContract(@RequestBody GenerateRequest req) {
        ContractTemplate t = templateRepository.findById(req.templateId).orElseThrow();
        Contract c = Contract.builder()
            .ownerType(req.ownerType)
            .ownerId(req.ownerId)
            .template(t)
            .contractNumber(req.contractNumber)
            .status("SENT")
            .build();
        return contractRepository.save(c);
    }

    @PostMapping("/{id}/signed")
    public Contract markSigned(@PathVariable Long id) {
        Contract c = contractRepository.findById(id).orElseThrow();
        c.setStatus("SIGNED");
        c.setSignedAt(java.time.Instant.now());
        return contractRepository.save(c);
    }

    public static class GenerateRequest {
        public String ownerType;
        public Long ownerId;
        public Long templateId;
        public String contractNumber;
    }
}
