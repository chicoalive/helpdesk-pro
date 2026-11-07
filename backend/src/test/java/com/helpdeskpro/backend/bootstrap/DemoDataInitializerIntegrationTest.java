package com.helpdeskpro.backend.bootstrap;

import com.helpdeskpro.backend.domain.AssetStatus;
import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.DashboardResponseDTO;
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketAuditLogRepository;
import com.helpdeskpro.backend.repositories.TicketCommentRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.services.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("Testes de Integração - DemoDataInitializer")
class DemoDataInitializerIntegrationTest {

    @Autowired
    private DemoDataInitializer demoDataInitializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAuditLogRepository ticketAuditLogRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        ticketAuditLogRepository.deleteAll();
        ticketCommentRepository.deleteAll();
        ticketRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Não deve executar seed automaticamente quando app.seed.demo-enabled=false")
    void naoDeveExecutarAutomaticamenteQuandoDesabilitado() {
        demoDataInitializer.run();

        assertEquals(0, userRepository.count());
        assertEquals(0, assetRepository.count());
        assertEquals(0, ticketRepository.count());
    }

    @Test
    @DisplayName("Deve popular usuários, ativos, chamados e auditorias de demonstração cobrindo o Dashboard")
    void devePopularDadosDeDemonstracaoComSucesso() {
        demoDataInitializer.seedDemoData();

        assertEquals(3, userRepository.count(), "Deve garantir 3 usuários (ADMIN, TECHNICIAN, REQUESTER)");
        assertEquals(4, assetRepository.count(), "Deve criar os 4 ativos de demonstração");
        assertEquals(3, assetRepository.countByStatus(AssetStatus.IN_USE));
        assertEquals(1, assetRepository.countByStatus(AssetStatus.UNDER_MAINTENANCE));

        assertEquals(7, ticketRepository.count(), "Deve criar os 7 chamados de demonstração em banco limpo");
        assertTrue(ticketAuditLogRepository.count() >= 15, "Deve gerar registros reais de auditoria pelas transições");

        DashboardResponseDTO summary = dashboardService.getSummary();
        assertTrue(summary.getTicketsByStatus().get(TicketStatus.CRIADO) >= 1);
        assertTrue(summary.getTicketsByStatus().get(TicketStatus.ABERTO) >= 1);
        assertTrue(summary.getTicketsByStatus().get(TicketStatus.EM_ATENDIMENTO) >= 1);
        assertTrue(summary.getTicketsByStatus().get(TicketStatus.RESOLVIDO) >= 1);
        assertTrue(summary.getTicketsByStatus().get(TicketStatus.FECHADO) >= 1);

        assertTrue(summary.getTicketsByPriority().get(TicketPriority.ALTA) >= 1);
        assertTrue(summary.getTicketsByPriority().get(TicketPriority.MEDIA) >= 1);
        assertTrue(summary.getTicketsByPriority().get(TicketPriority.BAIXA) >= 1);

        assertTrue(summary.getUnassignedTickets() >= 1);
        assertEquals(1, summary.getAssetsUnderMaintenance());
        assertEquals(1, summary.getTicketsByTechnician().size());

        var ticketWithAttachment = ticketRepository.findFirstByTitleOrderByIdAsc("Notebook apresentando falha de inicialização");
        assertTrue(ticketWithAttachment.isPresent());
        assertEquals("evidencia-falha-inicializacao.png", ticketWithAttachment.get().getAttachmentName());
        assertEquals("image/png", ticketWithAttachment.get().getAttachmentContentType());
        assertTrue(ticketWithAttachment.get().getAttachmentData() != null && ticketWithAttachment.get().getAttachmentData().length > 0);
        assertTrue(ticketWithAttachment.get().getAttachmentData().length <= 2L * 1024 * 1024);
    }

    @Test
    @DisplayName("Deve ser totalmente idempotente e não duplicar dados ao executar novamente")
    void naoDeveDuplicarDadosAoReexecutar() {
        demoDataInitializer.seedDemoData();

        long usersAfterFirstRun = userRepository.count();
        long assetsAfterFirstRun = assetRepository.count();
        long ticketsAfterFirstRun = ticketRepository.count();
        long auditsAfterFirstRun = ticketAuditLogRepository.count();

        demoDataInitializer.seedDemoData();

        assertEquals(usersAfterFirstRun, userRepository.count(), "Usuários não devem ser duplicados");
        assertEquals(assetsAfterFirstRun, assetRepository.count(), "Ativos não devem ser duplicados");
        assertEquals(ticketsAfterFirstRun, ticketRepository.count(), "Chamados não devem ser duplicados");
        assertEquals(auditsAfterFirstRun, ticketAuditLogRepository.count(), "Auditorias não devem ser duplicadas");
    }

    @Test
    @DisplayName("Deve restaurar papel e senha das contas de demonstração caso tenham sido alterados (R5)")
    void deveRestaurarContasDeDemonstracaoSeAlteradas() {
        demoDataInitializer.seedDemoData();

        User ana = userRepository.findByEmail("ana@helpdesk.com").orElseThrow();
        ana.setRole(UserRole.REQUESTER);
        ana.setPassword(passwordEncoder.encode("senha-alterada"));
        userRepository.save(ana);

        demoDataInitializer.seedDemoData();

        User restoredAna = userRepository.findByEmail("ana@helpdesk.com").orElseThrow();
        assertEquals(UserRole.TECHNICIAN, restoredAna.getRole());
        assertTrue(passwordEncoder.matches("123", restoredAna.getPassword()));
    }
}
