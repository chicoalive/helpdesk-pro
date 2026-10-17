package com.helpdeskpro.backend.domain.enums;

public enum TicketPriority {
    BAIXA(72),
    MEDIA(24),
    ALTA(4);

    private final long slaHours;

    TicketPriority(long slaHours) {
        this.slaHours = slaHours;
    }

    public long getSlaHours() {
        return slaHours;
    }
}
