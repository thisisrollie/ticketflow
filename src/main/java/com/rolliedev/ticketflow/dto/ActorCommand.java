package com.rolliedev.ticketflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class ActorCommand {
    @NotNull Integer actorId;
}
