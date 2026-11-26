package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.AssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AssetRequestDTO {
    @jakarta.validation.constraints.PositiveOrZero
    private Long expectedVersion;
    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long value) { expectedVersion = value; }

    @NotBlank(message = "O código do ativo é obrigatório")
    @jakarta.validation.constraints.Size(max=255, message="O código deve ter no máximo 255 caracteres")
    private String code;

    @NotBlank(message = "O tipo do ativo é obrigatório")
    @jakarta.validation.constraints.Size(max=255, message="O tipo deve ter no máximo 255 caracteres")
    private String type;

    @jakarta.validation.constraints.Size(max=255, message="A descrição deve ter no máximo 255 caracteres")
    private String description;

    @NotNull(message = "O status do ativo é obrigatório")
    private AssetStatus status;

    public AssetRequestDTO() {
    }

    public AssetRequestDTO(String code, String type, String description, AssetStatus status) {
        this.code = code;
        this.type = type;
        this.description = description;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public void setStatus(AssetStatus status) {
        this.status = status;
    }
}
