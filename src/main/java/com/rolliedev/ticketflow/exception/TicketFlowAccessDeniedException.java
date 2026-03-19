package com.rolliedev.ticketflow.exception;

// For permission/access violations — maps to HTTP 403
public class TicketFlowAccessDeniedException extends TicketFlowException {

    public TicketFlowAccessDeniedException(String message) {
        super(message);
    }
}
