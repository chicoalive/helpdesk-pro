package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.entities.TicketComment;
import com.helpdeskpro.backend.dto.TicketCommentRequestDTO;
import com.helpdeskpro.backend.dto.TicketCommentResponseDTO;
import com.helpdeskpro.backend.exceptions.BusinessRuleException;
import com.helpdeskpro.backend.exceptions.TicketNotFoundException;
import com.helpdeskpro.backend.repositories.TicketCommentRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TicketCommentService {

    private final TicketCommentRepository ticketCommentRepository;
    private final TicketRepository ticketRepository;
    private final SecurityUtils securityUtils;

    public TicketCommentService(TicketCommentRepository ticketCommentRepository,
                                TicketRepository ticketRepository,
                                SecurityUtils securityUtils) {
        this.ticketCommentRepository = ticketCommentRepository;
        this.ticketRepository = ticketRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public TicketCommentResponseDTO createComment(Long ticketId, TicketCommentRequestDTO dto) {
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new BusinessRuleException("O conteúdo do comentário é obrigatório.");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId));

        User currentUser = securityUtils.getCurrentUser();

        if (currentUser.getRole() == UserRole.REQUESTER && !ticket.getRequester().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Você não possui permissão para comentar neste chamado.");
        }

        TicketComment comment = new TicketComment(dto.getContent().trim(), ticket, currentUser);
        TicketComment saved = ticketCommentRepository.save(comment);

        return TicketCommentResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<TicketCommentResponseDTO> getCommentsByTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId));

        User currentUser = securityUtils.getCurrentUser();

        if (currentUser.getRole() == UserRole.REQUESTER && !ticket.getRequester().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Você não possui permissão para visualizar comentários deste chamado.");
        }

        return ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(TicketCommentResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
