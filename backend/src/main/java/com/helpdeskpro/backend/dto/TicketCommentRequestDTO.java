package com.helpdeskpro.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TicketCommentRequestDTO {

    @NotBlank(message = "O conteúdo do comentário é obrigatório.")
    private String content;

    @NotNull(message = "O ID do autor (userId) é obrigatório.")
    private Long userId;

    public TicketCommentRequestDTO() {
    }

    public TicketCommentRequestDTO(String content, Long userId) {
        this.content = content;
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
