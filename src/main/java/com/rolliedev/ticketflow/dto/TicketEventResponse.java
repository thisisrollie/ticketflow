package com.rolliedev.ticketflow.dto;

import com.rolliedev.ticketflow.entity.enums.TicketEventType;

import java.time.Instant;
import java.util.Map;

public record TicketEventResponse(Long id,
                                  UserSummary actor,
                                  TicketEventType eventType,
                                  Map<String, Object> payload,
                                  Instant createdAt) {
}
