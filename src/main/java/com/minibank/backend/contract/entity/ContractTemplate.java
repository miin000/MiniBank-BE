package com.minibank.backend.contract.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.minibank.backend.admin.entity.AdminUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "contract_templates")
public class ContractTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    // Could be HTML template or reference to a docx/pdf template stored in documents/storage
    @Column(name = "template_body", columnDefinition = "text")
    private String templateBody;

    @Column(name = "template_file_url")
    private String templateFileUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AdminUser createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
