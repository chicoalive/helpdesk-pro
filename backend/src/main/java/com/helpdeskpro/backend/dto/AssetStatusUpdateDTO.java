package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.AssetStatus;
import jakarta.validation.constraints.NotNull;

public class AssetStatusUpdateDTO {

    @NotNull(message = "O status do ativo é obrigatório")
    private AssetStatus status;

    public AssetStatusUpdateDTO() {
    }

    public AssetStatusUpdateDTO(AssetStatus status) {
        this.status = status;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public void setStatus(AssetStatus status) {
        this.status = status;
    }
}
