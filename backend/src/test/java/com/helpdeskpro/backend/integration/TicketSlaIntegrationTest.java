package com.helpdeskpro.backend.integration;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketCommentRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("Testes de Integração - SLA simples na resposta de chamados (Etapa 11)")
class TicketSlaIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private User requester;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ticketCommentRepository.deleteAll();
        ticketRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(new User("Admin", "admin@sla.com", passwordEncoder.encode("senha123"), UserRole.ADMIN));
        requester = userRepository.save(new User("Solicitante", "req@sla.com", passwordEncoder.encode("senha123"), UserRole.REQUESTER));
        adminToken = tokenService.generateToken(admin);
    }

    @Test
    @DisplayName("GET /tickets/{id} expõe slaDeadline e slaBreached=false para chamado recém-criado")
    void getTicket_ExposesSlaFields_WithinDeadline() throws Exception {
        Ticket ticket = ticketRepository.save(
                new Ticket("Chamado novo", "Desc", TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, requester, null));

        mockMvc.perform(get("/tickets/" + ticket.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaDeadline", notNullValue()))
                .andExpect(jsonPath("$.slaBreached", is(false)));
    }

    @Test
    @DisplayName("GET /tickets lista slaBreached=true para chamado ativo com createdAt antigo e false se FECHADO")
    void listTickets_ExposesBreach_ForOldActiveTicketOnly() throws Exception {
        Ticket active = ticketRepository.save(
                new Ticket("Ativo atrasado", "Desc", TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, requester, null));
        Ticket closed = ticketRepository.save(
                new Ticket("Fechado antigo", "Desc", TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, requester, null));

        // @PrePersist define createdAt = agora; ajusta a instância gerenciada (mesma transação do teste)
        // para um instante fixo no passado, sem sleep e sem alterar o enum.
        active.setCreatedAt(Instant.now().minus(Duration.ofHours(100)));
        closed.setCreatedAt(Instant.now().minus(Duration.ofHours(100)));
        closed.setStatus(TicketStatus.FECHADO);

        mockMvc.perform(get("/tickets/" + active.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaDeadline", notNullValue()))
                .andExpect(jsonPath("$.slaBreached", is(true)));

        mockMvc.perform(get("/tickets/" + closed.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaDeadline", notNullValue()))
                .andExpect(jsonPath("$.slaBreached", is(false)));
    }
}
