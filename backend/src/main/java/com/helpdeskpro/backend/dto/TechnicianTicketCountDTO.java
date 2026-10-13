package com.helpdeskpro.backend.dto;

public class TechnicianTicketCountDTO {

    private Long technicianId;
    private String technicianName;
    private Long total;

    public TechnicianTicketCountDTO() {
    }

    public TechnicianTicketCountDTO(Long technicianId, String technicianName, Long total) {
        this.technicianId = technicianId;
        this.technicianName = technicianName;
        this.total = total;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(Long technicianId) {
        this.technicianId = technicianId;
    }

    public String getTechnicianName() {
        return technicianName;
    }

    public void setTechnicianName(String technicianName) {
        this.technicianName = technicianName;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
