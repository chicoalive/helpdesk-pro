package com.helpdeskpro.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class TicketCommentRequestDTO {

    @NotBlank(message = "O conteúdo do comentário é obrigatório")
    private String content;

    public TicketCommentRequestDTO() {
    }

    public TicketCommentRequestDTO(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
