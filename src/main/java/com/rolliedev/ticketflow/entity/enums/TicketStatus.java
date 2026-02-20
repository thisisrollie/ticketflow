package com.rolliedev.ticketflow.entity.enums;

import com.rolliedev.ticketflow.exception.InvalidStatusTransitionException;

import java.util.EnumSet;
import java.util.Map;

public enum TicketStatus {
    NEW, IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED;

    private static final Map<TicketStatus, EnumSet<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
            NEW, EnumSet.of(IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(WAITING_CUSTOMER, RESOLVED),
            WAITING_CUSTOMER, EnumSet.of(IN_PROGRESS, RESOLVED),
            RESOLVED, EnumSet.of(IN_PROGRESS, CLOSED),
            CLOSED, EnumSet.noneOf(TicketStatus.class)
    );

    public boolean canTransitionTo(TicketStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        EnumSet<TicketStatus> allowedTargets = ALLOWED_TRANSITIONS.get(this);
        return allowedTargets != null && allowedTargets.contains(targetStatus);
    }

    public void assertCanTransitionTo(TicketStatus targetStatus) {
        if (!canTransitionTo(targetStatus)) {
            throw new InvalidStatusTransitionException(this, targetStatus);
        }
    }

    public EnumSet<TicketStatus> getAllowedTransitions() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(TicketStatus.class));
    }
}
