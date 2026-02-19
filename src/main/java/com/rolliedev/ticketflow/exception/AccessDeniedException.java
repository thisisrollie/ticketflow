package com.rolliedev.ticketflow.exception;

// For permission/access violations — maps to HTTP 403
public class AccessDeniedException extends TicketFlowException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
