package com.minibank.backend.contract.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.minibank.backend.contract.entity.Contract;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    List<Contract> findByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
}
