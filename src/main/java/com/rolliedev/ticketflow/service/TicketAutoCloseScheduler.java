package com.rolliedev.ticketflow.service;

import com.rolliedev.ticketflow.config.TicketProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketAutoCloseScheduler {

    private final TicketService ticketService;
    private final TicketProperties ticketProperties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${app.ticket.auto-close-check-delay-hours:24}",
            timeUnit = TimeUnit.HOURS
    )
    public void autoCloseTickets() {
        Instant threshold = Instant.now(clock)
                .minus(ticketProperties.autoCloseAfterDays(), ChronoUnit.DAYS);

        int closedTicketsCount = ticketService.closeResolvedTicketsOlderThan(threshold);

        if (closedTicketsCount > 0) {
            log.info("Auto-closed {} tickets", closedTicketsCount);
        }
    }
}
