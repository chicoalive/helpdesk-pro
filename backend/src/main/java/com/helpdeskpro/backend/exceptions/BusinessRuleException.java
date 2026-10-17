package com.helpdeskpro.backend.exceptions;

public class BusinessRuleException extends RuntimeException {

    private final Long existingTicketId;

    public BusinessRuleException(String message) {
        super(message);
        this.existingTicketId = null;
    }

    public BusinessRuleException(String message, Long existingTicketId) {
        super(message);
        this.existingTicketId = existingTicketId;
    }

    public Long getExistingTicketId() {
        return existingTicketId;
    }
}
