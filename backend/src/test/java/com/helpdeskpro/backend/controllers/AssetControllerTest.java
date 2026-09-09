package com.helpdeskpro.backend.controllers;

import com.helpdeskpro.backend.domain.AssetStatus;
import com.helpdeskpro.backend.dto.AssetRequestDTO;
import com.helpdeskpro.backend.dto.AssetResponseDTO;
import com.helpdeskpro.backend.dto.AssetStatusUpdateDTO;
import com.helpdeskpro.backend.services.AssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetControllerTest {

    @Mock
    private AssetService assetService;

    @InjectMocks
    private AssetController assetController;

    @Test
    @DisplayName("POST /assets - Deve retornar HTTP 201 Created ao criar ativo")
    void createAsset_Returns201() {
        AssetRequestDTO request = new AssetRequestDTO("NOTE-001", "Notebook", "Dell", AssetStatus.INACTIVE);
        AssetResponseDTO response = new AssetResponseDTO(1L, "NOTE-001", "Notebook", "Dell", AssetStatus.INACTIVE);

        when(assetService.createAsset(request)).thenReturn(response);

        ResponseEntity<AssetResponseDTO> result = assetController.createAsset(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(response, result.getBody());
    }

    @Test
    @DisplayName("GET /assets - Deve retornar HTTP 200 OK com lista de ativos")
    void getAllAssets_Returns200() {
        List<AssetResponseDTO> list = List.of(new AssetResponseDTO(1L, "NOTE-001", "Notebook", "Dell", AssetStatus.INACTIVE));
        when(assetService.getAllAssets()).thenReturn(list);

        ResponseEntity<List<AssetResponseDTO>> result = assetController.getAllAssets();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(list, result.getBody());
    }

    @Test
    @DisplayName("GET /assets/{id} - Deve retornar HTTP 200 OK ao buscar ativo por id")
    void getAssetById_Returns200() {
        AssetResponseDTO response = new AssetResponseDTO(1L, "NOTE-001", "Notebook", "Dell", AssetStatus.INACTIVE);
        when(assetService.getAssetById(1L)).thenReturn(response);

        ResponseEntity<AssetResponseDTO> result = assetController.getAssetById(1L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
    }

    @Test
    @DisplayName("PUT /assets/{id} - Deve retornar HTTP 200 OK ao atualizar ativo")
    void updateAsset_Returns200() {
        AssetRequestDTO request = new AssetRequestDTO("NOTE-001", "Notebook", "Dell", AssetStatus.UNDER_MAINTENANCE);
        AssetResponseDTO response = new AssetResponseDTO(1L, "NOTE-001", "Notebook", "Dell", AssetStatus.UNDER_MAINTENANCE);

        when(assetService.updateAsset(1L, request)).thenReturn(response);

        ResponseEntity<AssetResponseDTO> result = assetController.updateAsset(1L, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
    }

    @Test
    @DisplayName("PATCH /assets/{id}/status - Deve retornar HTTP 200 OK ao atualizar status")
    void updateAssetStatus_Returns200() {
        AssetStatusUpdateDTO request = new AssetStatusUpdateDTO(AssetStatus.IN_USE);
        AssetResponseDTO response = new AssetResponseDTO(1L, "NOTE-001", "Notebook", "Dell", AssetStatus.IN_USE);

        when(assetService.updateAssetStatus(1L, request)).thenReturn(response);

        ResponseEntity<AssetResponseDTO> result = assetController.updateAssetStatus(1L, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
    }

    @Test
    @DisplayName("DELETE /assets/{id} - Deve retornar HTTP 204 No Content ao excluir")
    void deleteAsset_Returns204() {
        ResponseEntity<Void> result = assetController.deleteAsset(1L);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(assetService).deleteAsset(1L);
    }
}
