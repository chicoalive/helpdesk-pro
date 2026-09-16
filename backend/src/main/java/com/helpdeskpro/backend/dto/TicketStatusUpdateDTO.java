package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

public class TicketStatusUpdateDTO {

    @NotNull(message = "O status é obrigatório")
    private TicketStatus status;

    private String solution;

    public TicketStatusUpdateDTO() {
    }

    public TicketStatusUpdateDTO(TicketStatus status, String solution) {
        this.status = status;
        this.solution = solution;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public String getSolution() {
        return solution;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }
}
