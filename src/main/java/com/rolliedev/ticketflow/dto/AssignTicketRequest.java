package com.rolliedev.ticketflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Value;

@Value
public class AssignTicketRequest {
    @NotNull
    @Positive
    Integer assigneeId;
}
