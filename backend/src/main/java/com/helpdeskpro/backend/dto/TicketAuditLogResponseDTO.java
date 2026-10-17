package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.entities.TicketAuditLog;

import java.time.Instant;

public class TicketAuditLogResponseDTO {

    private Long id;
    private Long ticketId;
    private Long changedById;
    private String changedByName;
    private String changedByEmail;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private Instant changedAt;

    public TicketAuditLogResponseDTO() {
    }

    public TicketAuditLogResponseDTO(Long id, Long ticketId, Long changedById, String changedByName,
                                     String changedByEmail, String fieldName, String oldValue,
                                     String newValue, Instant changedAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.changedById = changedById;
        this.changedByName = changedByName;
        this.changedByEmail = changedByEmail;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedAt = changedAt;
    }

    public static TicketAuditLogResponseDTO fromEntity(TicketAuditLog auditLog) {
        return new TicketAuditLogResponseDTO(
                auditLog.getId(),
                auditLog.getTicket().getId(),
                auditLog.getChangedBy().getId(),
                auditLog.getChangedBy().getName(),
                auditLog.getChangedBy().getEmail(),
                auditLog.getFieldName(),
                auditLog.getOldValue(),
                auditLog.getNewValue(),
                auditLog.getChangedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Long getChangedById() {
        return changedById;
    }

    public String getChangedByName() {
        return changedByName;
    }

    public String getChangedByEmail() {
        return changedByEmail;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
