package com.rolliedev.ticketflow.exception;

import com.rolliedev.ticketflow.entity.enums.TicketStatus;

// For invalid state transitions — maps to HTTP 422 (Unprocessable Entity)
public class InvalidStatusTransitionException extends TicketFlowException {

    private final TicketStatus currentStatus;
    private final TicketStatus targetStatus;

    public InvalidStatusTransitionException(TicketStatus currentStatus, TicketStatus targetStatus) {
        super("Cannot transition from %s to %s".formatted(currentStatus, targetStatus));
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }
}
