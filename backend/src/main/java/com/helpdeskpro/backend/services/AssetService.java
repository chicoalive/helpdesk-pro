package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.*;
import com.helpdeskpro.backend.domain.entities.*;
import com.helpdeskpro.backend.dto.*;
import com.helpdeskpro.backend.exceptions.*;
import com.helpdeskpro.backend.repositories.*;
import com.helpdeskpro.backend.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;

@Service
public class AssetService {
 private final AssetRepository assetRepository; private final AssetAuditLogRepository auditRepository;
 private final SecurityUtils security;private final UserRepository users;private final EntityManager em;private final Clock clock;
 public AssetService(AssetRepository assets,AssetAuditLogRepository audits,SecurityUtils security,UserRepository users,EntityManager em,Clock clock){this.assetRepository=assets;this.auditRepository=audits;this.security=security;this.users=users;this.em=em;this.clock=clock;}
 private User administrator(boolean lock){
  User actor=security.getCurrentUser();if(actor.getRole()!=UserRole.ADMIN)throw new AccessDeniedException("Somente administradores podem gerenciar ativos.");
  if(lock){long session=actor.getSessionVersion();users.lockActiveAdministrators();actor=users.findByIdForUpdate(actor.getId()).orElseThrow(()->new AccessDeniedException("Conta indisponível."));em.refresh(actor);if(!actor.isEnabled()||actor.getRole()!=UserRole.ADMIN||actor.getSessionVersion()!=session)throw new AccessDeniedException("Sessão ou permissão alterada. Entre novamente.");}
  return actor;
 }
 private Asset locked(Long id){Asset a=assetRepository.findByIdForUpdate(id).orElseThrow(()->new AssetNotFoundException("Ativo não encontrado."));em.refresh(a);return a;}
 private void version(Asset asset,Long expected){if(expected==null)throw new BusinessRuleException("Informe a versão atual do ativo (expectedVersion).");if(!expected.equals(asset.getVersion()))throw new org.springframework.dao.OptimisticLockingFailureException("Ativo alterado por outra operação.");}
 private String description(String value){return value==null||value.isBlank()?null:value.trim();}
 private String code(String value){if(value==null||value.isBlank())throw new BusinessRuleException("O código do ativo é obrigatório.");String normalized=value.trim().toUpperCase(Locale.ROOT);if(normalized.length()>255)throw new BusinessRuleException("O código deve ter no máximo 255 caracteres.");return normalized;}
 private void change(Map<String,UserAuditChange> changes,String field,String before,String after){if(!Objects.equals(before,after))changes.put(field,new UserAuditChange(before,after));}
 @Transactional public AssetResponseDTO createAsset(AssetRequestDTO dto){User actor=administrator(true);String code=code(dto.getCode());if(assetRepository.existsByCode(code))throw new DuplicateAssetCodeException("Já existe um ativo cadastrado com este código.");
  Asset asset=assetRepository.save(new Asset(code,dto.getType().trim(),description(dto.getDescription()),dto.getStatus()));
  Map<String,UserAuditChange> changes=new LinkedHashMap<>();change(changes,"code",null,asset.getCode());change(changes,"type",null,asset.getType());change(changes,"description",null,asset.getDescription());change(changes,"status",null,asset.getStatus().name());
  auditRepository.save(new AssetAuditLog(asset,actor,"ASSET_CREATED",clock.instant(),changes));assetRepository.flush();return AssetResponseDTO.fromEntity(asset);
 }
 @Transactional(readOnly=true) public List<AssetResponseDTO> getAllAssets(){return assetRepository.findAll().stream().map(AssetResponseDTO::fromEntity).toList();}
 @Transactional(readOnly=true) public List<AssetResponseDTO> getAvailableAssets(){boolean requester=security.getCurrentUser().getRole()==UserRole.REQUESTER;return assetRepository.findByStatus(AssetStatus.IN_USE).stream().map(a->{var dto=AssetResponseDTO.fromEntity(a);if(requester)dto.setDescription(null);return dto;}).toList();}
 @Transactional(readOnly=true) public AssetResponseDTO getAssetById(Long id){return AssetResponseDTO.fromEntity(assetRepository.findById(id).orElseThrow(()->new AssetNotFoundException("Ativo não encontrado.")));}
 @Transactional public AssetResponseDTO updateAsset(Long id,AssetRequestDTO dto){User actor=administrator(true);Asset a=locked(id);version(a,dto.getExpectedVersion());String code=code(dto.getCode());if(assetRepository.existsByCodeAndIdNot(code,id))throw new DuplicateAssetCodeException("Já existe outro ativo cadastrado com este código.");
  String type=dto.getType().trim(),description=description(dto.getDescription());Map<String,UserAuditChange> changes=new LinkedHashMap<>();change(changes,"code",a.getCode(),code);change(changes,"type",a.getType(),type);change(changes,"description",a.getDescription(),description);change(changes,"status",a.getStatus().name(),dto.getStatus().name());
  if(changes.isEmpty())return AssetResponseDTO.fromEntity(a);a.setCode(code);a.setType(type);a.setDescription(description);a.setStatus(dto.getStatus());return save(a,actor,changes);
 }
 @Transactional public AssetResponseDTO updateAssetStatus(Long id,AssetStatusUpdateDTO dto){User actor=administrator(true);Asset a=locked(id);version(a,dto.getExpectedVersion());if(a.getStatus()==dto.getStatus())return AssetResponseDTO.fromEntity(a);Map<String,UserAuditChange> changes=Map.of("status",new UserAuditChange(a.getStatus().name(),dto.getStatus().name()));a.setStatus(dto.getStatus());return save(a,actor,changes);}
 private AssetResponseDTO save(Asset asset,User actor,Map<String,UserAuditChange> changes){Asset saved=assetRepository.save(asset);auditRepository.save(new AssetAuditLog(saved,actor,"ASSET_UPDATED",clock.instant(),changes));assetRepository.flush();return AssetResponseDTO.fromEntity(saved);}
 @Transactional(readOnly=true) public List<AssetAuditResponseDTO> getAudit(Long id){administrator(false);if(!assetRepository.existsById(id))throw new AssetNotFoundException("Ativo não encontrado.");return auditRepository.findByAssetIdOrderByOccurredAtAscIdAsc(id).stream().map(AssetAuditResponseDTO::from).toList();}
 @Transactional public void deleteAsset(Long id){administrator(false);if(!assetRepository.existsById(id))throw new AssetNotFoundException("Ativo não encontrado.");throw new BusinessRuleException("Ativos não podem ser excluídos. Use os estados Inativo ou Descartado para preservar o histórico.");}
}
