package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.entities.TicketAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketAuditLogRepository extends JpaRepository<TicketAuditLog, Long> {
    List<TicketAuditLog> findByTicketIdOrderByChangedAtAsc(Long ticketId);
}
