package com.helpdeskpro.backend.controllers;

import com.helpdeskpro.backend.dto.DashboardResponseDTO;
import com.helpdeskpro.backend.services.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardResponseDTO> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }
}
