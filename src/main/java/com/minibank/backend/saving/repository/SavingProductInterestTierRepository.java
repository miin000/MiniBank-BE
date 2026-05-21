package com.minibank.backend.saving.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.minibank.backend.saving.entity.SavingProductInterestTier;

@Repository
public interface SavingProductInterestTierRepository extends JpaRepository<SavingProductInterestTier, Long> {
    List<SavingProductInterestTier> findBySavingProductIdOrderByEffectiveFromDescMinAmountAsc(Long savingProductId);
}
