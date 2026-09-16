package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;

import java.time.Instant;

public class TicketResponseDTO {

    private Long id;
    private String title;
    private String description;
    private String solution;
    private TicketStatus status;
    private TicketCategory category;
    private TicketPriority priority;
    private Instant createdAt;
    private Instant updatedAt;

    private Long requesterId;
    private String requesterName;
    private String requesterEmail;

    private Long technicianId;
    private String technicianName;
    private String technicianEmail;

    private Long assetId;
    private String assetCode;
    private String assetType;

    public TicketResponseDTO() {
    }

    public TicketResponseDTO(Long id, String title, String description, String solution, TicketStatus status,
                             TicketCategory category, TicketPriority priority, Instant createdAt, Instant updatedAt,
                             Long requesterId, String requesterName, String requesterEmail,
                             Long technicianId, String technicianName, String technicianEmail,
                             Long assetId, String assetCode, String assetType) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.solution = solution;
        this.status = status;
        this.category = category;
        this.priority = priority;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.requesterEmail = requesterEmail;
        this.technicianId = technicianId;
        this.technicianName = technicianName;
        this.technicianEmail = technicianEmail;
        this.assetId = assetId;
        this.assetCode = assetCode;
        this.assetType = assetType;
    }

    public static TicketResponseDTO fromEntity(Ticket ticket) {
        TicketResponseDTO dto = new TicketResponseDTO();
        dto.setId(ticket.getId());
        dto.setTitle(ticket.getTitle());
        dto.setDescription(ticket.getDescription());
        dto.setSolution(ticket.getSolution());
        dto.setStatus(ticket.getStatus());
        dto.setCategory(ticket.getCategory());
        dto.setPriority(ticket.getPriority());
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setUpdatedAt(ticket.getUpdatedAt());

        if (ticket.getRequester() != null) {
            dto.setRequesterId(ticket.getRequester().getId());
            dto.setRequesterName(ticket.getRequester().getName());
            dto.setRequesterEmail(ticket.getRequester().getEmail());
        }

        if (ticket.getTechnician() != null) {
            dto.setTechnicianId(ticket.getTechnician().getId());
            dto.setTechnicianName(ticket.getTechnician().getName());
            dto.setTechnicianEmail(ticket.getTechnician().getEmail());
        }

        if (ticket.getAsset() != null) {
            dto.setAssetId(ticket.getAsset().getId());
            dto.setAssetCode(ticket.getAsset().getCode());
            dto.setAssetType(ticket.getAsset().getType());
        }

        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSolution() {
        return solution;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public String getTechnicianName() {
        return technicianName;
    }

    public String getTechnicianEmail() {
        return technicianEmail;
    }

    public Long getAssetId() {
        return assetId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public void setCategory(TicketCategory category) {
        this.category = category;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }

    public void setTechnicianId(Long technicianId) {
        this.technicianId = technicianId;
    }

    public void setTechnicianName(String technicianName) {
        this.technicianName = technicianName;
    }

    public void setTechnicianEmail(String technicianEmail) {
        this.technicianEmail = technicianEmail;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }
}
