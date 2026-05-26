package com.rolliedev.ticketflow.dto;

import com.rolliedev.ticketflow.entity.enums.TicketPriority;

public record SlaPolicy(TicketPriority priority,
                        int firstResponseDueMinutes,
                        int resolutionDueMinutes) {
}
