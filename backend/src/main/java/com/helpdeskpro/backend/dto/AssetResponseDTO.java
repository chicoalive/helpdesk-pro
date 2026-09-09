package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.domain.AssetStatus;

public class AssetResponseDTO {

    private Long id;
    private String code;
    private String type;
    private String description;
    private AssetStatus status;

    public AssetResponseDTO() {
    }

    public AssetResponseDTO(Long id, String code, String type, String description, AssetStatus status) {
        this.id = id;
        this.code = code;
        this.type = type;
        this.description = description;
        this.status = status;
    }

    public static AssetResponseDTO fromEntity(Asset asset) {
        return new AssetResponseDTO(
                asset.getId(),
                asset.getCode(),
                asset.getType(),
                asset.getDescription(),
                asset.getStatus()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
