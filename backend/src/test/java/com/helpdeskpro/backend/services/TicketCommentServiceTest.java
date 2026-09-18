package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.entities.TicketComment;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.TicketCommentRequestDTO;
import com.helpdeskpro.backend.dto.TicketCommentResponseDTO;
import com.helpdeskpro.backend.exceptions.BusinessRuleException;
import com.helpdeskpro.backend.exceptions.TicketNotFoundException;
import com.helpdeskpro.backend.exceptions.UserNotFoundException;
import com.helpdeskpro.backend.repositories.TicketCommentRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCommentServiceTest {

    @Mock
    private TicketCommentRepository ticketCommentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TicketCommentService ticketCommentService;

    private User requester;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        requester = new User("Carlos Solicitante", "carlos@helpdesk.com", "senha123", UserRole.REQUESTER);
        ReflectionTestUtils.setField(requester, "id", 1L);

        ticket = new Ticket("ERP fora do ar", "Falha na conexão", TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, requester, null);
        ReflectionTestUtils.setField(ticket, "id", 10L);
    }

    @Test
    @DisplayName("Deve salvar comentário válido com sucesso")
    void createComment_Valid_Success() {
        TicketCommentRequestDTO dto = new TicketCommentRequestDTO("Verificamos o problema e estamos atuando.", 1L);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(ticketCommentRepository.save(any(TicketComment.class))).thenAnswer(invocation -> {
            TicketComment c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 100L);
            return c;
        });

        TicketCommentResponseDTO response = ticketCommentService.createComment(10L, dto);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Verificamos o problema e estamos atuando.", response.getContent());
        assertEquals(10L, response.getTicketId());
        assertEquals(1L, response.getAuthorId());
        assertEquals("Carlos Solicitante", response.getAuthorName());
        assertEquals(UserRole.REQUESTER, response.getAuthorRole());
        assertNotNull(response.getCreatedAt());

        verify(ticketCommentRepository).save(any(TicketComment.class));
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException quando conteúdo for nulo, vazio ou somente espaços")
    void createComment_EmptyContent_ThrowsBusinessRuleException() {
        TicketCommentRequestDTO nullDto = new TicketCommentRequestDTO(null, 1L);
        TicketCommentRequestDTO emptyDto = new TicketCommentRequestDTO("", 1L);
        TicketCommentRequestDTO blankDto = new TicketCommentRequestDTO("   ", 1L);

        assertThrows(BusinessRuleException.class, () -> ticketCommentService.createComment(10L, nullDto));
        assertThrows(BusinessRuleException.class, () -> ticketCommentService.createComment(10L, emptyDto));
        assertThrows(BusinessRuleException.class, () -> ticketCommentService.createComment(10L, blankDto));

        verify(ticketCommentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar TicketNotFoundException quando ticket não for encontrado")
    void createComment_TicketNotFound_ThrowsTicketNotFoundException() {
        TicketCommentRequestDTO dto = new TicketCommentRequestDTO("Comentário", 1L);
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TicketNotFoundException.class, () -> ticketCommentService.createComment(99L, dto));
        verify(ticketCommentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar UserNotFoundException quando autor (userId) não for encontrado")
    void createComment_UserNotFound_ThrowsUserNotFoundException() {
        TicketCommentRequestDTO dto = new TicketCommentRequestDTO("Comentário", 99L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> ticketCommentService.createComment(10L, dto));
        verify(ticketCommentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve permitir adicionar comentário em ticket com status FECHADO (regra de status livre)")
    void createComment_TicketClosed_Allowed_Success() {
        ticket.setStatus(TicketStatus.FECHADO);
        TicketCommentRequestDTO dto = new TicketCommentRequestDTO("Comentário pós-fechamento do ticket.", 1L);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(ticketCommentRepository.save(any(TicketComment.class))).thenAnswer(invocation -> {
            TicketComment c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 101L);
            return c;
        });

        TicketCommentResponseDTO response = ticketCommentService.createComment(10L, dto);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("Comentário pós-fechamento do ticket.", response.getContent());
    }

    @Test
    @DisplayName("Deve permitir adicionar comentário em ticket com status RESOLVIDO (regra de status livre)")
    void createComment_TicketResolved_Allowed_Success() {
        ticket.setStatus(TicketStatus.RESOLVIDO);
        TicketCommentRequestDTO dto = new TicketCommentRequestDTO("Comentário em ticket resolvido.", 1L);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(ticketCommentRepository.save(any(TicketComment.class))).thenAnswer(invocation -> {
            TicketComment c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 102L);
            return c;
        });

        TicketCommentResponseDTO response = ticketCommentService.createComment(10L, dto);

        assertNotNull(response);
        assertEquals(102L, response.getId());
    }

    @Test
    @DisplayName("Deve buscar e retornar comentários ordenados cronologicamente")
    void getCommentsByTicket_ReturnsOrderedComments() {
        TicketComment c1 = new TicketComment("Primeiro comentário", ticket, requester);
        ReflectionTestUtils.setField(c1, "id", 1L);
        ReflectionTestUtils.setField(c1, "createdAt", LocalDateTime.now().minusMinutes(10));

        TicketComment c2 = new TicketComment("Segundo comentário", ticket, requester);
        ReflectionTestUtils.setField(c2, "id", 2L);
        ReflectionTestUtils.setField(c2, "createdAt", LocalDateTime.now().minusMinutes(5));

        when(ticketRepository.existsById(10L)).thenReturn(true);
        when(ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(c1, c2));

        List<TicketCommentResponseDTO> comments = ticketCommentService.getCommentsByTicket(10L);

        assertNotNull(comments);
        assertEquals(2, comments.size());
        assertEquals("Primeiro comentário", comments.get(0).getContent());
        assertEquals("Segundo comentário", comments.get(1).getContent());
    }

    @Test
    @DisplayName("Deve lançar TicketNotFoundException ao buscar comentários de ticket inexistente")
    void getCommentsByTicket_TicketNotFound_ThrowsTicketNotFoundException() {
        when(ticketRepository.existsById(99L)).thenReturn(false);

        assertThrows(TicketNotFoundException.class, () -> ticketCommentService.getCommentsByTicket(99L));
        verify(ticketCommentRepository, never()).findByTicketIdOrderByCreatedAtAsc(any());
    }
}
