package com.rolliedev.ticketflow.dto;

import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;

import java.time.Instant;

public record TicketResponse(Long id,
                             String title,
                             String description,
                             TicketStatus status,
                             TicketPriority priority,
                             UserSummary createdBy,
                             UserSummary assignedTo,
                             Instant createdAt,
                             Instant modifiedAt,
                             Instant resolvedAt) {
}
