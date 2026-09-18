package com.helpdeskpro.backend.controllers;

import com.helpdeskpro.backend.dto.TicketCommentRequestDTO;
import com.helpdeskpro.backend.dto.TicketCommentResponseDTO;
import com.helpdeskpro.backend.services.TicketCommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/comments")
public class TicketCommentController {

    private final TicketCommentService ticketCommentService;

    public TicketCommentController(TicketCommentService ticketCommentService) {
        this.ticketCommentService = ticketCommentService;
    }

    @PostMapping
    public ResponseEntity<TicketCommentResponseDTO> createComment(
            @PathVariable Long ticketId,
            @RequestBody @Valid TicketCommentRequestDTO dto) {
        TicketCommentResponseDTO created = ticketCommentService.createComment(ticketId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<TicketCommentResponseDTO>> getCommentsByTicket(
            @PathVariable Long ticketId) {
        List<TicketCommentResponseDTO> comments = ticketCommentService.getCommentsByTicket(ticketId);
        return ResponseEntity.ok(comments);
    }
}
