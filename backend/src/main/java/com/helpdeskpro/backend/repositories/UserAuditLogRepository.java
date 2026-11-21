package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.entities.UserAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, Long> {
    List<UserAuditLog> findByUserIdOrderByOccurredAtAsc(Long userId);
}
