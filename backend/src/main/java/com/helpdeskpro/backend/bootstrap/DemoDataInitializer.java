package com.helpdeskpro.backend.bootstrap;

import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.domain.AssetStatus;
import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.TicketAssignTechnicianDTO;
import com.helpdeskpro.backend.dto.TicketRequestDTO;
import com.helpdeskpro.backend.dto.TicketResponseDTO;
import com.helpdeskpro.backend.dto.TicketStatusUpdateDTO;
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.services.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

@Component
@Order(2)
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private static final String ADMIN_EMAIL = "admin@helpdesk.com";
    private static final String TECH_EMAIL = "ana@helpdesk.com";
    private static final String REQUESTER_EMAIL = "carlos@helpdesk.com";
    private static final String DEFAULT_DEMO_PASSWORD = "123";

    private static final String DEMO_ATTACHMENT_RESOURCE = "demo/evidencia-falha-inicializacao.png";
    private static final String DEMO_ATTACHMENT_NAME = "evidencia-falha-inicializacao.png";
    private static final String DEMO_ATTACHMENT_CONTENT_TYPE = "image/png";
    private static final long MAX_ATTACHMENT_SIZE_BYTES = 2L * 1024 * 1024;

    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(
            TicketStatus.CRIADO,
            TicketStatus.ABERTO,
            TicketStatus.EM_ATENDIMENTO,
            TicketStatus.AGUARDANDO_USUARIO
    );

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final PasswordEncoder passwordEncoder;
    private final boolean demoEnabled;

    public DemoDataInitializer(UserRepository userRepository,
                               AssetRepository assetRepository,
                               TicketRepository ticketRepository,
                               TicketService ticketService,
                               PasswordEncoder passwordEncoder,
                               @Value("${app.seed.demo-enabled:false}") boolean demoEnabled) {
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
        this.passwordEncoder = passwordEncoder;
        this.demoEnabled = demoEnabled;
    }

    @Override
    public void run(String... args) {
        if (!demoEnabled) {
            return;
        }
        seedDemoData();
    }

    public void seedDemoData() {
        log.info("Verificando dados de demonstração (seed idempotente)...");

        User admin = ensureUser("Administrador Sistema", ADMIN_EMAIL, UserRole.ADMIN);
        User technician = ensureUser("Ana Tecnica", TECH_EMAIL, UserRole.TECHNICIAN);
        User requester = ensureUser("Carlos Solicitante", REQUESTER_EMAIL, UserRole.REQUESTER);

        Asset notebookFinanceiro = ensureAsset(
                "NB-TI-001",
                "NOTEBOOK",
                "Notebook Dell Latitude 5440 - Financeiro",
                AssetStatus.IN_USE
        );
        Asset notebookComercial = ensureAsset(
                "NB-TI-002",
                "NOTEBOOK",
                "Notebook Lenovo ThinkPad T14 - Comercial",
                AssetStatus.IN_USE
        );
        ensureAsset(
                "MON-TI-003",
                "MONITOR",
                "Monitor Dell UltraSharp 24\" - Operações",
                AssetStatus.IN_USE
        );
        ensureAsset(
                "IMP-TI-004",
                "IMPRESSORA",
                "Impressora Multifuncional HP LaserJet Pro - 2º Andar",
                AssetStatus.UNDER_MAINTENANCE
        );

        seedDemoTickets(admin, technician, requester, notebookFinanceiro, notebookComercial);

        log.info("Verificação de dados de demonstração concluída.");
    }

    private User ensureUser(String name, String email, UserRole role) {
        return userRepository.findByEmail(email).map(existing -> {
            boolean updated = false;
            if (existing.getRole() != role) {
                existing.setRole(role);
                updated = true;
            }
            if (existing.getPassword() == null || !passwordEncoder.matches(DEFAULT_DEMO_PASSWORD, existing.getPassword())) {
                existing.setPassword(passwordEncoder.encode(DEFAULT_DEMO_PASSWORD));
                updated = true;
            }
            if (updated) {
                log.info("Conta de demonstração restaurada para estado íntegro: {} ({})", email, role);
                return userRepository.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
            User created = userRepository.save(new User(
                    name,
                    email,
                    passwordEncoder.encode(DEFAULT_DEMO_PASSWORD),
                    role
            ));
            log.info("Usuário de demonstração criado: {} ({})", email, role);
            return created;
        });
    }

    private Asset ensureAsset(String code, String type, String description, AssetStatus status) {
        return assetRepository.findByCode(code).orElseGet(() -> {
            Asset created = assetRepository.save(new Asset(code, type, description, status));
            log.info("Ativo de demonstração criado: {} ({})", code, status);
            return created;
        });
    }

    private void seedDemoTickets(User admin,
                                 User technician,
                                 User requester,
                                 Asset notebookFinanceiro,
                                 Asset notebookComercial) {
        try {
            // 1. INCIDENTE_SISTEMA (ALTA) -> RESOLVIDO (somente se não houver chamado ativo de sistema impedindo a criação)
            String resolvedSystemTitle = "Instabilidade no serviço de autenticação corporativa";
            if (!ticketRepository.existsByTitle(resolvedSystemTitle)
                    && !hasActiveTicketByCategory(requester.getId(), TicketCategory.INCIDENTE_SISTEMA)) {
                TicketResponseDTO created = createAsRequester(requester, new TicketRequestDTO(
                        resolvedSystemTitle,
                        "Usuários relataram lentidão e falha intermitente ao autenticar no portal interno durante o início do expediente.",
                        TicketCategory.INCIDENTE_SISTEMA,
                        null
                ));
                assignAsUser(admin, created.getId(), technician.getId());
                updateStatusAsUser(technician, created.getId(), TicketStatus.EM_ATENDIMENTO, null);
                updateStatusAsUser(
                        technician,
                        created.getId(),
                        TicketStatus.RESOLVIDO,
                        "Reiniciado o pool de conexões do serviço de autenticação e normalizado o tempo de resposta."
                );
            }

            // 2. INCIDENTE_SISTEMA (ALTA) -> CRIADO (sem técnico, se o solicitante ainda não tiver um ativo na categoria)
            String createdSystemTitle = "Sistema indisponível para acesso";
            if (!ticketRepository.existsByTitle(createdSystemTitle)
                    && !hasActiveTicketByCategory(requester.getId(), TicketCategory.INCIDENTE_SISTEMA)) {
                createAsRequester(requester, new TicketRequestDTO(
                        createdSystemTitle,
                        "Ao tentar acessar o módulo principal pela manhã, o sistema apresenta erro de tempo limite de conexão.",
                        TicketCategory.INCIDENTE_SISTEMA,
                        null
                ));
            }

            // 3. INCIDENTE_EQUIPAMENTO (MEDIA) -> FECHADO (com ativo NB-TI-001, anexo de imagem e ciclo completo de auditoria)
            String closedEquipTitle = "Notebook apresentando falha de inicialização";
            if (!ticketRepository.existsByTitle(closedEquipTitle)
                    && !hasActiveEquipmentTicket(requester.getId(), notebookFinanceiro.getId())) {
                TicketResponseDTO created = createAsRequester(requester, new TicketRequestDTO(
                        closedEquipTitle,
                        "Equipamento desliga sozinho logo após a tela inicial e apresenta alerta sonoro contínuo.",
                        TicketCategory.INCIDENTE_EQUIPAMENTO,
                        notebookFinanceiro.getId()
                ));
                ensureDemoAttachment(closedEquipTitle);
                assignAsUser(admin, created.getId(), technician.getId());
                updateStatusAsUser(technician, created.getId(), TicketStatus.EM_ATENDIMENTO, null);
                String solution = "Substituído módulo de memória RAM com defeito e validada a inicialização do sistema operacional.";
                updateStatusAsUser(technician, created.getId(), TicketStatus.RESOLVIDO, solution);
                updateStatusAsUser(admin, created.getId(), TicketStatus.FECHADO, solution);
            } else {
                ensureDemoAttachment(closedEquipTitle);
            }

            // 4. INCIDENTE_EQUIPAMENTO (MEDIA) -> EM_ATENDIMENTO (com ativo NB-TI-002)
            String inProgressEquipTitle = "Lentidão e travamentos frequentes na estação de trabalho";
            if (!ticketRepository.existsByTitle(inProgressEquipTitle)
                    && !hasActiveEquipmentTicket(requester.getId(), notebookComercial.getId())) {
                TicketResponseDTO created = createAsRequester(requester, new TicketRequestDTO(
                        inProgressEquipTitle,
                        "O notebook apresenta alto consumo de disco e congelamentos constantes ao abrir planilhas e navegador.",
                        TicketCategory.INCIDENTE_EQUIPAMENTO,
                        notebookComercial.getId()
                ));
                assignAsUser(admin, created.getId(), technician.getId());
                updateStatusAsUser(technician, created.getId(), TicketStatus.EM_ATENDIMENTO, null);
            }

            // 5. SOLICITACAO_ACESSO (MEDIA) -> ABERTO (atribuído à técnica)
            String openAccessTitle = "Solicitação de acesso ao módulo de relatórios gerenciais";
            if (!ticketRepository.existsByTitle(openAccessTitle)
                    && !hasActiveTicketByCategory(requester.getId(), TicketCategory.SOLICITACAO_ACESSO)) {
                TicketResponseDTO created = createAsRequester(requester, new TicketRequestDTO(
                        openAccessTitle,
                        "Necessito de permissão de leitura e exportação no painel de relatórios gerenciais do setor comercial.",
                        TicketCategory.SOLICITACAO_ACESSO,
                        null
                ));
                assignAsUser(admin, created.getId(), technician.getId());
            }

            // 6. DUVIDA (BAIXA) -> RESOLVIDO (com solução e auditoria)
            String resolvedQuestionTitle = "Dúvida sobre utilização do portal e acompanhamento de prazos";
            if (!ticketRepository.existsByTitle(resolvedQuestionTitle)) {
                TicketResponseDTO created = createAsRequester(requester, new TicketRequestDTO(
                        resolvedQuestionTitle,
                        "Gostaria de saber como anexar capturas de tela em um chamado e onde consultar o prazo estimado de atendimento.",
                        TicketCategory.DUVIDA,
                        null
                ));
                assignAsUser(admin, created.getId(), technician.getId());
                updateStatusAsUser(technician, created.getId(), TicketStatus.EM_ATENDIMENTO, null);
                updateStatusAsUser(
                        technician,
                        created.getId(),
                        TicketStatus.RESOLVIDO,
                        "Orientado o solicitante sobre o envio de imagens PNG/JPG e consulta do prazo estimado nos detalhes do chamado."
                );
            }

            // 7. DUVIDA (BAIXA) -> CRIADO (sem técnico atribuído)
            String createdQuestionTitle = "Como configurar assinatura padrão no e-mail corporativo";
            if (!ticketRepository.existsByTitle(createdQuestionTitle)) {
                createAsRequester(requester, new TicketRequestDTO(
                        createdQuestionTitle,
                        "Preciso do passo a passo atualizado para inserir a assinatura institucional com logotipo no webmail.",
                        TicketCategory.DUVIDA,
                        null
                ));
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void ensureDemoAttachment(String ticketTitle) {
        ticketRepository.findFirstByTitleOrderByIdAsc(ticketTitle).ifPresent(ticket -> {
            if (ticket.getAttachmentData() != null && ticket.getAttachmentData().length > 0) {
                return;
            }

            ClassPathResource resource = new ClassPathResource(DEMO_ATTACHMENT_RESOURCE);
            if (!resource.exists()) {
                log.warn("Imagem de demonstração não encontrada no classpath: {}", DEMO_ATTACHMENT_RESOURCE);
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                if (bytes.length == 0 || bytes.length > MAX_ATTACHMENT_SIZE_BYTES) {
                    log.warn("Imagem de demonstração inválida ou acima de 2 MB ({} bytes)", bytes.length);
                    return;
                }
                ticket.setAttachmentName(DEMO_ATTACHMENT_NAME);
                ticket.setAttachmentContentType(DEMO_ATTACHMENT_CONTENT_TYPE);
                ticket.setAttachmentData(bytes);
                ticketRepository.save(ticket);
                log.info("Anexo de demonstração '{}' ({} bytes) vinculado ao chamado #{}",
                        DEMO_ATTACHMENT_NAME, bytes.length, ticket.getId());
            } catch (IOException e) {
                log.warn("Não foi possível carregar a imagem de demonstração: {}", e.getMessage());
            }
        });
    }

    private boolean hasActiveTicketByCategory(Long requesterId, TicketCategory category) {
        return ticketRepository.findFirstByRequesterIdAndCategoryAndStatusInOrderByIdAsc(
                requesterId,
                category,
                ACTIVE_STATUSES
        ).isPresent();
    }

    private boolean hasActiveEquipmentTicket(Long requesterId, Long assetId) {
        return ticketRepository.findFirstByRequesterIdAndCategoryAndAssetIdAndStatusInOrderByIdAsc(
                requesterId,
                TicketCategory.INCIDENTE_EQUIPAMENTO,
                assetId,
                ACTIVE_STATUSES
        ).isPresent();
    }

    private TicketResponseDTO createAsRequester(User requester, TicketRequestDTO dto) {
        return executeAs(requester, () -> {
            TicketResponseDTO created = ticketService.createTicket(dto);
            log.info("Chamado de demonstração criado: #{} - {} ({})",
                    created.getId(), created.getTitle(), created.getPriority());
            return created;
        });
    }

    private void assignAsUser(User actor, Long ticketId, Long technicianId) {
        executeAs(actor, () -> {
            ticketRepository.flush();
            var request = new TicketAssignTechnicianDTO(technicianId);
            request.setExpectedVersion(ticketRepository.findById(ticketId).orElseThrow().getVersion());
            return ticketService.assignTechnician(ticketId, request);
        });
    }

    private void updateStatusAsUser(User actor, Long ticketId, TicketStatus status, String solution) {
        executeAs(actor, () -> ticketService.updateTicketStatus(
                ticketId,
                new TicketStatusUpdateDTO(status, solution)
        ));
    }

    private <T> T executeAs(User user, Supplier<T> action) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user,
                null,
                user.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
