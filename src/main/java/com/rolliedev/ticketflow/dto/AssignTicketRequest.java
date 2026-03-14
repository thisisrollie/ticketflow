package com.rolliedev.ticketflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class AssignTicketRequest {
    @NotNull
    Integer actorId;
    @NotNull
    Integer assigneeId;
}
