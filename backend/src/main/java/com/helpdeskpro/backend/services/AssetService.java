package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.dto.AssetRequestDTO;
import com.helpdeskpro.backend.dto.AssetResponseDTO;
import com.helpdeskpro.backend.dto.AssetStatusUpdateDTO;
import com.helpdeskpro.backend.exceptions.AssetNotFoundException;
import com.helpdeskpro.backend.exceptions.DuplicateAssetCodeException;
import com.helpdeskpro.backend.repositories.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssetService {

    private final AssetRepository assetRepository;

    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Transactional
    public AssetResponseDTO createAsset(AssetRequestDTO dto) {
        if (assetRepository.existsByCode(dto.getCode())) {
            throw new DuplicateAssetCodeException("Já existe um ativo cadastrado com o código: " + dto.getCode());
        }

        Asset asset = new Asset(
                dto.getCode(),
                dto.getType(),
                dto.getDescription(),
                dto.getStatus()
        );

        Asset savedAsset = assetRepository.save(asset);
        return AssetResponseDTO.fromEntity(savedAsset);
    }

    @Transactional(readOnly = true)
    public List<AssetResponseDTO> getAllAssets() {
        return assetRepository.findAll()
                .stream()
                .map(AssetResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssetResponseDTO getAssetById(Long id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new AssetNotFoundException("Ativo não encontrado com o id: " + id));
        return AssetResponseDTO.fromEntity(asset);
    }

    @Transactional
    public AssetResponseDTO updateAsset(Long id, AssetRequestDTO dto) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new AssetNotFoundException("Ativo não encontrado com o id: " + id));

        if (assetRepository.existsByCodeAndIdNot(dto.getCode(), id)) {
            throw new DuplicateAssetCodeException("Já existe outro ativo cadastrado com o código: " + dto.getCode());
        }

        asset.setCode(dto.getCode());
        asset.setType(dto.getType());
        asset.setDescription(dto.getDescription());
        asset.setStatus(dto.getStatus());

        Asset updatedAsset = assetRepository.save(asset);
        return AssetResponseDTO.fromEntity(updatedAsset);
    }

    @Transactional
    public AssetResponseDTO updateAssetStatus(Long id, AssetStatusUpdateDTO dto) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new AssetNotFoundException("Ativo não encontrado com o id: " + id));

        asset.setStatus(dto.getStatus());

        Asset updatedAsset = assetRepository.save(asset);
        return AssetResponseDTO.fromEntity(updatedAsset);
    }

    @Transactional
    public void deleteAsset(Long id) {
        if (!assetRepository.existsById(id)) {
            throw new AssetNotFoundException("Ativo não encontrado com o id: " + id);
        }
        assetRepository.deleteById(id);
    }
}
