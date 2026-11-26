package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.domain.AssetStatus;
import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.entities.TicketAuditLog;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.TicketAssignTechnicianDTO;
import com.helpdeskpro.backend.dto.TicketRequestDTO;
import com.helpdeskpro.backend.dto.TicketResponseDTO;
import com.helpdeskpro.backend.dto.TicketStatusUpdateDTO;
import com.helpdeskpro.backend.exceptions.AssetNotFoundException;
import com.helpdeskpro.backend.exceptions.BusinessRuleException;
import com.helpdeskpro.backend.exceptions.TicketNotFoundException;
import com.helpdeskpro.backend.exceptions.UserNotFoundException;
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.TicketAuditLogRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class TicketService {

    private static final long MAX_ATTACHMENT_SIZE_BYTES = 2L * 1024 * 1024;
    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(
            TicketStatus.CRIADO,
            TicketStatus.ABERTO,
            TicketStatus.EM_ATENDIMENTO,
            TicketStatus.AGUARDANDO_USUARIO
    );

    public record TicketAttachmentData(String fileName, String contentType, byte[] data) {
    }

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final TicketAuditLogRepository ticketAuditLogRepository;
    private final SecurityUtils securityUtils;
    private final java.time.Clock clock;
    private final jakarta.persistence.EntityManager entityManager;

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository,
                         AssetRepository assetRepository, TicketAuditLogRepository ticketAuditLogRepository,
                         SecurityUtils securityUtils, jakarta.persistence.EntityManager entityManager, java.time.Clock clock) {
        this.clock = clock;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.ticketAuditLogRepository = ticketAuditLogRepository;
        this.securityUtils = securityUtils;
        this.entityManager = entityManager;
    }

    @Transactional
    public TicketResponseDTO createTicket(TicketRequestDTO dto) {
        User requester = securityUtils.getCurrentUser();

        if (requester.getRole() != UserRole.REQUESTER) {
            throw new BusinessRuleException("O solicitante deve ter o papel REQUESTER.");
        }

        Asset asset = null;
        if (dto.getCategory() == TicketCategory.INCIDENTE_EQUIPAMENTO) {
            if (dto.getAssetId() == null) {
                throw new BusinessRuleException("Para a categoria INCIDENTE_EQUIPAMENTO, o envio do ativo (assetId) é obrigatório.");
            }
        }

        if (dto.getAssetId() != null) {
            asset = assetRepository.findById(dto.getAssetId())
                    .orElseThrow(() -> new AssetNotFoundException("Ativo não encontrado com o id: " + dto.getAssetId()));

            if (asset.getStatus() != AssetStatus.IN_USE) {
                throw new BusinessRuleException("Somente ativos com status IN_USE podem ser vinculados a chamados.");
            }
        }

        long authenticatedVersion = requester.getSessionVersion();
        // Serialize duplicate detection for this requester across all application instances.
        User lockedRequester = userRepository.findByIdForUpdate(requester.getId())
                .orElseThrow(() -> new AccessDeniedException("Conta não disponível."));
        entityManager.refresh(lockedRequester);
        if (!lockedRequester.isEnabled() || lockedRequester.getRole() != UserRole.REQUESTER
                || lockedRequester.getSessionVersion() != authenticatedVersion) {
            throw new AccessDeniedException("Sessão não disponível. Entre novamente.");
        }
        requester = lockedRequester;
        if (asset != null) {
            asset = assetRepository.findByIdForUpdate(asset.getId()).orElseThrow(() -> new AssetNotFoundException("Ativo não encontrado."));
            entityManager.refresh(asset);
            if (asset.getStatus() != AssetStatus.IN_USE) throw new BusinessRuleException("Somente ativos com status IN_USE podem ser vinculados a chamados.");
        }

        validateNoDuplicateActiveTicket(
                requester.getId(),
                dto.getCategory(),
                asset != null ? asset.getId() : dto.getAssetId()
        );

        TicketPriority priority = calculatePriority(dto.getCategory());

        Ticket ticket = new Ticket(
                dto.getTitle(),
                dto.getDescription(),
                dto.getCategory(),
                priority,
                requester,
                asset
        );
        ticket.setCreatedAt(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        ticket.setStatus(TicketStatus.CRIADO);
        ticket.setTechnician(null);

        Ticket savedTicket = ticketRepository.save(ticket);
        ticketRepository.flush();
        return response(savedTicket);
    }

    private void validateNoDuplicateActiveTicket(Long requesterId, TicketCategory category, Long assetId) {
        if (category == TicketCategory.DUVIDA || category == TicketCategory.OUTRO) {
            return;
        }

        Optional<Ticket> existingActiveTicket;
        if (category == TicketCategory.INCIDENTE_EQUIPAMENTO) {
            existingActiveTicket = ticketRepository.findFirstByRequesterIdAndCategoryAndAssetIdAndStatusInOrderByIdAsc(
                    requesterId,
                    category,
                    assetId,
                    ACTIVE_STATUSES
            );
        } else {
            existingActiveTicket = ticketRepository.findFirstByRequesterIdAndCategoryAndStatusInOrderByIdAsc(
                    requesterId,
                    category,
                    ACTIVE_STATUSES
            );
        }

        if (existingActiveTicket != null && existingActiveTicket.isPresent()) {
            Long existingId = existingActiveTicket.get().getId();
            throw new BusinessRuleException(
                    "Você já possui um chamado em andamento para este tipo de problema. Acompanhe o chamado #" + existingId + ".",
                    existingId
            );
        }
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getAllTickets() {
        User currentUser = securityUtils.getCurrentUser();
        List<Ticket> tickets;

        if (currentUser.getRole() == UserRole.REQUESTER) {
            tickets = ticketRepository.findByRequesterId(currentUser.getId());
        } else {
            tickets = ticketRepository.findAll();
        }

        return tickets.stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponseDTO getTicketById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + id));

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() == UserRole.REQUESTER && !ticket.getRequester().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Você não possui permissão para visualizar este chamado.");
        }

        return response(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getTicketsByRequester(Long requesterId) {
        User currentUser = securityUtils.getCurrentUser();

        if (currentUser.getRole() == UserRole.REQUESTER && !currentUser.getId().equals(requesterId)) {
            throw new AccessDeniedException("Você não possui permissão para visualizar chamados de outro solicitante.");
        }

        if (!userRepository.existsById(requesterId)) {
            throw new UserNotFoundException("Solicitante não encontrado com o id: " + requesterId);
        }

        return ticketRepository.findByRequesterId(requesterId)
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public TicketResponseDTO assignTechnician(Long ticketId, TicketAssignTechnicianDTO dto) {
        User authenticated = securityUtils.getCurrentUser();
        if (authenticated.getRole() == UserRole.REQUESTER) {
            throw new AccessDeniedException("Solicitantes não possuem permissão para atribuir técnicos.");
        }
        if (authenticated.getRole() == UserRole.TECHNICIAN && !authenticated.getId().equals(dto.getTechnicianId())) {
            throw new AccessDeniedException("Técnicos só podem assumir chamados para si mesmos.");
        }
        long authenticatedVersion = authenticated.getSessionVersion();
        // Follow the same administrator/user lock order as account administration.
        userRepository.lockActiveAdministrators();
        var locked = new java.util.HashMap<Long, User>();
        for (Long id : java.util.stream.Stream.of(authenticated.getId(), dto.getTechnicianId()).distinct().sorted().toList()) {
            User account = userRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new UserNotFoundException("Conta não encontrada para atribuição."));
            entityManager.refresh(account);
            locked.put(id, account);
        }
        User currentUser = locked.get(authenticated.getId());
        if (!currentUser.isEnabled() || currentUser.getSessionVersion() != authenticatedVersion
                || (currentUser.getRole() != UserRole.ADMIN && currentUser.getRole() != UserRole.TECHNICIAN)) {
            throw new AccessDeniedException("Sessão ou permissão alterada. Entre novamente.");
        }
        User technician = locked.get(dto.getTechnicianId());
        if (!technician.isEnabled()) throw new BusinessRuleException("Técnicos inativos não podem receber novas atribuições.");
        if (technician.getRole() != UserRole.TECHNICIAN) throw new BusinessRuleException("Apenas usuários com papel TECHNICIAN podem ser atribuídos como técnico.");
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId));
        entityManager.refresh(ticket);
        if (dto.getExpectedVersion() == null) {
            throw new BusinessRuleException("Informe a versão atual do chamado (expectedVersion).");
        }
        checkVersion(ticket, dto.getExpectedVersion());
        if (!ACTIVE_STATUSES.contains(ticket.getStatus())) {
            throw new BusinessRuleException("Chamados resolvidos, fechados ou cancelados não permitem atribuição ou transferência.");
        }
        User previous = ticket.getTechnician();
        if (currentUser.getRole() == UserRole.TECHNICIAN && previous != null) {
            throw new AccessDeniedException("Somente administradores podem transferir chamados já atribuídos.");
        }
        if (previous != null && previous.getId().equals(technician.getId())) return response(ticket);
        String reason = dto.getJustification() == null ? null : dto.getJustification().strip();
        if (previous != null && (reason == null || reason.isBlank())) {
            throw new BusinessRuleException("A justificativa é obrigatória para transferir o chamado.");
        }
        if (reason != null && reason.length() > 2000) throw new BusinessRuleException("A justificativa deve ter no máximo 2000 caracteres.");
        TicketStatus previousStatus = ticket.getStatus();
        ticket.setTechnician(technician);
        if (ticket.getStatus() == TicketStatus.CRIADO) ticket.setStatus(TicketStatus.ABERTO);
        ticketAuditLogRepository.save(new TicketAuditLog(ticket, currentUser, "technician",
                previous == null ? null : previous.getId().toString(), technician.getId().toString(),
                reason == null || reason.isBlank() ? null : reason, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
        if (previousStatus != ticket.getStatus()) recordAudit(ticket, currentUser, "status", previousStatus.name(), ticket.getStatus().name());
        Ticket updated = ticketRepository.save(ticket);
        ticketRepository.flush();
        return response(updated);
    }

    @Transactional
    public TicketResponseDTO updateTicketStatus(Long ticketId, TicketStatusUpdateDTO dto) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId));

        User currentUser = securityUtils.getCurrentUser();

        if (currentUser.getRole() == UserRole.REQUESTER) {
            entityManager.refresh(ticket);
            if (!ticket.getRequester().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Você não possui permissão para alterar este chamado.");
            }
            if (dto.getStatus() != TicketStatus.CANCELADO) {
                throw new AccessDeniedException("Solicitantes só possuem permissão para cancelar seus próprios chamados.");
            }
        } else if (currentUser.getRole() == UserRole.TECHNICIAN) {
            if (ticket.getTechnician() == null || !ticket.getTechnician().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Técnicos só podem alterar chamados atribuídos a si mesmos.");
            }
        }

        checkVersion(ticket, dto.getExpectedVersion());
        if (currentUser.getRole() == UserRole.REQUESTER && dto.getSolution() != null
                && !dto.getSolution().isBlank()) {
            throw new AccessDeniedException("Solicitantes não podem registrar ou alterar a solução técnica.");
        }
        TicketStatus currentStatus = ticket.getStatus();
        TicketStatus newStatus = dto.getStatus();
        String previousSolution = ticket.getSolution();
        String effectiveSolution = dto.getSolution() != null && !dto.getSolution().trim().isEmpty()
                ? dto.getSolution().trim()
                : (previousSolution != null ? previousSolution.trim() : null);

        validateStatusTransition(currentStatus, newStatus, effectiveSolution);

        if (dto.getSolution() != null && !dto.getSolution().trim().isEmpty()) {
            String newSolution = dto.getSolution().trim();
            if (!Objects.equals(previousSolution, newSolution)) {
                ticket.setSolution(newSolution);
                recordAudit(ticket, currentUser, "solution", previousSolution, newSolution);
            }
        }
        if (currentStatus != newStatus) {
            if (newStatus == TicketStatus.RESOLVIDO) {
                ticket.recordSlaResolution(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                        com.helpdeskpro.backend.domain.TicketSla.deadline(ticket.getCreatedAt(), ticket.getPriority()));
            }
            ticket.setStatus(newStatus);
            recordAudit(ticket, currentUser, "status", currentStatus.name(), newStatus.name());
        }

        Ticket updatedTicket = ticketRepository.save(ticket);
        ticketRepository.flush();
        return response(updatedTicket);
    }

    private TicketResponseDTO response(Ticket ticket) {
        return TicketResponseDTO.fromEntity(ticket, clock.instant());
    }

    private void checkVersion(Ticket ticket, Long expected) {
        if (expected != null && !expected.equals(ticket.getVersion())) {
            throw new org.springframework.dao.OptimisticLockingFailureException("Chamado alterado por outra operação.");
        }
    }

    private void recordAudit(Ticket ticket, User changedBy, String fieldName, String oldValue, String newValue) {
        ticketAuditLogRepository.save(new TicketAuditLog(ticket, changedBy, fieldName, oldValue, newValue, null, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }

    private void validateStatusTransition(TicketStatus currentStatus, TicketStatus newStatus, String solution) {
        if (currentStatus == newStatus) {
            return;
        }

        if (currentStatus == TicketStatus.FECHADO || currentStatus == TicketStatus.CANCELADO) {
            throw new BusinessRuleException("Não é permitido alterar o status de um chamado que já está " + currentStatus + ".");
        }

        if (newStatus == TicketStatus.CANCELADO) {
            if (currentStatus == TicketStatus.RESOLVIDO || currentStatus == TicketStatus.FECHADO) {
                throw new BusinessRuleException("Não é permitido cancelar um chamado que já está com status " + currentStatus + ".");
            }
            if (currentStatus != TicketStatus.CRIADO && currentStatus != TicketStatus.ABERTO) {
                throw new BusinessRuleException("O cancelamento só é permitido para chamados com status CRIADO ou ABERTO.");
            }
            return;
        }

        if (newStatus == TicketStatus.ABERTO) {
            if (currentStatus != TicketStatus.CRIADO) {
                throw new BusinessRuleException("Transição inválida: o status ABERTO só pode ser definido a partir do status CRIADO.");
            }
            return;
        }

        if (newStatus == TicketStatus.EM_ATENDIMENTO) {
            if (currentStatus != TicketStatus.ABERTO && currentStatus != TicketStatus.AGUARDANDO_USUARIO) {
                throw new BusinessRuleException("Transição inválida: o status EM_ATENDIMENTO só pode ser definido a partir do status ABERTO ou AGUARDANDO_USUARIO.");
            }
            return;
        }

        if (newStatus == TicketStatus.AGUARDANDO_USUARIO) {
            if (currentStatus != TicketStatus.EM_ATENDIMENTO) {
                throw new BusinessRuleException("Transição inválida: o status AGUARDANDO_USUARIO só pode ser definido a partir do status EM_ATENDIMENTO.");
            }
            return;
        }

        if (newStatus == TicketStatus.RESOLVIDO) {
            if (currentStatus != TicketStatus.EM_ATENDIMENTO && currentStatus != TicketStatus.AGUARDANDO_USUARIO) {
                throw new BusinessRuleException("Transição inválida: o status RESOLVIDO só pode ser definido a partir do status EM_ATENDIMENTO ou AGUARDANDO_USUARIO.");
            }
            if (solution == null || solution.trim().isEmpty()) {
                throw new BusinessRuleException("Para transitar o status para RESOLVIDO, o campo solução é obrigatório.");
            }
            return;
        }

        if (newStatus == TicketStatus.FECHADO) {
            if (currentStatus != TicketStatus.RESOLVIDO) {
                throw new BusinessRuleException("O chamado só pode ser alterado para FECHADO se o status atual for RESOLVIDO.");
            }
            if (solution == null || solution.trim().isEmpty()) {
                throw new BusinessRuleException("Para transitar o status para FECHADO, o campo solução é obrigatório.");
            }
            return;
        }

        throw new BusinessRuleException("Transição de status não permitida de " + currentStatus + " para " + newStatus + ".");
    }

    @Transactional
    public TicketResponseDTO uploadAttachment(Long ticketId, MultipartFile file) {
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() != UserRole.REQUESTER) {
            throw new AccessDeniedException("Apenas solicitantes podem anexar imagens ao chamado.");
        }

        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId));

        entityManager.refresh(ticket);
        if (!ticket.getRequester().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Você não possui permissão para anexar imagem a este chamado.");
        }

        if (ticket.getAttachmentData() != null && ticket.getAttachmentData().length > 0) {
            throw new BusinessRuleException("Este chamado já possui uma imagem anexada.");
        }

        var image = AttachmentImages.upload(file);
        ticket.setAttachmentName(image.name());
        ticket.setAttachmentContentType(image.contentType());
        ticket.setAttachmentData(image.bytes());

        Ticket savedTicket = ticketRepository.save(ticket);
        ticketRepository.flush();
        return response(savedTicket);
    }

    @Transactional(readOnly = true)
    public TicketAttachmentData getTicketAttachment(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId));

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() == UserRole.REQUESTER && !ticket.getRequester().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Você não possui permissão para visualizar o anexo deste chamado.");
        }

        if (ticket.getAttachmentData() == null || ticket.getAttachmentData().length == 0) {
            throw new TicketNotFoundException("O chamado não possui anexo.");
        }

        return new TicketAttachmentData(
                ticket.getAttachmentName(),
                AttachmentImages.validate(ticket.getAttachmentData()),
                ticket.getAttachmentData()
        );
    }

    private TicketPriority calculatePriority(TicketCategory category) {
        return switch (category) {
            case INCIDENTE_SISTEMA -> TicketPriority.ALTA;
            case INCIDENTE_EQUIPAMENTO, SOLICITACAO_ACESSO, OUTRO -> TicketPriority.MEDIA;
            case DUVIDA -> TicketPriority.BAIXA;
        };
    }
}
