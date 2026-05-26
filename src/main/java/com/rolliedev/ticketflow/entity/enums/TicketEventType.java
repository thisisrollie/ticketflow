package com.rolliedev.ticketflow.entity.enums;

public enum TicketEventType {
    CREATED,
    ASSIGNED,
    STATUS_CHANGED,
    PRIORITY_CHANGED,
    COMMENTED,
    COMMENT_DELETED,
    FIRST_RESPONSE_SLA_BREACHED,
    RESOLUTION_SLA_BREACHED;
}
