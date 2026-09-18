package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.enums.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TicketRequestDTO {

    @NotBlank(message = "O título é obrigatório")
    private String title;

    @NotBlank(message = "A descrição é obrigatória")
    private String description;

    @NotNull(message = "A categoria é obrigatória")
    private TicketCategory category;

    private Long assetId;

    public TicketRequestDTO() {
    }

    public TicketRequestDTO(String title, String description, TicketCategory category, Long assetId) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.assetId = assetId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCategory(TicketCategory category) {
        this.category = category;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }
}
