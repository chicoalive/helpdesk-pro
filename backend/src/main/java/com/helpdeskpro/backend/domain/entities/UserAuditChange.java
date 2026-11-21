package com.helpdeskpro.backend.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserAuditChange {
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    protected UserAuditChange() {}
    public UserAuditChange(String oldValue, String newValue) {
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
}
