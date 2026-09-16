package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.domain.AssetStatus;
import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.entities.Ticket;
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
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository, AssetRepository assetRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public TicketResponseDTO createTicket(TicketRequestDTO dto) {
        User requester = userRepository.findById(dto.getRequesterId())
                .orElseThrow(() -> new BusinessRuleException("Solicitante não encontrado com o id: " + dto.getRequesterId()));

        Asset asset = null;
        if (dto.getCategory() == TicketCategory.INCIDENTE_EQUIPAMENTO) {
            if (dto.getAssetId() == null) {
                throw new BusinessRuleException("Para a categoria INCIDENTE_EQUIPAMENTO, o envio do ativo (assetId) é obrigatório.");
            }
        }

        if (dto.getAssetId() != null) {
            asset = assetRepository.findById(dto.getAssetId())
                    .orElseThrow(() -> new AssetNotFoundException("Ativo não encontrado com o id: " + dto.getAssetId()));

            if (asset.getStatus() == AssetStatus.INACTIVE || asset.getStatus() == AssetStatus.DISCARDED) {
                throw new BusinessRuleException("Não é permitido vincular um ativo que possua status " + asset.getStatus() + ".");
            }
        }

        TicketPriority priority = calculatePriority(dto.getCategory());

        Ticket ticket = new Ticket(
                dto.getTitle(),
                dto.getDescription(),
                dto.getCategory(),
                priority,
                requester,
                asset
        );
        ticket.setStatus(TicketStatus.CRIADO);
        ticket.setTechnician(null);

        Ticket savedTicket = ticketRepository.save(ticket);
        return TicketResponseDTO.fromEntity(savedTicket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getAllTickets() {
        return ticketRepository.findAll()
                .stream()
                .map(TicketResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponseDTO getTicketById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + id));
        return TicketResponseDTO.fromEntity(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getTicketsByRequester(Long requesterId) {
        if (!userRepository.existsById(requesterId)) {
            throw new BusinessRuleException("Solicitante não encontrado com o id: " + requesterId);
        }
        return ticketRepository.findByRequesterId(requesterId)
                .stream()
                .map(TicketResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public TicketResponseDTO assignTechnician(Long ticketId, TicketAssignTechnicianDTO dto) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId));

        if (ticket.getTechnician() != null) {
            throw new BusinessRuleException("O chamado já possui um técnico atribuído e não pode ser reatribuído.");
        }

        User technician = userRepository.findById(dto.getTechnicianId())
                .orElseThrow(() -> new BusinessRuleException("Técnico não encontrado com o id: " + dto.getTechnicianId()));

        ticket.setTechnician(technician);
        if (ticket.getStatus() == TicketStatus.CRIADO) {
            ticket.setStatus(TicketStatus.ABERTO);
        }

        Ticket updatedTicket = ticketRepository.save(ticket);
        return TicketResponseDTO.fromEntity(updatedTicket);
    }

    @Transactional
    public TicketResponseDTO updateTicketStatus(Long ticketId, TicketStatusUpdateDTO dto) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Chamado não encontrado com o id: " + ticketId));

        TicketStatus currentStatus = ticket.getStatus();
        TicketStatus newStatus = dto.getStatus();
        String effectiveSolution = dto.getSolution() != null ? dto.getSolution() : ticket.getSolution();

        if (newStatus == TicketStatus.RESOLVIDO || newStatus == TicketStatus.FECHADO) {
            if (effectiveSolution == null || effectiveSolution.trim().isEmpty()) {
                throw new BusinessRuleException("Para transitar o status para " + newStatus + ", o campo solução é obrigatório.");
            }
        }

        if (newStatus == TicketStatus.FECHADO && currentStatus != TicketStatus.RESOLVIDO) {
            throw new BusinessRuleException("O chamado só pode ser alterado para FECHADO se o status atual for RESOLVIDO.");
        }

        if (newStatus == TicketStatus.CANCELADO) {
            if (currentStatus != TicketStatus.CRIADO
                    && currentStatus != TicketStatus.ABERTO
                    && currentStatus != TicketStatus.EM_ATENDIMENTO
                    && currentStatus != TicketStatus.AGUARDANDO_USUARIO) {
                throw new BusinessRuleException("Não é permitido cancelar um chamado que já está com status " + currentStatus + ".");
            }
        }

        if (dto.getSolution() != null) {
            ticket.setSolution(dto.getSolution());
        }
        ticket.setStatus(newStatus);

        Ticket updatedTicket = ticketRepository.save(ticket);
        return TicketResponseDTO.fromEntity(updatedTicket);
    }

    private TicketPriority calculatePriority(TicketCategory category) {
        return switch (category) {
            case INCIDENTE_SISTEMA -> TicketPriority.ALTA;
            case INCIDENTE_EQUIPAMENTO, SOLICITACAO_ACESSO, OUTRO -> TicketPriority.MEDIA;
            case DUVIDA -> TicketPriority.BAIXA;
        };
    }
}
