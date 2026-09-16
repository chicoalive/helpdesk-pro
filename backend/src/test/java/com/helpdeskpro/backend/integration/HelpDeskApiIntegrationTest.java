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
import com.helpdeskpro.backend.dto.TicketAssignTechnicianDTO;
import com.helpdeskpro.backend.dto.TicketRequestDTO;
import com.helpdeskpro.backend.dto.TicketStatusUpdateDTO;
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class HelpDeskApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TicketRepository ticketRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        ticketRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.save(new User(name, email, "senha123", role));
    }

    private Asset saveAsset(String code, String type, AssetStatus status) {
        return assetRepository.save(new Asset(code, type, "Descricao do ativo", status));
    }

    private Ticket saveTicket(String title, String desc, TicketCategory cat, TicketPriority prio, User req, Asset asset, TicketStatus status) {
        Ticket t = new Ticket(title, desc, cat, prio, req, asset);
        t.setStatus(status);
        return ticketRepository.save(t);
    }

    @Nested
    @DisplayName("Cenários de Sucesso")
    class SuccessScenarios {

        @Test
        @DisplayName("1. Criar REQUESTER e TECHNICIAN sem expor password na resposta")
        void cenario1_criarUsuarios_SemExporSenha() throws Exception {
            String requesterJson = """
                {
                    "name": "Carlos Solicitante",
                    "email": "carlos@helpdesk.com",
                    "password": "segredo123",
                    "role": "REQUESTER"
                }
                """;
            mockMvc.perform(post("/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requesterJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Carlos Solicitante")))
                    .andExpect(jsonPath("$.email", is("carlos@helpdesk.com")))
                    .andExpect(jsonPath("$.role", is("REQUESTER")))
                    .andExpect(jsonPath("$.password").doesNotExist());

            String techJson = """
                {
                    "name": "Ana Tecnica",
                    "email": "ana@helpdesk.com",
                    "password": "segredo456",
                    "role": "TECHNICIAN"
                }
                """;
            mockMvc.perform(post("/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(techJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Ana Tecnica")))
                    .andExpect(jsonPath("$.email", is("ana@helpdesk.com")))
                    .andExpect(jsonPath("$.role", is("TECHNICIAN")))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("2. Listar usuários cadastrados sem expor senhas")
        void cenario2_listarUsuarios() throws Exception {
            saveUser("Usuario 1", "u1@helpdesk.com", UserRole.REQUESTER);
            saveUser("Usuario 2", "u2@helpdesk.com", UserRole.TECHNICIAN);

            mockMvc.perform(get("/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].password").doesNotExist())
                    .andExpect(jsonPath("$[1].password").doesNotExist());
        }

        @Test
        @DisplayName("3. Criar ativo IN_USE com sucesso")
        void cenario3_criarAtivoInUse() throws Exception {
            AssetRequestDTO dto = new AssetRequestDTO("NOTE-101", "Notebook", "Dell Latitude 3420", AssetStatus.IN_USE);

            mockMvc.perform(post("/assets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code", is("NOTE-101")))
                    .andExpect(jsonPath("$.type", is("Notebook")))
                    .andExpect(jsonPath("$.status", is("IN_USE")));
        }

        @Test
        @DisplayName("4. Criar ativo INACTIVE com sucesso")
        void cenario4_criarAtivoInactive() throws Exception {
            AssetRequestDTO dto = new AssetRequestDTO("MON-202", "Monitor", "LG Ultrawide", AssetStatus.INACTIVE);

            mockMvc.perform(post("/assets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code", is("MON-202")))
                    .andExpect(jsonPath("$.status", is("INACTIVE")));
        }

        @Test
        @DisplayName("5. Listar todos os ativos cadastrados")
        void cenario5_listarAtivos() throws Exception {
            saveAsset("NOTE-001", "Notebook", AssetStatus.IN_USE);
            saveAsset("MON-001", "Monitor", AssetStatus.INACTIVE);

            mockMvc.perform(get("/assets"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("6. Criar ticket INCIDENTE_SISTEMA com prioridade ALTA e status CRIADO")
        void cenario6_criarTicketIncidenteSistema() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            TicketRequestDTO dto = new TicketRequestDTO("ERP Fora do ar", "Banco fora", TicketCategory.INCIDENTE_SISTEMA, requester.getId(), null);

            mockMvc.perform(post("/tickets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status", is("CRIADO")))
                    .andExpect(jsonPath("$.priority", is("ALTA")))
                    .andExpect(jsonPath("$.category", is("INCIDENTE_SISTEMA")))
                    .andExpect(jsonPath("$.requesterId", is(requester.getId().intValue())));
        }

        @Test
        @DisplayName("7. Criar ticket INCIDENTE_EQUIPAMENTO com ativo IN_USE, prioridade MEDIA e status CRIADO")
        void cenario7_criarTicketIncidenteEquipamento() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            Asset asset = saveAsset("NOTE-303", "Notebook", AssetStatus.IN_USE);
            TicketRequestDTO dto = new TicketRequestDTO("Tela quebrada", "Display quebrou", TicketCategory.INCIDENTE_EQUIPAMENTO, requester.getId(), asset.getId());

            mockMvc.perform(post("/tickets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status", is("CRIADO")))
                    .andExpect(jsonPath("$.priority", is("MEDIA")))
                    .andExpect(jsonPath("$.assetId", is(asset.getId().intValue())))
                    .andExpect(jsonPath("$.assetCode", is("NOTE-303")));
        }

        @Test
        @DisplayName("8. Criar ticket DUVIDA com prioridade BAIXA e status CRIADO")
        void cenario8_criarTicketDuvida() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            TicketRequestDTO dto = new TicketRequestDTO("Como usar VPN", "Duvida sobre token", TicketCategory.DUVIDA, requester.getId(), null);

            mockMvc.perform(post("/tickets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status", is("CRIADO")))
                    .andExpect(jsonPath("$.priority", is("BAIXA")));
        }

        @Test
        @DisplayName("9. Listar todos os tickets")
        void cenario9_listarTodosOsTickets() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            saveTicket("T1", "Desc 1", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.CRIADO);
            saveTicket("T2", "Desc 2", TicketCategory.INCIDENTE_SISTEMA, TicketPriority.ALTA, requester, null, TicketStatus.CRIADO);

            mockMvc.perform(get("/tickets"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("10. Buscar ticket por ID")
        void cenario10_buscarTicketPorId() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            Ticket ticket = saveTicket("Ticket Unico", "Descricao detalhada", TicketCategory.SOLICITACAO_ACESSO, TicketPriority.MEDIA, requester, null, TicketStatus.CRIADO);

            mockMvc.perform(get("/tickets/" + ticket.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(ticket.getId().intValue())))
                    .andExpect(jsonPath("$.title", is("Ticket Unico")));
        }

        @Test
        @DisplayName("11. Listar tickets por solicitante")
        void cenario11_listarTicketsPorSolicitante() throws Exception {
            User requester1 = saveUser("Solicitante 1", "req1@helpdesk.com", UserRole.REQUESTER);
            User requester2 = saveUser("Solicitante 2", "req2@helpdesk.com", UserRole.REQUESTER);

            saveTicket("Ticket R1 A", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester1, null, TicketStatus.CRIADO);
            saveTicket("Ticket R1 B", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester1, null, TicketStatus.CRIADO);
            saveTicket("Ticket R2 A", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester2, null, TicketStatus.CRIADO);

            mockMvc.perform(get("/tickets/requester/" + requester1.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].requesterId", is(requester1.getId().intValue())))
                    .andExpect(jsonPath("$[1].requesterId", is(requester1.getId().intValue())));
        }

        @Test
        @DisplayName("12. Atribuir TECHNICIAN a ticket CRIADO e confirmar transição automática para ABERTO")
        void cenario12_atribuirTecnico_TransicaoParaAberto() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            User technician = saveUser("Tecnico", "tech@helpdesk.com", UserRole.TECHNICIAN);
            Ticket ticket = saveTicket("Chamado", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.CRIADO);

            TicketAssignTechnicianDTO assignDTO = new TicketAssignTechnicianDTO(technician.getId());

            mockMvc.perform(patch("/tickets/" + ticket.getId() + "/technician")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(assignDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.technicianId", is(technician.getId().intValue())))
                    .andExpect(jsonPath("$.technicianName", is("Tecnico")))
                    .andExpect(jsonPath("$.status", is("ABERTO")));
        }

        @Test
        @DisplayName("13. Mudar status de ABERTO para EM_ATENDIMENTO")
        void cenario13_mudarParaEmAtendimento() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            Ticket ticket = saveTicket("Chamado", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.ABERTO);

            TicketStatusUpdateDTO statusDTO = new TicketStatusUpdateDTO(TicketStatus.EM_ATENDIMENTO, null);

            mockMvc.perform(patch("/tickets/" + ticket.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("EM_ATENDIMENTO")));
        }

        @Test
        @DisplayName("14. Mudar status de EM_ATENDIMENTO para RESOLVIDO com solution preenchida")
        void cenario14_mudarParaResolvidoComSolution() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            Ticket ticket = saveTicket("Chamado", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.EM_ATENDIMENTO);

            TicketStatusUpdateDTO statusDTO = new TicketStatusUpdateDTO(TicketStatus.RESOLVIDO, "Resolvido com reinicialização do roteador");

            mockMvc.perform(patch("/tickets/" + ticket.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("RESOLVIDO")))
                    .andExpect(jsonPath("$.solution", is("Resolvido com reinicialização do roteador")));
        }

        @Test
        @DisplayName("15. Mudar de RESOLVIDO para FECHADO com sucesso")
        void cenario15_mudarDeResolvidoParaFechado() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            Ticket ticket = saveTicket("Chamado", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.RESOLVIDO);
            ticket.setSolution("Solução já validada");
            ticketRepository.save(ticket);

            TicketStatusUpdateDTO statusDTO = new TicketStatusUpdateDTO(TicketStatus.FECHADO, "Solução já validada");

            mockMvc.perform(patch("/tickets/" + ticket.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("FECHADO")));
        }

        @Test
        @DisplayName("16. Cancelar ticket com status CRIADO ou ABERTO")
        void cenario16_cancelarTicketCriadoOuAberto() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            Ticket ticketCriado = saveTicket("T Criado", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.CRIADO);
            Ticket ticketAberto = saveTicket("T Aberto", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.ABERTO);

            TicketStatusUpdateDTO cancelDTO = new TicketStatusUpdateDTO(TicketStatus.CANCELADO, null);

            mockMvc.perform(patch("/tickets/" + ticketCriado.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cancelDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CANCELADO")));

            mockMvc.perform(patch("/tickets/" + ticketAberto.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cancelDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CANCELADO")));
        }
    }

    @Nested
    @DisplayName("Cenários de Erro e Regras de Negócio")
    class ErrorScenarios {

        @Test
        @DisplayName("1. Criar INCIDENTE_EQUIPAMENTO sem assetId deve retornar 400 Bad Request")
        void erro1_incidenteEquipamentoSemAssetId() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            TicketRequestDTO dto = new TicketRequestDTO("Teclado quebrado", "Tecla nao responde", TicketCategory.INCIDENTE_EQUIPAMENTO, requester.getId(), null);

            mockMvc.perform(post("/tickets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", is("Para a categoria INCIDENTE_EQUIPAMENTO, o envio do ativo (assetId) é obrigatório.")));
        }

        @Test
        @DisplayName("2. Criar INCIDENTE_EQUIPAMENTO usando asset INACTIVE deve retornar 400 Bad Request")
        void erro2_incidenteEquipamentoAssetInactive() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            Asset inactiveAsset = saveAsset("PC-999", "Desktop", AssetStatus.INACTIVE);
            TicketRequestDTO dto = new TicketRequestDTO("PC com tela preta", "Nao da video", TicketCategory.INCIDENTE_EQUIPAMENTO, requester.getId(), inactiveAsset.getId());

            mockMvc.perform(post("/tickets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", is("Somente ativos com status IN_USE podem ser vinculados a chamados.")));
        }

        @Test
        @DisplayName("3. Criar ticket com requester que não tenha papel REQUESTER deve retornar 400 Bad Request")
        void erro3_requesterNaoTemPapelRequester() throws Exception {
            User adminUser = saveUser("Admin", "admin@helpdesk.com", UserRole.ADMIN);
            TicketRequestDTO dto = new TicketRequestDTO("Duvida", "Desc", TicketCategory.DUVIDA, adminUser.getId(), null);

            mockMvc.perform(post("/tickets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", is("O solicitante deve ter o papel REQUESTER.")));
        }

        @Test
        @DisplayName("4. Atribuir usuário que não tenha papel TECHNICIAN deve retornar 400 Bad Request")
        void erro4_atribuirUsuarioNaoTechnician() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            User nonTechUser = saveUser("Outro Solicitante", "outro@helpdesk.com", UserRole.REQUESTER);
            Ticket ticket = saveTicket("Chamado", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.CRIADO);

            TicketAssignTechnicianDTO assignDTO = new TicketAssignTechnicianDTO(nonTechUser.getId());

            mockMvc.perform(patch("/tickets/" + ticket.getId() + "/technician")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(assignDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", is("Apenas usuários com papel TECHNICIAN podem ser atribuídos como técnico.")));
        }

        @Test
        @DisplayName("5. Atribuir segundo técnico a ticket que já possui técnico deve retornar 400 Bad Request")
        void erro5_atribuirSegundoTecnico() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            User tech1 = saveUser("Tecnico 1", "tech1@helpdesk.com", UserRole.TECHNICIAN);
            User tech2 = saveUser("Tecnico 2", "tech2@helpdesk.com", UserRole.TECHNICIAN);

            Ticket ticket = saveTicket("Chamado", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.ABERTO);
            ticket.setTechnician(tech1);
            ticketRepository.save(ticket);

            TicketAssignTechnicianDTO assignDTO = new TicketAssignTechnicianDTO(tech2.getId());

            mockMvc.perform(patch("/tickets/" + ticket.getId() + "/technician")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(assignDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", is("O chamado já possui um técnico atribuído e não pode ser reatribuído.")));
        }

        @Test
        @DisplayName("6. Mudar para RESOLVIDO sem solution deve retornar 400 Bad Request")
        void erro6_mudarParaResolvidoSemSolution() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            Ticket ticket = saveTicket("Chamado", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.EM_ATENDIMENTO);

            TicketStatusUpdateDTO statusDTO = new TicketStatusUpdateDTO(TicketStatus.RESOLVIDO, "   ");

            mockMvc.perform(patch("/tickets/" + ticket.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", is("Para transitar o status para RESOLVIDO, o campo solução é obrigatório.")));
        }

        @Test
        @DisplayName("7. Mudar diretamente para FECHADO sem RESOLVIDO deve retornar 400 Bad Request")
        void erro7_mudarParaFechadoSemResolvido() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            Ticket ticket = saveTicket("Chamado", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.ABERTO);

            TicketStatusUpdateDTO statusDTO = new TicketStatusUpdateDTO(TicketStatus.FECHADO, "Tentando fechar direto");

            mockMvc.perform(patch("/tickets/" + ticket.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", is("O chamado só pode ser alterado para FECHADO se o status atual for RESOLVIDO.")));
        }

        @Test
        @DisplayName("8. Cancelar ticket RESOLVIDO ou FECHADO deve retornar 400 Bad Request")
        void erro8_cancelarTicketResolvidoOuFechado() throws Exception {
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            Ticket ticketResolvido = saveTicket("Chamado Resolvido", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.RESOLVIDO);
            ticketResolvido.setSolution("Solucao ok");
            ticketRepository.save(ticketResolvido);

            TicketStatusUpdateDTO cancelDTO = new TicketStatusUpdateDTO(TicketStatus.CANCELADO, null);

            mockMvc.perform(patch("/tickets/" + ticketResolvido.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cancelDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", is("Não é permitido cancelar um chamado que já está com status RESOLVIDO.")));
        }

        @Test
        @DisplayName("9. Buscar ticket inexistente deve retornar 404 Not Found")
        void erro9_buscarTicketInexistente() throws Exception {
            mockMvc.perform(get("/tickets/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.message", is("Chamado não encontrado com o id: 999999")));
        }

        @Test
        @DisplayName("10. Usar requester, technician ou asset inexistente deve retornar 404 Not Found")
        void erro10_usarEntidadesInexistentes() throws Exception {
            // Requester inexistente na criação
            TicketRequestDTO reqInexistente = new TicketRequestDTO("Titulo", "Desc", TicketCategory.DUVIDA, 999999L, null);
            mockMvc.perform(post("/tickets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqInexistente)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.message", is("Solicitante não encontrado com o id: 999999")));

            // Asset inexistente na criação
            User requester = saveUser("Solicitante", "req@helpdesk.com", UserRole.REQUESTER);
            TicketRequestDTO assetInexistente = new TicketRequestDTO("Titulo", "Desc", TicketCategory.INCIDENTE_EQUIPAMENTO, requester.getId(), 999999L);
            mockMvc.perform(post("/tickets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(assetInexistente)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.message", is("Ativo não encontrado com o id: 999999")));

            // Technician inexistente na atribuição
            Ticket ticket = saveTicket("Chamado", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null, TicketStatus.CRIADO);
            TicketAssignTechnicianDTO techInexistente = new TicketAssignTechnicianDTO(999999L);
            mockMvc.perform(patch("/tickets/" + ticket.getId() + "/technician")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(techInexistente)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.message", is("Técnico não encontrado com o id: 999999")));

            // Requester inexistente na consulta de tickets por solicitante
            mockMvc.perform(get("/tickets/requester/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.message", is("Solicitante não encontrado com o id: 999999")));
        }
    }
}
