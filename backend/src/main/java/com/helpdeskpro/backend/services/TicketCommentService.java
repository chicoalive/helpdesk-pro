package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.entities.TicketComment;
import com.helpdeskpro.backend.dto.TicketCommentRequestDTO;
import com.helpdeskpro.backend.dto.TicketCommentResponseDTO;
import com.helpdeskpro.backend.exceptions.BusinessRuleException;
import com.helpdeskpro.backend.exceptions.TicketNotFoundException;
import com.helpdeskpro.backend.exceptions.UserNotFoundException;
import com.helpdeskpro.backend.repositories.TicketCommentRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TicketCommentService {

    private final TicketCommentRepository ticketCommentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public TicketCommentService(TicketCommentRepository ticketCommentRepository,
                                TicketRepository ticketRepository,
                                UserRepository userRepository) {
        this.ticketCommentRepository = ticketCommentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TicketCommentResponseDTO createComment(Long ticketId, TicketCommentRequestDTO dto) {
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new BusinessRuleException("O conteúdo do comentário é obrigatório.");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId));

        User author = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado com o id: " + dto.getUserId()));

        TicketComment comment = new TicketComment(dto.getContent().trim(), ticket, author);
        TicketComment saved = ticketCommentRepository.save(comment);

        return TicketCommentResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<TicketCommentResponseDTO> getCommentsByTicket(Long ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId);
        }

        return ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(TicketCommentResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
