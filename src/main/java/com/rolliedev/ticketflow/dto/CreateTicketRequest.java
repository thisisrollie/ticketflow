package com.rolliedev.ticketflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(@NotBlank @Size(max = 128) String title,
                                  @NotBlank String description,
                                  @NotNull Integer creatorId) {
}
