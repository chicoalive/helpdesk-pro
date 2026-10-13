package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.AssetStatus;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.DashboardResponseDTO;
import com.helpdeskpro.backend.dto.TechnicianTicketCountDTO;
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    @DisplayName("Sem dados: todos os status e prioridades zerados, lista de técnicos vazia")
    void getSummary_NoData_AllZero() {
        when(ticketRepository.countGroupedByStatus()).thenReturn(List.of());
        when(ticketRepository.countGroupedByPriority()).thenReturn(List.of());
        when(ticketRepository.countGroupedByTechnician()).thenReturn(List.of());
        when(ticketRepository.countByTechnicianIsNull()).thenReturn(0L);
        when(assetRepository.countByStatus(AssetStatus.UNDER_MAINTENANCE)).thenReturn(0L);

        DashboardResponseDTO result = dashboardService.getSummary();

        assertEquals(TicketStatus.values().length, result.getTicketsByStatus().size());
        for (TicketStatus status : TicketStatus.values()) {
            assertEquals(0L, result.getTicketsByStatus().get(status));
        }
        assertEquals(TicketPriority.values().length, result.getTicketsByPriority().size());
        for (TicketPriority priority : TicketPriority.values()) {
            assertEquals(0L, result.getTicketsByPriority().get(priority));
        }
        assertTrue(result.getTicketsByTechnician().isEmpty());
        assertEquals(0L, result.getUnassignedTickets());
        assertEquals(0L, result.getAssetsUnderMaintenance());
    }

    @Test
    @DisplayName("Completa com zero os status e prioridades sem registros e mantém a ordem do enum")
    void getSummary_FillsMissingWithZeroAndKeepsEnumOrder() {
        List<Object[]> statusRows = new ArrayList<>();
        statusRows.add(new Object[]{TicketStatus.FECHADO, 4L});
        statusRows.add(new Object[]{TicketStatus.ABERTO, 3L});
        List<Object[]> priorityRows = new ArrayList<>();
        priorityRows.add(new Object[]{TicketPriority.ALTA, 2L});

        when(ticketRepository.countGroupedByStatus()).thenReturn(statusRows);
        when(ticketRepository.countGroupedByPriority()).thenReturn(priorityRows);
        when(ticketRepository.countGroupedByTechnician()).thenReturn(List.of());
        when(ticketRepository.countByTechnicianIsNull()).thenReturn(0L);
        when(assetRepository.countByStatus(AssetStatus.UNDER_MAINTENANCE)).thenReturn(0L);

        DashboardResponseDTO result = dashboardService.getSummary();

        assertEquals(4L, result.getTicketsByStatus().get(TicketStatus.FECHADO));
        assertEquals(3L, result.getTicketsByStatus().get(TicketStatus.ABERTO));
        assertEquals(0L, result.getTicketsByStatus().get(TicketStatus.CANCELADO));
        assertEquals(0L, result.getTicketsByStatus().get(TicketStatus.CRIADO));
        assertEquals(List.of(TicketStatus.values()), new ArrayList<>(result.getTicketsByStatus().keySet()));

        assertEquals(2L, result.getTicketsByPriority().get(TicketPriority.ALTA));
        assertEquals(0L, result.getTicketsByPriority().get(TicketPriority.BAIXA));
        assertEquals(0L, result.getTicketsByPriority().get(TicketPriority.MEDIA));
        assertEquals(List.of(TicketPriority.values()), new ArrayList<>(result.getTicketsByPriority().keySet()));
    }

    @Test
    @DisplayName("Ordena técnicos por total decrescente e depois por nome")
    void getSummary_SortsTechniciansByTotalDescThenName() {
        List<TechnicianTicketCountDTO> unsorted = new ArrayList<>();
        unsorted.add(new TechnicianTicketCountDTO(3L, "Carla", 2L));
        unsorted.add(new TechnicianTicketCountDTO(2L, "Bruno", 5L));
        unsorted.add(new TechnicianTicketCountDTO(1L, "Ana", 2L));

        when(ticketRepository.countGroupedByStatus()).thenReturn(List.of());
        when(ticketRepository.countGroupedByPriority()).thenReturn(List.of());
        when(ticketRepository.countGroupedByTechnician()).thenReturn(unsorted);
        when(ticketRepository.countByTechnicianIsNull()).thenReturn(0L);
        when(assetRepository.countByStatus(AssetStatus.UNDER_MAINTENANCE)).thenReturn(0L);

        DashboardResponseDTO result = dashboardService.getSummary();

        List<TechnicianTicketCountDTO> list = result.getTicketsByTechnician();
        assertEquals(3, list.size());
        assertEquals("Bruno", list.get(0).getTechnicianName());
        assertEquals(5L, list.get(0).getTotal());
        assertEquals("Ana", list.get(1).getTechnicianName());
        assertEquals("Carla", list.get(2).getTechnicianName());
    }

    @Test
    @DisplayName("Retorna chamados sem técnico e ativos em manutenção")
    void getSummary_UnassignedAndAssetsUnderMaintenance() {
        when(ticketRepository.countGroupedByStatus()).thenReturn(List.of());
        when(ticketRepository.countGroupedByPriority()).thenReturn(List.of());
        when(ticketRepository.countGroupedByTechnician()).thenReturn(List.of());
        when(ticketRepository.countByTechnicianIsNull()).thenReturn(7L);
        when(assetRepository.countByStatus(AssetStatus.UNDER_MAINTENANCE)).thenReturn(3L);

        DashboardResponseDTO result = dashboardService.getSummary();

        assertEquals(7L, result.getUnassignedTickets());
        assertEquals(3L, result.getAssetsUnderMaintenance());
    }
}
