package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.AssetStatus;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.DashboardResponseDTO;
import com.helpdeskpro.backend.dto.TechnicianTicketCountDTO;
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final TicketRepository ticketRepository;
    private final AssetRepository assetRepository;
    private final java.time.Clock clock;

    public DashboardService(TicketRepository ticketRepository, AssetRepository assetRepository, java.time.Clock clock) {
        this.clock = clock;
        this.ticketRepository = ticketRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public DashboardResponseDTO getSummary() {
        Map<TicketStatus, Long> byStatus = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            byStatus.put(status, 0L);
        }
        for (Object[] row : ticketRepository.countGroupedByStatus()) {
            byStatus.put((TicketStatus) row[0], ((Number) row[1]).longValue());
        }

        Map<TicketPriority, Long> byPriority = new LinkedHashMap<>();
        for (TicketPriority priority : TicketPriority.values()) {
            byPriority.put(priority, 0L);
        }
        for (Object[] row : ticketRepository.countGroupedByPriority()) {
            byPriority.put((TicketPriority) row[0], ((Number) row[1]).longValue());
        }

        List<TechnicianTicketCountDTO> byTechnician = ticketRepository.countGroupedByTechnician()
                .stream()
                .sorted(Comparator.comparing(TechnicianTicketCountDTO::getTotal).reversed()
                        .thenComparing(TechnicianTicketCountDTO::getTechnicianName))
                .toList();

        long unassigned = ticketRepository.countByTechnicianIsNull();
        long underMaintenance = assetRepository.countByStatus(AssetStatus.UNDER_MAINTENANCE);

        var bySla = new java.util.EnumMap<com.helpdeskpro.backend.domain.enums.TicketSlaStatus, Long>(com.helpdeskpro.backend.domain.enums.TicketSlaStatus.class);
        for (var state : com.helpdeskpro.backend.domain.enums.TicketSlaStatus.values()) bySla.put(state, 0L);
        var now = clock.instant();
        for (var ticket : ticketRepository.findAll()) {
            var state = com.helpdeskpro.backend.domain.TicketSla.assess(ticket, now).status();
            bySla.merge(state, 1L, Long::sum);
        }
        var response = new DashboardResponseDTO(byStatus, byPriority, byTechnician, unassigned, underMaintenance);
        response.setTicketsBySla(bySla);
        return response;
    }
}
