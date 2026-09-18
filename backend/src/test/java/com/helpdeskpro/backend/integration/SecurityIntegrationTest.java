package com.helpdeskpro.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.domain.AssetStatus;
import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.AssetRequestDTO;
import com.helpdeskpro.backend.dto.LoginRequestDTO;
import com.helpdeskpro.backend.dto.TicketStatusUpdateDTO;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("Testes de Integração - Camada de Segurança e JWT (Etapa 6)")
class SecurityIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    private User admin;
    private User requester1;
    private User requester2;
    private User tech1;
    private User tech2;

    private String adminToken;
    private String req1Token;
    private String req2Token;
    private String tech1Token;
    private String tech2Token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ticketCommentRepository.deleteAll();
        ticketRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(new User("Admin", "admin@sec.com", passwordEncoder.encode("senha123"), UserRole.ADMIN));
        requester1 = userRepository.save(new User("Requester 1", "req1@sec.com", passwordEncoder.encode("senha123"), UserRole.REQUESTER));
        requester2 = userRepository.save(new User("Requester 2", "req2@sec.com", passwordEncoder.encode("senha123"), UserRole.REQUESTER));
        tech1 = userRepository.save(new User("Tech 1", "tech1@sec.com", passwordEncoder.encode("senha123"), UserRole.TECHNICIAN));
        tech2 = userRepository.save(new User("Tech 2", "tech2@sec.com", passwordEncoder.encode("senha123"), UserRole.TECHNICIAN));

        adminToken = tokenService.generateToken(admin);
        req1Token = tokenService.generateToken(requester1);
        req2Token = tokenService.generateToken(requester2);
        tech1Token = tokenService.generateToken(tech1);
        tech2Token = tokenService.generateToken(tech2);
    }

    @Test
    @DisplayName("POST /auth/login - Autenticação com credenciais válidas retorna 200 OK e token")
    void login_Success() throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO("admin@sec.com", "senha123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    @DisplayName("POST /auth/login - Autenticação com senha incorreta retorna 401 Unauthorized")
    void login_WrongPassword_Returns401() throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO("admin@sec.com", "senhaErrada");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Requisição sem token em endpoint protegido retorna 401 Unauthorized")
    void unauthenticatedRequest_Returns401() throws Exception {
        mockMvc.perform(get("/assets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        mockMvc.perform(get("/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("REQUESTER tentando listar /users deve retornar 403 Forbidden")
    void requesterListingUsers_Returns403() throws Exception {
        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("REQUESTER tentando criar ativo (POST /assets) deve retornar 403 Forbidden")
    void requesterCreatingAsset_Returns403() throws Exception {
        AssetRequestDTO dto = new AssetRequestDTO("NOTE-SEC", "Notebook", "Dell", AssetStatus.IN_USE);

        mockMvc.perform(post("/assets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("TECHNICIAN tentando criar ativo (POST /assets) deve retornar 403 Forbidden")
    void technicianCreatingAsset_Returns403() throws Exception {
        AssetRequestDTO dto = new AssetRequestDTO("NOTE-TECH", "Notebook", "Dell", AssetStatus.IN_USE);

        mockMvc.perform(post("/assets")
                        .header("Authorization", "Bearer " + tech1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("Usuário autenticado (REQUESTER) pode ler ativos (GET /assets) -> 200 OK")
    void requesterReadingAssets_Returns200() throws Exception {
        assetRepository.save(new Asset("NOTE-001", "Notebook", "Dell", AssetStatus.IN_USE));

        mockMvc.perform(get("/assets")
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("REQUESTER tentando visualizar chamado de outro solicitante deve retornar 403 Forbidden")
    void requesterViewingOtherRequesterTicket_Returns403() throws Exception {
        Ticket ticket = ticketRepository.save(new Ticket(
                "Ticket de R2",
                "Descricao",
                TicketCategory.DUVIDA,
                TicketPriority.BAIXA,
                requester2,
                null
        ));

        mockMvc.perform(get("/tickets/" + ticket.getId())
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("TECHNICIAN tentando alterar chamado atribuído a outro técnico deve retornar 403 Forbidden")
    void technicianAlteringOtherTechnicianTicket_Returns403() throws Exception {
        Ticket ticket = new Ticket(
                "Ticket de R1",
                "Descricao",
                TicketCategory.DUVIDA,
                TicketPriority.BAIXA,
                requester1,
                null
        );
        ticket.setStatus(TicketStatus.ABERTO);
        ticket.setTechnician(tech1);
        ticket = ticketRepository.save(ticket);

        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.EM_ATENDIMENTO, null);

        // tech2 tenta alterar ticket atribuído a tech1
        mockMvc.perform(patch("/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + tech2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("REQUESTER tentando alterar status para outro que não seja CANCELADO deve retornar 403 Forbidden")
    void requesterAlteringStatusNotCancel_Returns403() throws Exception {
        Ticket ticket = new Ticket(
                "Ticket de R1",
                "Descricao",
                TicketCategory.DUVIDA,
                TicketPriority.BAIXA,
                requester1,
                null
        );
        ticket.setStatus(TicketStatus.ABERTO);
        ticket = ticketRepository.save(ticket);

        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.EM_ATENDIMENTO, null);

        mockMvc.perform(patch("/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }
}
