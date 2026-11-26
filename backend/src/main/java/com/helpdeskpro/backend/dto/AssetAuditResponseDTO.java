package com.helpdeskpro.backend.dto;
import com.helpdeskpro.backend.domain.entities.*;
import java.time.Instant;
import java.util.Map;
public record AssetAuditResponseDTO(Long id,Long assetId,Long actorId,String actorName,String operation,Instant occurredAt,Map<String,UserAuditChange> changes) {
 public static AssetAuditResponseDTO from(AssetAuditLog event){return new AssetAuditResponseDTO(event.getId(),event.getAsset().getId(),event.getActor().getId(),event.getActor().getName(),event.getOperation(),event.getOccurredAt(),event.getChanges());}
}
