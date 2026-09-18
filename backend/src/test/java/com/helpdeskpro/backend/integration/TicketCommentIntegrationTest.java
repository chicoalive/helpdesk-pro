package com.helpdeskpro.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.TicketCommentRequestDTO;
import com.helpdeskpro.backend.repositories.TicketCommentRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Testes de Integração - Módulo de Comentários de Chamados (Etapa 5)")
public class TicketCommentIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    private Long dynamicUserId;
    private Long dynamicTicketId;
    private String dynamicUserName;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Limpeza dos repositórios
        ticketCommentRepository.deleteAll();
        ticketRepository.deleteAll();
        userRepository.deleteAll();

        // Geração dinâmica de registros para teste
        User user = userRepository.save(new User("Carlos Solicitante", "carlos.comentarios@helpdesk.com", "segredo123", UserRole.REQUESTER));
        Ticket ticket = ticketRepository.save(new Ticket(
                "Problema no Sistema ERP",
                "Erro ao emitir relatórios mensais",
                TicketCategory.INCIDENTE_SISTEMA,
                TicketPriority.ALTA,
                user,
                null
        ));

        this.dynamicUserId = user.getId();
        this.dynamicTicketId = ticket.getId();
        this.dynamicUserName = user.getName();
    }

    @Nested
    @DisplayName("Cenários de Sucesso")
    class SuccessScenarios {

        @Test
        @DisplayName("POST /tickets/{id}/comments - Adicionar comentário com sucesso deve retornar 201 Created")
        void cenario1_adicionarComentario_Sucesso() throws Exception {
            TicketCommentRequestDTO dto = new TicketCommentRequestDTO("Primeiro comentário sobre o chamado.", dynamicUserId);

            mockMvc.perform(post("/tickets/" + dynamicTicketId + "/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.content", is("Primeiro comentário sobre o chamado.")))
                    .andExpect(jsonPath("$.ticketId", is(dynamicTicketId.intValue())))
                    .andExpect(jsonPath("$.authorId", is(dynamicUserId.intValue())))
                    .andExpect(jsonPath("$.authorName", is(dynamicUserName)))
                    .andExpect(jsonPath("$.authorRole", is("REQUESTER")))
                    .andExpect(jsonPath("$.createdAt", notNullValue()))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("POST /tickets/{id}/comments - Adicionar comentário em ticket com status FECHADO (status livre) deve retornar 201 Created")
        void cenario2_adicionarComentario_TicketFechado_Sucesso() throws Exception {
            Ticket ticket = ticketRepository.findById(dynamicTicketId).orElseThrow();
            ticket.setStatus(TicketStatus.FECHADO);
            ticketRepository.save(ticket);

            TicketCommentRequestDTO dto = new TicketCommentRequestDTO("Comentário em chamado já fechado.", dynamicUserId);

            mockMvc.perform(post("/tickets/" + dynamicTicketId + "/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.content", is("Comentário em chamado já fechado.")));
        }

        @Test
        @DisplayName("GET /tickets/{id}/comments - Listar histórico de comentários deve retornar 200 OK e lista ordenada")
        void cenario3_listarComentarios_Sucesso() throws Exception {
            TicketCommentRequestDTO dto1 = new TicketCommentRequestDTO("Primeiro comentário", dynamicUserId);
            TicketCommentRequestDTO dto2 = new TicketCommentRequestDTO("Segundo comentário", dynamicUserId);

            mockMvc.perform(post("/tickets/" + dynamicTicketId + "/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto1)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/tickets/" + dynamicTicketId + "/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto2)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/tickets/" + dynamicTicketId + "/comments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].content", is("Primeiro comentário")))
                    .andExpect(jsonPath("$[1].content", is("Segundo comentário")))
                    .andExpect(jsonPath("$[0].password").doesNotExist())
                    .andExpect(jsonPath("$[1].password").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Cenários de Erro e Validações")
    class ErrorScenarios {

        @Test
        @DisplayName("POST /tickets/{id}/comments - Conteúdo vazio ou em branco deve retornar 400 Bad Request")
        void erro1_conteudoVazio_Retorna400() throws Exception {
            TicketCommentRequestDTO dto = new TicketCommentRequestDTO("   ", dynamicUserId);

            mockMvc.perform(post("/tickets/" + dynamicTicketId + "/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)));
        }

        @Test
        @DisplayName("POST /tickets/{id}/comments - Autor (userId) nulo deve retornar 400 Bad Request")
        void erro2_autorNulo_Retorna400() throws Exception {
            TicketCommentRequestDTO dto = new TicketCommentRequestDTO("Conteúdo válido", null);

            mockMvc.perform(post("/tickets/" + dynamicTicketId + "/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)));
        }

        @Test
        @DisplayName("POST /tickets/{id}/comments - Ticket inexistente deve retornar 404 Not Found")
        void erro3_ticketInexistente_Retorna404() throws Exception {
            Long nonExistentTicketId = dynamicTicketId + 9999L;
            TicketCommentRequestDTO dto = new TicketCommentRequestDTO("Comentário para ticket inexistente", dynamicUserId);

            mockMvc.perform(post("/tickets/" + nonExistentTicketId + "/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)));
        }

        @Test
        @DisplayName("POST /tickets/{id}/comments - Autor (userId) inexistente deve retornar 404 Not Found")
        void erro4_autorInexistente_Retorna404() throws Exception {
            Long nonExistentUserId = dynamicUserId + 9999L;
            TicketCommentRequestDTO dto = new TicketCommentRequestDTO("Comentário de autor inexistente", nonExistentUserId);

            mockMvc.perform(post("/tickets/" + dynamicTicketId + "/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)));
        }

        @Test
        @DisplayName("GET /tickets/{id}/comments - Listar comentários de ticket inexistente deve retornar 404 Not Found")
        void erro5_listarComentarios_TicketInexistente_Retorna404() throws Exception {
            Long nonExistentTicketId = dynamicTicketId + 9999L;

            mockMvc.perform(get("/tickets/" + nonExistentTicketId + "/comments"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)));
        }
    }
}
