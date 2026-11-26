package com.helpdeskpro.backend.domain;

import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.enums.*;
import java.time.*;

/** Hours are continuous; equality with the deadline remains within the deadline. */
public final class TicketSla {
    private TicketSla() {}
    public record Assessment(TicketSlaStatus status, Instant deadline, Long delaySeconds) {}
    public static Instant deadline(Instant createdAt, TicketPriority priority) {
        return createdAt == null || priority == null ? null : createdAt.plus(Duration.ofHours(priority.getSlaHours()));
    }
    public static Assessment assess(Ticket ticket, Instant now) {
        Instant deadline = deadline(ticket.getCreatedAt(), ticket.getPriority());
        if (ticket.getStatus() == TicketStatus.CANCELADO) return new Assessment(TicketSlaStatus.CANCELLED, deadline, null);
        if (ticket.getStatus() == TicketStatus.RESOLVIDO || ticket.getStatus() == TicketStatus.FECHADO) {
            Instant resolved = ticket.getResolvedAt(), recordedDeadline = ticket.getSlaResolutionDeadline();
            if (resolved == null || recordedDeadline == null || ticket.getCreatedAt() == null || resolved.isBefore(ticket.getCreatedAt()))
                return new Assessment(TicketSlaStatus.UNKNOWN, deadline, null);
            return new Assessment(resolved.isAfter(recordedDeadline) ? TicketSlaStatus.RESOLVED_OVERDUE : TicketSlaStatus.RESOLVED_ON_TIME,
                    recordedDeadline, delay(recordedDeadline, resolved));
        }
        if (ticket.getStatus() == null || deadline == null || now == null || now.isBefore(ticket.getCreatedAt()))
            return new Assessment(TicketSlaStatus.UNKNOWN, deadline, null);
        return new Assessment(now.isAfter(deadline) ? TicketSlaStatus.IN_PROGRESS_OVERDUE : TicketSlaStatus.IN_PROGRESS_ON_TIME,
                deadline, delay(deadline, now));
    }
    private static long delay(Instant deadline, Instant reference) {
        if (!reference.isAfter(deadline)) return 0;
        Duration duration = Duration.between(deadline, reference);
        return duration.getSeconds() + (duration.getNano() == 0 ? 0 : 1);
    }
}
