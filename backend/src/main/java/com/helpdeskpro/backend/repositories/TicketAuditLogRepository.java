package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.entities.TicketAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketAuditLogRepository extends JpaRepository<TicketAuditLog, Long> {
    @org.springframework.data.jpa.repository.Query("select a from TicketAuditLog a where a.ticket.id=:ticketId order by a.changedAt,a.id")
    List<TicketAuditLog> findByTicketIdOrderByChangedAtAsc(@org.springframework.data.repository.query.Param("ticketId") Long ticketId);
}
