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
@Table(name = "contracts")
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_type", nullable = false, length = 64)
    private String ownerType; // e.g., "loan_application" or "saving"

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ContractTemplate template;

    @Column(name = "contract_number", length = 64)
    private String contractNumber;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "status", length = 32)
    private String status; // DRAFT, SENT, PENDING_SIGNATURE, SIGNED, CANCELLED

    @Column(name = "signed_at")
    private Instant signedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AdminUser createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
