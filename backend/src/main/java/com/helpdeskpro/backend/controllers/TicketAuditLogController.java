package com.helpdeskpro.backend.controllers;

import com.helpdeskpro.backend.dto.TicketAuditLogResponseDTO;
import com.helpdeskpro.backend.services.TicketAuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/audit")
public class TicketAuditLogController {

    private final TicketAuditLogService ticketAuditLogService;

    public TicketAuditLogController(TicketAuditLogService ticketAuditLogService) {
        this.ticketAuditLogService = ticketAuditLogService;
    }

    @GetMapping
    public ResponseEntity<List<TicketAuditLogResponseDTO>> getAuditByTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketAuditLogService.getAuditByTicket(ticketId));
    }
}
