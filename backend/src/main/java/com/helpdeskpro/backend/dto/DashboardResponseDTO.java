package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;

import java.util.List;
import java.util.Map;

public class DashboardResponseDTO {

    private Map<TicketStatus, Long> ticketsByStatus;
    private Map<TicketPriority, Long> ticketsByPriority;
    private List<TechnicianTicketCountDTO> ticketsByTechnician;
    private long unassignedTickets;
    private long assetsUnderMaintenance;

    public DashboardResponseDTO() {
    }

    public DashboardResponseDTO(Map<TicketStatus, Long> ticketsByStatus,
                                Map<TicketPriority, Long> ticketsByPriority,
                                List<TechnicianTicketCountDTO> ticketsByTechnician,
                                long unassignedTickets,
                                long assetsUnderMaintenance) {
        this.ticketsByStatus = ticketsByStatus;
        this.ticketsByPriority = ticketsByPriority;
        this.ticketsByTechnician = ticketsByTechnician;
        this.unassignedTickets = unassignedTickets;
        this.assetsUnderMaintenance = assetsUnderMaintenance;
    }

    public Map<TicketStatus, Long> getTicketsByStatus() {
        return ticketsByStatus;
    }

    public void setTicketsByStatus(Map<TicketStatus, Long> ticketsByStatus) {
        this.ticketsByStatus = ticketsByStatus;
    }

    public Map<TicketPriority, Long> getTicketsByPriority() {
        return ticketsByPriority;
    }

    public void setTicketsByPriority(Map<TicketPriority, Long> ticketsByPriority) {
        this.ticketsByPriority = ticketsByPriority;
    }

    public List<TechnicianTicketCountDTO> getTicketsByTechnician() {
        return ticketsByTechnician;
    }

    public void setTicketsByTechnician(List<TechnicianTicketCountDTO> ticketsByTechnician) {
        this.ticketsByTechnician = ticketsByTechnician;
    }

    public long getUnassignedTickets() {
        return unassignedTickets;
    }

    public void setUnassignedTickets(long unassignedTickets) {
        this.unassignedTickets = unassignedTickets;
    }

    public long getAssetsUnderMaintenance() {
        return assetsUnderMaintenance;
    }

    public void setAssetsUnderMaintenance(long assetsUnderMaintenance) {
        this.assetsUnderMaintenance = assetsUnderMaintenance;
    }
}
