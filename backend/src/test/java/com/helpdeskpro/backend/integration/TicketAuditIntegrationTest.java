package com.helpdeskpro.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.entities.TicketAuditLog;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.TicketAssignTechnicianDTO;
import com.helpdeskpro.backend.dto.TicketStatusUpdateDTO;
import com.helpdeskpro.backend.repositories.TicketAuditLogRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketAuditIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TicketAuditLogRepository ticketAuditLogRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;
    private User admin;
    private User technician;
    private User requester;
    private Ticket ticket;
    private String adminToken;
    private String technicianToken;
    private String requesterToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ticketAuditLogRepository.deleteAll();
        ticketRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(createUser("Admin Auditoria", "audit.admin@test.local", UserRole.ADMIN));
        technician = userRepository.save(createUser("Tecnico Auditoria", "audit.tech@test.local", UserRole.TECHNICIAN));
        requester = userRepository.save(createUser("Solicitante Auditoria", "audit.requester@test.local", UserRole.REQUESTER));
        ticket = ticketRepository.save(new Ticket(
                "Ticket para auditoria",
                "Descricao",
                TicketCategory.DUVIDA,
                TicketPriority.BAIXA,
                requester,
                null
        ));

        adminToken = tokenService.generateToken(admin);
        technicianToken = tokenService.generateToken(technician);
        requesterToken = tokenService.generateToken(requester);
    }

    @Test
    void assigningFirstTechnicianAuditsTechnicianAndAutomaticStatusInSameOperation() throws Exception {
        mockMvc.perform(patch("/tickets/{id}/technician", ticket.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketAssignTechnicianDTO(technician.getId()))))
                .andExpect(status().isOk());

        List<TicketAuditLog> entries = ticketAuditLogRepository.findByTicketIdOrderByChangedAtAsc(ticket.getId());
        assertEquals(2, entries.size());
        assertEquals("technician", entries.get(0).getFieldName());
        assertNull(entries.get(0).getOldValue());
        assertEquals(technician.getId().toString(), entries.get(0).getNewValue());
        assertEquals("status", entries.get(1).getFieldName());
        assertEquals("CRIADO", entries.get(1).getOldValue());
        assertEquals("ABERTO", entries.get(1).getNewValue());
        assertEquals(admin.getId(), entries.get(0).getChangedBy().getId());
        assertNotNull(entries.get(0).getChangedAt());
        assertEquals(TicketStatus.ABERTO, ticketRepository.findById(ticket.getId()).orElseThrow().getStatus());
    }

    @Test
    void statusOnlyChangeCreatesOneAuditEntry() throws Exception {
        ticket.setStatus(TicketStatus.ABERTO);
        ticket.setTechnician(technician);
        ticketRepository.save(ticket);

        updateStatus(TicketStatus.EM_ATENDIMENTO, null);

        List<TicketAuditLog> entries = ticketAuditLogRepository.findByTicketIdOrderByChangedAtAsc(ticket.getId());
        assertEquals(1, entries.size());
        assertEquals("status", entries.get(0).getFieldName());
        assertEquals("ABERTO", entries.get(0).getOldValue());
        assertEquals("EM_ATENDIMENTO", entries.get(0).getNewValue());
    }

    @Test
    void statusAndSolutionChangesCreateSeparateEntriesWithAuthenticatedActor() throws Exception {
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);
        ticket.setTechnician(technician);
        ticketRepository.save(ticket);

        updateStatus(TicketStatus.RESOLVIDO, "  Solucao aplicada  ");

        List<TicketAuditLog> entries = ticketAuditLogRepository.findByTicketIdOrderByChangedAtAsc(ticket.getId());
        assertEquals(2, entries.size());
        assertEquals(technician.getId(), entries.get(0).getChangedBy().getId());
        assertEquals("solution", entries.get(0).getFieldName());
        assertNull(entries.get(0).getOldValue());
        assertEquals("Solucao aplicada", entries.get(0).getNewValue());
        assertEquals("status", entries.get(1).getFieldName());
        assertEquals("EM_ATENDIMENTO", entries.get(1).getOldValue());
        assertEquals("RESOLVIDO", entries.get(1).getNewValue());
        assertNotNull(entries.get(0).getChangedAt());
        assertNotNull(entries.get(1).getChangedAt());
    }

    @Test
    void solutionOnlyChangeCreatesAnEntry() throws Exception {
        ticket.setStatus(TicketStatus.RESOLVIDO);
        ticket.setTechnician(technician);
        ticket.setSolution("Solucao anterior");
        ticketRepository.save(ticket);

        updateStatus(TicketStatus.RESOLVIDO, "Solucao nova");

        List<TicketAuditLog> entries = ticketAuditLogRepository.findByTicketIdOrderByChangedAtAsc(ticket.getId());
        assertEquals(1, entries.size());
        assertEquals("solution", entries.get(0).getFieldName());
        assertEquals("Solucao anterior", entries.get(0).getOldValue());
        assertEquals("Solucao nova", entries.get(0).getNewValue());
    }

    @Test
    void unchangedValuesDoNotCreateAuditEntries() throws Exception {
        ticket.setStatus(TicketStatus.RESOLVIDO);
        ticket.setTechnician(technician);
        ticket.setSolution("Solucao existente");
        ticketRepository.save(ticket);

        updateStatus(TicketStatus.RESOLVIDO, "Solucao existente");

        assertEquals(0, ticketAuditLogRepository.findByTicketIdOrderByChangedAtAsc(ticket.getId()).size());
    }

    @Test
    void auditEndpointReturnsChronologicalEntriesWithoutSensitiveUserData() throws Exception {
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);
        ticket.setTechnician(technician);
        ticketRepository.save(ticket);
        updateStatus(TicketStatus.RESOLVIDO, "Resolvido");

        mockMvc.perform(get("/tickets/{id}/audit", ticket.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fieldName", is("solution")))
                .andExpect(jsonPath("$[0].changedById", is(technician.getId().intValue())))
                .andExpect(jsonPath("$[0].changedByName", is(technician.getName())))
                .andExpect(jsonPath("$[0].changedByEmail", is(technician.getEmail())))
                .andExpect(jsonPath("$[0].changedAt", notNullValue()))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].fieldName", is("status")));

        List<TicketAuditLog> entries = ticketAuditLogRepository.findByTicketIdOrderByChangedAtAsc(ticket.getId());
        assertEquals(true, !entries.get(0).getChangedAt().isAfter(entries.get(1).getChangedAt()));
    }

    @Test
    void adminAndTechnicianCanReadAuditAndRequesterCannot() throws Exception {
        mockMvc.perform(get("/tickets/{id}/audit", ticket.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tickets/{id}/audit", ticket.getId())
                        .header("Authorization", bearer(technicianToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tickets/{id}/audit", ticket.getId())
                        .header("Authorization", bearer(requesterToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditEndpointRequiresAuthenticationAndExistingTicket() throws Exception {
        mockMvc.perform(get("/tickets/{id}/audit", ticket.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/tickets/{id}/audit", ticket.getId() + 9999)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    private void updateStatus(TicketStatus status, String solution) throws Exception {
        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(status, solution);
        mockMvc.perform(patch("/tickets/{id}/status", ticket.getId())
                        .header("Authorization", bearer(technicianToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    private User createUser(String name, String email, UserRole role) {
        return new User(name, email, passwordEncoder.encode("audit-test-password"), role);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
