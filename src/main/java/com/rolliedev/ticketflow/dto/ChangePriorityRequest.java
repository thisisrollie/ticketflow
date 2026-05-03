package com.rolliedev.ticketflow.dto;

import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class ChangePriorityRequest {
    @NotNull
    TicketPriority newPriority;
}
