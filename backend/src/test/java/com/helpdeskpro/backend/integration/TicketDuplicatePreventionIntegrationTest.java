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
import com.helpdeskpro.backend.dto.TicketRequestDTO;
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

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("Testes de Integração - Prevenção de Chamados Repetidos")
class TicketDuplicatePreventionIntegrationTest {

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

    private User requester1;
    private User requester2;
    private String req1Token;
    private String req2Token;

    private Asset asset1;
    private Asset asset2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ticketCommentRepository.deleteAll();
        ticketRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();

        requester1 = userRepository.save(new User("Requester 1", "req1@dup.com", passwordEncoder.encode("123"), UserRole.REQUESTER));
        requester2 = userRepository.save(new User("Requester 2", "req2@dup.com", passwordEncoder.encode("123"), UserRole.REQUESTER));

        req1Token = tokenService.generateToken(requester1);
        req2Token = tokenService.generateToken(requester2);

        asset1 = assetRepository.save(new Asset("NOTE-101", "Notebook", "Dell Latitude", AssetStatus.IN_USE));
        asset2 = assetRepository.save(new Asset("MON-202", "Monitor", "LG Ultrawide", AssetStatus.IN_USE));
    }

    private Ticket saveTicketWithStatus(User requester, TicketCategory category, TicketPriority priority, Asset asset, TicketStatus status) {
        Ticket ticket = new Ticket("Título Inicial", "Descrição Inicial", category, priority, requester, asset);
        ticket.setStatus(status);
        return ticketRepository.save(ticket);
    }

    @Test
    @DisplayName("1. INCIDENTE_SISTEMA ativo e SOLICITACAO_ACESSO ativo -> bloqueiam novo chamado e retornam existingTicketId")
    void activeIncidenteSistemaAndSolicitacaoAcesso_BlocksCreation() throws Exception {
        Ticket tSistema = saveTicketWithStatus(requester1, TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, null, TicketStatus.CRIADO);
        Ticket tAcesso = saveTicketWithStatus(requester1, TicketCategory.SOLICITACAO_ACESSO, TicketPriority.MEDIA, null, TicketStatus.AGUARDANDO_USUARIO);

        TicketRequestDTO reqSistema = new TicketRequestDTO("Título Inicial", "Descrição Inicial", TicketCategory.INCIDENTE_SISTEMA, null);
        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqSistema)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Você já possui um chamado em andamento para este tipo de problema. Acompanhe o chamado #" + tSistema.getId() + ".")))
                .andExpect(jsonPath("$.existingTicketId", is(tSistema.getId().intValue())));

        TicketRequestDTO reqAcesso = new TicketRequestDTO("Acesso pasta", "Preciso de acesso", TicketCategory.SOLICITACAO_ACESSO, null);
        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAcesso)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Você já possui um chamado em andamento para este tipo de problema. Acompanhe o chamado #" + tAcesso.getId() + ".")))
                .andExpect(jsonPath("$.existingTicketId", is(tAcesso.getId().intValue())));
    }

    @Test
    @DisplayName("2. Título diferente na mesma categoria ativa (INCIDENTE_SISTEMA) -> continua bloqueando")
    void differentTitleInSameActiveCategory_StillBlocks() throws Exception {
        Ticket existing = saveTicketWithStatus(requester1, TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, null, TicketStatus.ABERTO);

        TicketRequestDTO dto = new TicketRequestDTO("Título Completamente Diferente XYZ", "Descrição Inicial", TicketCategory.INCIDENTE_SISTEMA, null);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Você já possui um chamado em andamento para este tipo de problema. Acompanhe o chamado #" + existing.getId() + ".")))
                .andExpect(jsonPath("$.existingTicketId", is(existing.getId().intValue())));
    }

    @Test
    @DisplayName("3. Descrição diferente na mesma categoria ativa (SOLICITACAO_ACESSO) -> continua bloqueando")
    void differentDescriptionInSameActiveCategory_StillBlocks() throws Exception {
        Ticket existing = saveTicketWithStatus(requester1, TicketCategory.SOLICITACAO_ACESSO, TicketPriority.MEDIA, null, TicketStatus.EM_ATENDIMENTO);

        TicketRequestDTO dto = new TicketRequestDTO("Título Inicial", "Descrição totalmente distinta com texto novo", TicketCategory.SOLICITACAO_ACESSO, null);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Você já possui um chamado em andamento para este tipo de problema. Acompanhe o chamado #" + existing.getId() + ".")))
                .andExpect(jsonPath("$.existingTicketId", is(existing.getId().intValue())));
    }

    @Test
    @DisplayName("4. Chamado atrasado (SLA estourado) e ativo -> continua bloqueando")
    void breachedSlaActiveTicket_StillBlocks() throws Exception {
        Ticket breached = saveTicketWithStatus(requester1, TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, null, TicketStatus.ABERTO);
        breached.setCreatedAt(Instant.now().minus(Duration.ofHours(100)));

        TicketRequestDTO dto = new TicketRequestDTO("Novo chamado após atraso", "Ainda fora do ar", TicketCategory.INCIDENTE_SISTEMA, null);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Você já possui um chamado em andamento para este tipo de problema. Acompanhe o chamado #" + breached.getId() + ".")))
                .andExpect(jsonPath("$.existingTicketId", is(breached.getId().intValue())));
    }

    @Test
    @DisplayName("5. Chamado anterior RESOLVIDO -> permite abrir novo chamado na mesma categoria")
    void resolvedTicket_AllowsNewTicket() throws Exception {
        Ticket resolved = saveTicketWithStatus(requester1, TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, null, TicketStatus.RESOLVIDO);
        resolved.setSolution("Serviço reiniciado");
        ticketRepository.save(resolved);

        TicketRequestDTO dto = new TicketRequestDTO("Novo problema no sistema", "Voltou a apresentar lentidão", TicketCategory.INCIDENTE_SISTEMA, null);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CRIADO")))
                .andExpect(jsonPath("$.category", is("INCIDENTE_SISTEMA")));
    }

    @Test
    @DisplayName("6. Chamado anterior FECHADO -> permite abrir novo chamado na mesma categoria")
    void closedTicket_AllowsNewTicket() throws Exception {
        Ticket closed = saveTicketWithStatus(requester1, TicketCategory.SOLICITACAO_ACESSO, TicketPriority.MEDIA, null, TicketStatus.FECHADO);
        closed.setSolution("Acesso liberado");
        ticketRepository.save(closed);

        TicketRequestDTO dto = new TicketRequestDTO("Nova solicitação de acesso", "Acesso ao módulo financeiro", TicketCategory.SOLICITACAO_ACESSO, null);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CRIADO")))
                .andExpect(jsonPath("$.category", is("SOLICITACAO_ACESSO")));
    }

    @Test
    @DisplayName("7. Chamado anterior CANCELADO -> permite abrir novo chamado na mesma categoria")
    void canceledTicket_AllowsNewTicket() throws Exception {
        saveTicketWithStatus(requester1, TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, null, TicketStatus.CANCELADO);

        TicketRequestDTO dto = new TicketRequestDTO("Novo problema após cancelamento", "Sistema caiu", TicketCategory.INCIDENTE_SISTEMA, null);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CRIADO")))
                .andExpect(jsonPath("$.category", is("INCIDENTE_SISTEMA")));
    }

    @Test
    @DisplayName("8. Requester diferente -> permite abrir chamado ativo na mesma categoria")
    void differentRequester_AllowsNewTicketInSameCategory() throws Exception {
        saveTicketWithStatus(requester1, TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, null, TicketStatus.CRIADO);

        TicketRequestDTO dto = new TicketRequestDTO("Também estou sem sistema", "Erro ao logar", TicketCategory.INCIDENTE_SISTEMA, null);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requesterId", is(requester2.getId().intValue())))
                .andExpect(jsonPath("$.category", is("INCIDENTE_SISTEMA")));
    }

    @Test
    @DisplayName("9. INCIDENTE_EQUIPAMENTO no mesmo equipamento ativo do mesmo requester -> bloqueia")
    void equipmentIncidentSameAsset_BlocksCreation() throws Exception {
        Ticket existingAssetTicket = saveTicketWithStatus(
                requester1,
                TicketCategory.INCIDENTE_EQUIPAMENTO,
                TicketPriority.MEDIA,
                asset1,
                TicketStatus.EM_ATENDIMENTO
        );

        TicketRequestDTO dto = new TicketRequestDTO(
                "Outro problema no mesmo notebook",
                "Agora a bateria não carrega",
                TicketCategory.INCIDENTE_EQUIPAMENTO,
                asset1.getId()
        );

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Você já possui um chamado em andamento para este tipo de problema. Acompanhe o chamado #" + existingAssetTicket.getId() + ".")))
                .andExpect(jsonPath("$.existingTicketId", is(existingAssetTicket.getId().intValue())));
    }

    @Test
    @DisplayName("10. INCIDENTE_EQUIPAMENTO em equipamento diferente do mesmo requester -> permite")
    void equipmentIncidentDifferentAsset_AllowsCreation() throws Exception {
        saveTicketWithStatus(
                requester1,
                TicketCategory.INCIDENTE_EQUIPAMENTO,
                TicketPriority.MEDIA,
                asset1,
                TicketStatus.CRIADO
        );

        TicketRequestDTO dto = new TicketRequestDTO(
                "Monitor piscando",
                "Monitor apresenta faixas na tela",
                TicketCategory.INCIDENTE_EQUIPAMENTO,
                asset2.getId()
        );

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category", is("INCIDENTE_EQUIPAMENTO")))
                .andExpect(jsonPath("$.assetId", is(asset2.getId().intValue())));
    }

    @Test
    @DisplayName("11. DUVIDA mesmo com outra DUVIDA ativa do mesmo requester -> permite")
    void activeDuvida_AllowsAnotherDuvida() throws Exception {
        saveTicketWithStatus(requester1, TicketCategory.DUVIDA, TicketPriority.BAIXA, null, TicketStatus.CRIADO);
        saveTicketWithStatus(requester1, TicketCategory.DUVIDA, TicketPriority.BAIXA, null, TicketStatus.EM_ATENDIMENTO);

        TicketRequestDTO dto = new TicketRequestDTO(
                "Outra dúvida sobre certificado digital",
                "Como renovar meu token?",
                TicketCategory.DUVIDA,
                null
        );

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CRIADO")))
                .andExpect(jsonPath("$.category", is("DUVIDA")));
    }

    @Test
    @DisplayName("12. OUTRO mesmo com outro chamado OUTRO ativo do mesmo requester -> permite")
    void activeOutro_AllowsAnotherOutro() throws Exception {
        saveTicketWithStatus(requester1, TicketCategory.OUTRO, TicketPriority.MEDIA, null, TicketStatus.ABERTO);

        TicketRequestDTO dto = new TicketRequestDTO(
                "Outro assunto diferente",
                "Solicitação geral de apoio em reunião",
                TicketCategory.OUTRO,
                null
        );

        mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + req1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CRIADO")))
                .andExpect(jsonPath("$.category", is("OUTRO")));
    }
}
