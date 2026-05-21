package com.minibank.backend.contract.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.minibank.backend.contract.entity.ContractTemplate;

// ── ContractTemplateRepository ──────────────────────────────────────────────
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {

    List<ContractTemplate> findAllByOrderByCreatedAtDesc();

    List<ContractTemplate> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Tìm mẫu áp dụng cho một dịch vụ cụ thể (loan / saving / general).
     * services lưu dạng: "loan", "saving", "loan,saving", "general"
     */
    @Query("select t from ContractTemplate t where t.status = 'active' " +
           "and (t.services = :svc or t.services like concat('%', :svc, '%') " +
           "     or t.services = 'general')")
    List<ContractTemplate> findActiveByService(@Param("svc") String service);

    boolean existsByCode(String code);

    Optional<ContractTemplate> findByCode(String code);
}
