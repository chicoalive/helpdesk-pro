package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.domain.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    Optional<Asset> findByCode(String code);
    long countByStatus(AssetStatus status);
    List<Asset> findByStatus(AssetStatus status);
}
