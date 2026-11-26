package com.helpdeskpro.backend.repositories;
import com.helpdeskpro.backend.domain.entities.AssetAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AssetAuditLogRepository extends JpaRepository<AssetAuditLog,Long> {
 List<AssetAuditLog> findByAssetIdOrderByOccurredAtAscIdAsc(Long assetId);
}
