package com.helpdeskpro.backend.dto;

import jakarta.validation.constraints.NotNull;

public class TicketAssignTechnicianDTO {

    @NotNull(message = "O ID do técnico é obrigatório")
    private Long technicianId;

    public TicketAssignTechnicianDTO() {
    }

    public TicketAssignTechnicianDTO(Long technicianId) {
        this.technicianId = technicianId;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(Long technicianId) {
        this.technicianId = technicianId;
    }
}
