package com.rolliedev.ticketflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(@NotBlank @Size(max = 128) String title,
                                  @NotBlank @Size(max = 4000) String description) {
}
