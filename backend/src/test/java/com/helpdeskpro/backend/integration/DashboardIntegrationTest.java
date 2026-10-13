package com.helpdeskpro.backend.integration;

import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.domain.AssetStatus;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("Testes de Integração - Dashboard e Indicadores (Etapa 10)")
class DashboardIntegrationTest {

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
    private User techAna;
    private User techBruno;
    private User techUnused;

    private String adminToken;
    private String requesterToken;
    private String techToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ticketCommentRepository.deleteAll();
        ticketRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(new User("Admin", "admin@dash.com", passwordEncoder.encode("senha123"), UserRole.ADMIN));
        requester = userRepository.save(new User("Solicitante", "req@dash.com", passwordEncoder.encode("senha123"), UserRole.REQUESTER));
        techAna = userRepository.save(new User("Ana", "ana@dash.com", passwordEncoder.encode("senha123"), UserRole.TECHNICIAN));
        techBruno = userRepository.save(new User("Bruno", "bruno@dash.com", passwordEncoder.encode("senha123"), UserRole.TECHNICIAN));
        techUnused = userRepository.save(new User("Carla", "carla@dash.com", passwordEncoder.encode("senha123"), UserRole.TECHNICIAN));

        adminToken = tokenService.generateToken(admin);
        requesterToken = tokenService.generateToken(requester);
        techToken = tokenService.generateToken(techAna);
    }

    private Ticket saveTicket(TicketStatus status, TicketPriority priority, User technician) {
        Ticket ticket = new Ticket("Chamado", "Descrição", TicketCategory.DUVIDA, priority, requester, null);
        ticket.setStatus(status);
        ticket.setTechnician(technician);
        return ticketRepository.save(ticket);
    }

    private void seedData() {
        saveTicket(TicketStatus.ABERTO, TicketPriority.ALTA, techAna);
        saveTicket(TicketStatus.EM_ATENDIMENTO, TicketPriority.ALTA, techAna);
        saveTicket(TicketStatus.FECHADO, TicketPriority.MEDIA, techAna);
        saveTicket(TicketStatus.EM_ATENDIMENTO, TicketPriority.BAIXA, techBruno);
        saveTicket(TicketStatus.CANCELADO, TicketPriority.MEDIA, null);
        saveTicket(TicketStatus.CRIADO, TicketPriority.MEDIA, null);

        assetRepository.save(new Asset("DASH-1", "Notebook", "Dell", AssetStatus.UNDER_MAINTENANCE));
        assetRepository.save(new Asset("DASH-2", "Notebook", "HP", AssetStatus.UNDER_MAINTENANCE));
        assetRepository.save(new Asset("DASH-3", "Monitor", "LG", AssetStatus.IN_USE));
        assetRepository.save(new Asset("DASH-4", "Mouse", "Logitech", AssetStatus.DISCARDED));
    }

    @Test
    @DisplayName("ADMIN recebe 200 OK com todas as contagens corretas")
    void admin_Returns200WithCounts() throws Exception {
        seedData();

        mockMvc.perform(get("/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsByStatus.CRIADO", is(1)))
                .andExpect(jsonPath("$.ticketsByStatus.ABERTO", is(1)))
                .andExpect(jsonPath("$.ticketsByStatus.EM_ATENDIMENTO", is(2)))
                .andExpect(jsonPath("$.ticketsByStatus.AGUARDANDO_USUARIO", is(0)))
                .andExpect(jsonPath("$.ticketsByStatus.RESOLVIDO", is(0)))
                .andExpect(jsonPath("$.ticketsByStatus.FECHADO", is(1)))
                .andExpect(jsonPath("$.ticketsByStatus.CANCELADO", is(1)))
                .andExpect(jsonPath("$.ticketsByPriority.BAIXA", is(1)))
                .andExpect(jsonPath("$.ticketsByPriority.MEDIA", is(3)))
                .andExpect(jsonPath("$.ticketsByPriority.ALTA", is(2)))
                .andExpect(jsonPath("$.ticketsByTechnician", hasSize(2)))
                .andExpect(jsonPath("$.ticketsByTechnician[0].technicianId", is(techAna.getId().intValue())))
                .andExpect(jsonPath("$.ticketsByTechnician[0].technicianName", is("Ana")))
                .andExpect(jsonPath("$.ticketsByTechnician[0].total", is(3)))
                .andExpect(jsonPath("$.ticketsByTechnician[1].technicianName", is("Bruno")))
                .andExpect(jsonPath("$.ticketsByTechnician[1].total", is(1)))
                .andExpect(jsonPath("$.unassignedTickets", is(2)))
                .andExpect(jsonPath("$.assetsUnderMaintenance", is(2)))
                .andExpect(jsonPath("$.totalTickets").doesNotExist());
    }

    @Test
    @DisplayName("TECHNICIAN recebe 200 OK")
    void technician_Returns200() throws Exception {
        seedData();

        mockMvc.perform(get("/dashboard/summary")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassignedTickets", is(2)));
    }

    @Test
    @DisplayName("REQUESTER recebe 403 Forbidden e nenhum dado global")
    void requester_Returns403() throws Exception {
        seedData();

        mockMvc.perform(get("/dashboard/summary")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(content().string(not(containsString("ticketsByStatus"))));
    }

    @Test
    @DisplayName("Sem token recebe 401 Unauthorized")
    void noToken_Returns401() throws Exception {
        mockMvc.perform(get("/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Sem dados: contagens zeradas, lista de técnicos vazia")
    void noData_ReturnsZeroedCounts() throws Exception {
        mockMvc.perform(get("/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsByStatus.CRIADO", is(0)))
                .andExpect(jsonPath("$.ticketsByStatus.ABERTO", is(0)))
                .andExpect(jsonPath("$.ticketsByStatus.EM_ATENDIMENTO", is(0)))
                .andExpect(jsonPath("$.ticketsByStatus.AGUARDANDO_USUARIO", is(0)))
                .andExpect(jsonPath("$.ticketsByStatus.RESOLVIDO", is(0)))
                .andExpect(jsonPath("$.ticketsByStatus.FECHADO", is(0)))
                .andExpect(jsonPath("$.ticketsByStatus.CANCELADO", is(0)))
                .andExpect(jsonPath("$.ticketsByPriority.BAIXA", is(0)))
                .andExpect(jsonPath("$.ticketsByPriority.MEDIA", is(0)))
                .andExpect(jsonPath("$.ticketsByPriority.ALTA", is(0)))
                .andExpect(jsonPath("$.ticketsByTechnician", hasSize(0)))
                .andExpect(jsonPath("$.unassignedTickets", is(0)))
                .andExpect(jsonPath("$.assetsUnderMaintenance", is(0)));
    }

    @Test
    @DisplayName("Técnico sem chamados não aparece na lista por técnico")
    void technicianWithoutTickets_NotListed() throws Exception {
        saveTicket(TicketStatus.ABERTO, TicketPriority.BAIXA, techAna);

        mockMvc.perform(get("/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsByTechnician", hasSize(1)))
                .andExpect(jsonPath("$.ticketsByTechnician[0].technicianName", is("Ana")))
                .andExpect(jsonPath("$.unassignedTickets", is(0)));
    }
}
