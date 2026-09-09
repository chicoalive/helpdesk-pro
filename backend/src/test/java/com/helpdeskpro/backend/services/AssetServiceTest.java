package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.domain.AssetStatus;
import com.helpdeskpro.backend.dto.AssetRequestDTO;
import com.helpdeskpro.backend.dto.AssetResponseDTO;
import com.helpdeskpro.backend.dto.AssetStatusUpdateDTO;
import com.helpdeskpro.backend.exceptions.AssetNotFoundException;
import com.helpdeskpro.backend.exceptions.DuplicateAssetCodeException;
import com.helpdeskpro.backend.repositories.AssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private AssetService assetService;

    @Test
    @DisplayName("Deve criar um ativo com sucesso quando os dados forem válidos e o código único")
    void createAsset_Success() {
        AssetRequestDTO requestDTO = new AssetRequestDTO("NOTE-001", "Notebook", "Dell Latitude 3420", AssetStatus.INACTIVE);
        Asset savedEntity = new Asset(1L, "NOTE-001", "Notebook", "Dell Latitude 3420", AssetStatus.INACTIVE);

        when(assetRepository.existsByCode("NOTE-001")).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenReturn(savedEntity);

        AssetResponseDTO response = assetService.createAsset(requestDTO);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("NOTE-001", response.getCode());
        assertEquals("Notebook", response.getType());
        assertEquals(AssetStatus.INACTIVE, response.getStatus());

        verify(assetRepository).existsByCode("NOTE-001");
        verify(assetRepository).save(any(Asset.class));
    }

    @Test
    @DisplayName("Deve lançar DuplicateAssetCodeException ao tentar criar ativo com código já existente")
    void createAsset_DuplicateCode_ThrowsException() {
        AssetRequestDTO requestDTO = new AssetRequestDTO("NOTE-001", "Notebook", "Dell Latitude 3420", AssetStatus.INACTIVE);

        when(assetRepository.existsByCode("NOTE-001")).thenReturn(true);

        assertThrows(DuplicateAssetCodeException.class, () -> assetService.createAsset(requestDTO));

        verify(assetRepository).existsByCode("NOTE-001");
        verify(assetRepository, never()).save(any(Asset.class));
    }

    @Test
    @DisplayName("Deve retornar todos os ativos cadastrados")
    void getAllAssets_ReturnsList() {
        List<Asset> mockAssets = List.of(
                new Asset(1L, "NOTE-001", "Notebook", "Dell", AssetStatus.INACTIVE),
                new Asset(2L, "MON-001", "Monitor", "LG", AssetStatus.IN_USE)
        );

        when(assetRepository.findAll()).thenReturn(mockAssets);

        List<AssetResponseDTO> result = assetService.getAllAssets();

        assertEquals(2, result.size());
        assertEquals("NOTE-001", result.get(0).getCode());
        assertEquals("MON-001", result.get(1).getCode());
    }

    @Test
    @DisplayName("Deve buscar ativo por id com sucesso")
    void getAssetById_Success() {
        Asset mockAsset = new Asset(1L, "NOTE-001", "Notebook", "Dell", AssetStatus.INACTIVE);

        when(assetRepository.findById(1L)).thenReturn(Optional.of(mockAsset));

        AssetResponseDTO result = assetService.getAssetById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("NOTE-001", result.getCode());
    }

    @Test
    @DisplayName("Deve lançar AssetNotFoundException ao buscar por id inexistente")
    void getAssetById_NotFound_ThrowsException() {
        when(assetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AssetNotFoundException.class, () -> assetService.getAssetById(99L));
    }

    @Test
    @DisplayName("Deve atualizar um ativo com sucesso")
    void updateAsset_Success() {
        Asset existingAsset = new Asset(1L, "NOTE-001", "Notebook", "Antigo", AssetStatus.INACTIVE);
        AssetRequestDTO updateDTO = new AssetRequestDTO("NOTE-001-MOD", "Notebook", "Novo", AssetStatus.UNDER_MAINTENANCE);
        Asset updatedAsset = new Asset(1L, "NOTE-001-MOD", "Notebook", "Novo", AssetStatus.UNDER_MAINTENANCE);

        when(assetRepository.findById(1L)).thenReturn(Optional.of(existingAsset));
        when(assetRepository.existsByCodeAndIdNot("NOTE-001-MOD", 1L)).thenReturn(false);
        when(assetRepository.save(existingAsset)).thenReturn(updatedAsset);

        AssetResponseDTO result = assetService.updateAsset(1L, updateDTO);

        assertNotNull(result);
        assertEquals("NOTE-001-MOD", result.getCode());
        assertEquals(AssetStatus.UNDER_MAINTENANCE, result.getStatus());
    }

    @Test
    @DisplayName("Deve lançar DuplicateAssetCodeException ao tentar atualizar código para um já existente em outro ativo")
    void updateAsset_DuplicateCodeOnOtherAsset_ThrowsException() {
        Asset existingAsset = new Asset(1L, "NOTE-001", "Notebook", "Antigo", AssetStatus.INACTIVE);
        AssetRequestDTO updateDTO = new AssetRequestDTO("NOTE-002", "Notebook", "Novo", AssetStatus.UNDER_MAINTENANCE);

        when(assetRepository.findById(1L)).thenReturn(Optional.of(existingAsset));
        when(assetRepository.existsByCodeAndIdNot("NOTE-002", 1L)).thenReturn(true);

        assertThrows(DuplicateAssetCodeException.class, () -> assetService.updateAsset(1L, updateDTO));
        verify(assetRepository, never()).save(any(Asset.class));
    }

    @Test
    @DisplayName("Deve lançar AssetNotFoundException ao tentar atualizar ativo inexistente")
    void updateAsset_NotFound_ThrowsException() {
        AssetRequestDTO updateDTO = new AssetRequestDTO("NOTE-001", "Notebook", "Novo", AssetStatus.UNDER_MAINTENANCE);

        when(assetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AssetNotFoundException.class, () -> assetService.updateAsset(99L, updateDTO));
    }

    @Test
    @DisplayName("Deve atualizar apenas o status do ativo com sucesso")
    void updateAssetStatus_Success() {
        Asset existingAsset = new Asset(1L, "NOTE-001", "Notebook", "Dell", AssetStatus.INACTIVE);
        AssetStatusUpdateDTO statusDTO = new AssetStatusUpdateDTO(AssetStatus.IN_USE);
        Asset updatedAsset = new Asset(1L, "NOTE-001", "Notebook", "Dell", AssetStatus.IN_USE);

        when(assetRepository.findById(1L)).thenReturn(Optional.of(existingAsset));
        when(assetRepository.save(existingAsset)).thenReturn(updatedAsset);

        AssetResponseDTO result = assetService.updateAssetStatus(1L, statusDTO);

        assertNotNull(result);
        assertEquals(AssetStatus.IN_USE, result.getStatus());
    }

    @Test
    @DisplayName("Deve excluir ativo existente com sucesso")
    void deleteAsset_Success() {
        when(assetRepository.existsById(1L)).thenReturn(true);

        assetService.deleteAsset(1L);

        verify(assetRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Deve lançar AssetNotFoundException ao tentar excluir ativo inexistente")
    void deleteAsset_NotFound_ThrowsException() {
        when(assetRepository.existsById(99L)).thenReturn(false);

        assertThrows(AssetNotFoundException.class, () -> assetService.deleteAsset(99L));
        verify(assetRepository, never()).deleteById(any());
    }
}
