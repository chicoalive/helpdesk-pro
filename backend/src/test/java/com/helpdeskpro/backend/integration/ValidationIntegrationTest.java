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
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketCommentRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("Testes de Integração - Validações de Entrada e Padronização de Erro 400 (Etapa 7)")
class ValidationIntegrationTest {

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
    private User requester;
    private User technician;

    private String adminToken;
    private String requesterToken;
    private String techToken;

    private Asset savedAsset;
    private Ticket savedTicket;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ticketCommentRepository.deleteAll();
        ticketRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(new User("Admin", "admin@val.com", passwordEncoder.encode("123"), UserRole.ADMIN));
        requester = userRepository.save(new User("Requester", "req@val.com", passwordEncoder.encode("123"), UserRole.REQUESTER));
        technician = userRepository.save(new User("Technician", "tech@val.com", passwordEncoder.encode("123"), UserRole.TECHNICIAN));

        adminToken = tokenService.generateToken(admin);
        requesterToken = tokenService.generateToken(requester);
        techToken = tokenService.generateToken(technician);

        savedAsset = assetRepository.save(new Asset("NOTE-001", "Notebook", "Dell Latitude", AssetStatus.IN_USE));
        savedTicket = ticketRepository.save(new Ticket(
                "Problema no ERP",
                "Erro ao emitir notas",
                TicketCategory.INCIDENTE_SISTEMA,
                TicketPriority.ALTA,
                requester,
                null
        ));
    }

    @Nested
    @DisplayName("Validação do LoginRequestDTO")
    class LoginValidationTests {

        @Test
        @DisplayName("POST /auth/login - JSON vazio deve retornar 400 com erros de email e senha")
        void login_EmptyJson_Returns400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.error", is("Bad Request")))
                    .andExpect(jsonPath("$.message", is("Erro de validação nos campos informados")))
                    .andExpect(jsonPath("$.path", is("/auth/login")))
                    .andExpect(jsonPath("$.timestamp", notNullValue()))
                    .andExpect(jsonPath("$.validationErrors.email", is("O e-mail é obrigatório")))
                    .andExpect(jsonPath("$.validationErrors.password", is("A senha é obrigatória")));
        }

        @Test
        @DisplayName("POST /auth/login - Formato de e-mail inválido deve retornar 400")
        void login_InvalidEmailFormat_Returns400() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "email": "email_invalido_sem_arroba",
                                        "password": "123"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.validationErrors.email", is("Formato de e-mail inválido")));
        }
    }

    @Nested
    @DisplayName("Validação do AssetRequestDTO e AssetStatusUpdateDTO")
    class AssetValidationTests {

        @Test
        @DisplayName("POST /assets - JSON vazio deve retornar 400 com erros de code, type e status")
        void createAsset_EmptyJson_Returns400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/assets")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.error", is("Bad Request")))
                    .andExpect(jsonPath("$.message", is("Erro de validação nos campos informados")))
                    .andExpect(jsonPath("$.validationErrors.code", is("O código do ativo é obrigatório")))
                    .andExpect(jsonPath("$.validationErrors.type", is("O tipo do ativo é obrigatório")))
                    .andExpect(jsonPath("$.validationErrors.status", is("O status do ativo é obrigatório")));
        }

        @Test
        @DisplayName("PUT /assets/{id} - Campos em branco e status nulo deve retornar 400 com mensagens por campo")
        void updateAsset_BlankFields_Returns400WithFieldErrors() throws Exception {
            mockMvc.perform(put("/assets/" + savedAsset.getId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "code": "   ",
                                        "type": "",
                                        "status": null
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.validationErrors.code", is("O código do ativo é obrigatório")))
                    .andExpect(jsonPath("$.validationErrors.type", is("O tipo do ativo é obrigatório")))
                    .andExpect(jsonPath("$.validationErrors.status", is("O status do ativo é obrigatório")));
        }

        @Test
        @DisplayName("PATCH /assets/{id}/status - Status nulo deve retornar 400 com mensagem no campo status")
        void updateAssetStatus_NullStatus_Returns400() throws Exception {
            mockMvc.perform(patch("/assets/" + savedAsset.getId() + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.validationErrors.status", is("O status do ativo é obrigatório")));
        }
    }

    @Nested
    @DisplayName("Validação do TicketRequestDTO, TicketAssignTechnicianDTO e TicketStatusUpdateDTO")
    class TicketValidationTests {

        @Test
        @DisplayName("POST /tickets - JSON vazio deve retornar 400 com erros de title, description e category")
        void createTicket_EmptyJson_Returns400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/tickets")
                            .header("Authorization", "Bearer " + requesterToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.error", is("Bad Request")))
                    .andExpect(jsonPath("$.message", is("Erro de validação nos campos informados")))
                    .andExpect(jsonPath("$.validationErrors.title", is("O título é obrigatório")))
                    .andExpect(jsonPath("$.validationErrors.description", is("A descrição é obrigatória")))
                    .andExpect(jsonPath("$.validationErrors.category", is("A categoria é obrigatória")));
        }

        @Test
        @DisplayName("PATCH /tickets/{id}/technician - ID do técnico nulo deve retornar 400")
        void assignTechnician_NullTechnicianId_Returns400() throws Exception {
            mockMvc.perform(patch("/tickets/" + savedTicket.getId() + "/technician")
                            .header("Authorization", "Bearer " + techToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.validationErrors.technicianId", is("O ID do técnico é obrigatório")));
        }

        @Test
        @DisplayName("PATCH /tickets/{id}/status - Status nulo deve retornar 400")
        void updateTicketStatus_NullStatus_Returns400() throws Exception {
            savedTicket.setTechnician(technician);
            ticketRepository.save(savedTicket);

            mockMvc.perform(patch("/tickets/" + savedTicket.getId() + "/status")
                            .header("Authorization", "Bearer " + techToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.validationErrors.status", is("O status é obrigatório")));
        }
    }

    @Nested
    @DisplayName("Validação do TicketCommentRequestDTO")
    class TicketCommentValidationTests {

        @Test
        @DisplayName("POST /tickets/{id}/comments - Conteúdo em branco deve retornar 400 com validação de campo")
        void createComment_BlankContent_Returns400WithFieldError() throws Exception {
            mockMvc.perform(post("/tickets/" + savedTicket.getId() + "/comments")
                            .header("Authorization", "Bearer " + requesterToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "content": "    "
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.validationErrors.content", is("O conteúdo do comentário é obrigatório")));
        }
    }
}
