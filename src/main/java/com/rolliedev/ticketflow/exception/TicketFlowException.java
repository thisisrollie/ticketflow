package com.rolliedev.ticketflow.exception;

public abstract class TicketFlowException extends RuntimeException {

    protected TicketFlowException(String message) {
        super(message);
    }
}
