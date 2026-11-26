package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.domain.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    @org.springframework.data.jpa.repository.Query("select count(a)>0 from Asset a where upper(trim(a.code))=upper(trim(:code))")
    boolean existsByCode(@org.springframework.data.repository.query.Param("code") String code);
    @org.springframework.data.jpa.repository.Query("select count(a)>0 from Asset a where upper(trim(a.code))=upper(trim(:code)) and a.id<>:id")
    boolean existsByCodeAndIdNot(@org.springframework.data.repository.query.Param("code") String code, @org.springframework.data.repository.query.Param("id") Long id);
    Optional<Asset> findByCode(String code);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Asset a where a.id=:id")
    Optional<Asset> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    long countByStatus(AssetStatus status);
    List<Asset> findByStatus(AssetStatus status);
}
