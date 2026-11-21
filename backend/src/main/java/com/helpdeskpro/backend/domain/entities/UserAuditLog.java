package com.helpdeskpro.backend.domain.entities;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_audit_logs")
public class UserAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", nullable = false, updatable = false)
    private UserRole assignedRole;

    @Column(nullable = false, updatable = false, length = 64)
    private String operation;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @ElementCollection
    @CollectionTable(name = "user_audit_changes", joinColumns = @JoinColumn(name = "audit_id"))
    @MapKeyColumn(name = "field_name")
    private java.util.Map<String, UserAuditChange> changes = new java.util.LinkedHashMap<>();

    public java.util.Map<String, UserAuditChange> getChanges() { return java.util.Map.copyOf(changes); }

    public static UserAuditLog updated(User actor, User user, java.util.Map<String, UserAuditChange> changes) {
        UserAuditLog audit = new UserAuditLog(actor, user);
        audit.operation = "USER_UPDATED";
        audit.changes.putAll(changes);
        return audit;
    }

    public static UserAuditLog stateChanged(User actor, User user, boolean previous) {
        UserAuditLog audit = new UserAuditLog(actor, user);
        audit.operation = user.isEnabled() ? "USER_REACTIVATED" : "USER_DEACTIVATED";
        audit.changes.put("active", new UserAuditChange(Boolean.toString(previous), Boolean.toString(user.isEnabled())));
        return audit;
    }

    @Column(name="recovery_request_id", updatable=false)
    private Long recoveryRequestId;
    public Long getRecoveryRequestId(){return recoveryRequestId;}
    public static UserAuditLog recovery(User actor, User user, String operation, Long requestId){
        UserAuditLog event=new UserAuditLog(actor,user);
        event.operation=operation;event.recoveryRequestId=requestId;return event;
    }

    protected UserAuditLog() {}

    public UserAuditLog(User actor, User user) {
        this.actor = actor;
        this.user = user;
        this.assignedRole = user.getRole();
        this.operation = "USER_CREATED";
    }

    @PrePersist
    public void prePersist() {
        if (occurredAt == null) occurredAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getActor() { return actor; }
    public User getUser() { return user; }
    public UserRole getAssignedRole() { return assignedRole; }
    public String getOperation() { return operation; }
    public Instant getOccurredAt() { return occurredAt; }
}
