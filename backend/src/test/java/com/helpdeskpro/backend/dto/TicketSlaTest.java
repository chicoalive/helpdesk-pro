package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SLA simples (horas corridas) - TicketPriority e TicketResponseDTO")
class TicketSlaTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private Ticket ticket(TicketPriority priority, TicketStatus status, Instant createdAt) {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, priority, null, null);
        ticket.setStatus(status);
        ticket.setCreatedAt(createdAt);
        return ticket;
    }

    @Test
    @DisplayName("Enum mantém as constantes e define as horas de SLA: BAIXA=72, MEDIA=24, ALTA=4")
    void enumSlaHours() {
        assertEquals(3, TicketPriority.values().length);
        assertEquals(72L, TicketPriority.BAIXA.getSlaHours());
        assertEquals(24L, TicketPriority.MEDIA.getSlaHours());
        assertEquals(4L, TicketPriority.ALTA.getSlaHours());
    }

    @Test
    @DisplayName("BAIXA gera deadline de createdAt + 72h")
    void deadline_Baixa() {
        TicketResponseDTO dto = TicketResponseDTO.fromEntity(
                ticket(TicketPriority.BAIXA, TicketStatus.ABERTO, CREATED_AT), CREATED_AT);
        assertEquals(CREATED_AT.plus(Duration.ofHours(72)), dto.getSlaDeadline());
    }

    @Test
    @DisplayName("MEDIA gera deadline de createdAt + 24h")
    void deadline_Media() {
        TicketResponseDTO dto = TicketResponseDTO.fromEntity(
                ticket(TicketPriority.MEDIA, TicketStatus.ABERTO, CREATED_AT), CREATED_AT);
        assertEquals(CREATED_AT.plus(Duration.ofHours(24)), dto.getSlaDeadline());
    }

    @Test
    @DisplayName("ALTA gera deadline de createdAt + 4h")
    void deadline_Alta() {
        TicketResponseDTO dto = TicketResponseDTO.fromEntity(
                ticket(TicketPriority.ALTA, TicketStatus.ABERTO, CREATED_AT), CREATED_AT);
        assertEquals(CREATED_AT.plus(Duration.ofHours(4)), dto.getSlaDeadline());
    }

    @Test
    @DisplayName("Chamado ativo dentro do prazo retorna slaBreached=false (inclusive exatamente no deadline)")
    void activeWithinDeadline_NotBreached() {
        Ticket ticket = ticket(TicketPriority.ALTA, TicketStatus.EM_ATENDIMENTO, CREATED_AT);
        Instant deadline = CREATED_AT.plus(Duration.ofHours(4));

        assertFalse(TicketResponseDTO.fromEntity(ticket, deadline.minusSeconds(1)).isSlaBreached());
        assertFalse(TicketResponseDTO.fromEntity(ticket, deadline).isSlaBreached());
    }

    @Test
    @DisplayName("Chamado ativo depois do prazo retorna slaBreached=true")
    void activeAfterDeadline_Breached() {
        Instant afterDeadline = CREATED_AT.plus(Duration.ofHours(4)).plusSeconds(1);

        for (TicketStatus status : new TicketStatus[]{
                TicketStatus.CRIADO, TicketStatus.ABERTO, TicketStatus.EM_ATENDIMENTO, TicketStatus.AGUARDANDO_USUARIO}) {
            TicketResponseDTO dto = TicketResponseDTO.fromEntity(ticket(TicketPriority.ALTA, status, CREATED_AT), afterDeadline);
            assertTrue(dto.isSlaBreached(), "Esperado slaBreached=true para status " + status);
        }
    }

    @Test
    @DisplayName("RESOLVIDO depois do prazo retorna slaBreached=false")
    void resolvido_AfterDeadline_NotBreached() {
        assertNotBreachedForTerminal(TicketStatus.RESOLVIDO);
    }

    @Test
    @DisplayName("FECHADO depois do prazo retorna slaBreached=false")
    void fechado_AfterDeadline_NotBreached() {
        assertNotBreachedForTerminal(TicketStatus.FECHADO);
    }

    @Test
    @DisplayName("CANCELADO depois do prazo retorna slaBreached=false")
    void cancelado_AfterDeadline_NotBreached() {
        assertNotBreachedForTerminal(TicketStatus.CANCELADO);
    }

    private void assertNotBreachedForTerminal(TicketStatus status) {
        Instant wellAfterDeadline = CREATED_AT.plus(Duration.ofHours(1000));
        TicketResponseDTO dto = TicketResponseDTO.fromEntity(ticket(TicketPriority.ALTA, status, CREATED_AT), wellAfterDeadline);
        assertNotNull(dto.getSlaDeadline());
        assertFalse(dto.isSlaBreached());
    }

    @Test
    @DisplayName("createdAt ou priority nulos não quebram o mapeamento: deadline nulo e slaBreached=false")
    void nullCreatedAtOrPriority_SafeMapping() {
        TicketResponseDTO noCreatedAt = TicketResponseDTO.fromEntity(
                ticket(TicketPriority.ALTA, TicketStatus.ABERTO, null), CREATED_AT.plus(Duration.ofHours(1000)));
        assertNull(noCreatedAt.getSlaDeadline());
        assertFalse(noCreatedAt.isSlaBreached());

        TicketResponseDTO noPriority = TicketResponseDTO.fromEntity(
                ticket(null, TicketStatus.ABERTO, CREATED_AT), CREATED_AT.plus(Duration.ofHours(1000)));
        assertNull(noPriority.getSlaDeadline());
        assertFalse(noPriority.isSlaBreached());
    }

    @Test
    @DisplayName("fromEntity(ticket) usa o relógio atual: chamado antigo ativo está estourado")
    void fromEntityWithoutClock_UsesCurrentTime() {
        Ticket old = ticket(TicketPriority.ALTA, TicketStatus.ABERTO, Instant.now().minus(Duration.ofHours(100)));
        Ticket recent = ticket(TicketPriority.BAIXA, TicketStatus.ABERTO, Instant.now());

        assertTrue(TicketResponseDTO.fromEntity(old).isSlaBreached());
        assertFalse(TicketResponseDTO.fromEntity(recent).isSlaBreached());
    }
}
