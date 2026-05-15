package com.minibank.backend.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.minibank.backend.contract.entity.ContractTemplate;

@Repository
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {
}
