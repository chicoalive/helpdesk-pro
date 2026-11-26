package com.helpdeskpro.backend.dto;

import jakarta.validation.constraints.NotNull;

public class TicketAssignTechnicianDTO {

    @NotNull(message = "O ID do técnico é obrigatório")
    private Long technicianId;

    @jakarta.validation.constraints.PositiveOrZero(message = "A versão deve ser não negativa")
    private Long expectedVersion;
    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }

    @jakarta.validation.constraints.Size(max = 2000, message = "A justificativa deve ter no máximo 2000 caracteres")
    private String justification;
    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

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
