package com.rolliedev.ticketflow.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.ticket")
@Validated
public record TicketProperties(
        @NotNull
        @Min(1)
        Integer autoCloseAfterDays
) {
}
