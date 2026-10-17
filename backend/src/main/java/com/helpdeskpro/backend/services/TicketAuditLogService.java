package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.dto.TicketAuditLogResponseDTO;
import com.helpdeskpro.backend.exceptions.TicketNotFoundException;
import com.helpdeskpro.backend.repositories.TicketAuditLogRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketAuditLogService {

    private final TicketRepository ticketRepository;
    private final TicketAuditLogRepository ticketAuditLogRepository;

    public TicketAuditLogService(TicketRepository ticketRepository,
                                 TicketAuditLogRepository ticketAuditLogRepository) {
        this.ticketRepository = ticketRepository;
        this.ticketAuditLogRepository = ticketAuditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<TicketAuditLogResponseDTO> getAuditByTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId));

        return ticketAuditLogRepository.findByTicketIdOrderByChangedAtAsc(ticket.getId())
                .stream()
                .map(TicketAuditLogResponseDTO::fromEntity)
                .toList();
    }
}
