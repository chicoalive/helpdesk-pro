package com.helpdeskpro.backend.domain.entities;

import com.helpdeskpro.backend.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="asset_audit_logs")
public class AssetAuditLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="asset_id",nullable=false,updatable=false) private Asset asset;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id",nullable=false,updatable=false) private User actor;
    @Column(nullable=false,updatable=false,length=64) private String operation;
    @Column(name="occurred_at",nullable=false,updatable=false) private Instant occurredAt;
    @ElementCollection @CollectionTable(name="asset_audit_changes",joinColumns=@JoinColumn(name="audit_id")) @MapKeyColumn(name="field_name")
    private Map<String,UserAuditChange> changes=new LinkedHashMap<>();
    protected AssetAuditLog() {}
    public AssetAuditLog(Asset asset,User actor,String operation,Instant at,Map<String,UserAuditChange> changes) {this.asset=asset;this.actor=actor;this.operation=operation;this.occurredAt=at;this.changes.putAll(changes);}
    public Long getId(){return id;} public Asset getAsset(){return asset;} public User getActor(){return actor;} public String getOperation(){return operation;} public Instant getOccurredAt(){return occurredAt;} public Map<String,UserAuditChange> getChanges(){return Map.copyOf(changes);}
}
