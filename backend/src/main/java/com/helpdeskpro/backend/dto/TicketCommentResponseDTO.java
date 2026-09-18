package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.TicketComment;

import java.time.LocalDateTime;

public class TicketCommentResponseDTO {

    private Long id;
    private String content;
    private LocalDateTime createdAt;
    private Long ticketId;
    private Long authorId;
    private String authorName;
    private UserRole authorRole;

    public TicketCommentResponseDTO() {
    }

    public TicketCommentResponseDTO(Long id, String content, LocalDateTime createdAt, Long ticketId,
                                  Long authorId, String authorName, UserRole authorRole) {
        this.id = id;
        this.content = content;
        this.createdAt = createdAt;
        this.ticketId = ticketId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.authorRole = authorRole;
    }

    public static TicketCommentResponseDTO fromEntity(TicketComment comment) {
        return new TicketCommentResponseDTO(
                comment.getId(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getTicket() != null ? comment.getTicket().getId() : null,
                comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                comment.getAuthor() != null ? comment.getAuthor().getName() : null,
                comment.getAuthor() != null ? comment.getAuthor().getRole() : null
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public UserRole getAuthorRole() {
        return authorRole;
    }

    public void setAuthorRole(UserRole authorRole) {
        this.authorRole = authorRole;
    }
}
