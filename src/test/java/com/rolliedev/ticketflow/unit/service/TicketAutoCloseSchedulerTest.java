package com.rolliedev.ticketflow.unit.service;

import com.rolliedev.ticketflow.config.TicketProperties;
import com.rolliedev.ticketflow.service.TicketAutoCloseScheduler;
import com.rolliedev.ticketflow.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketAutoCloseSchedulerTest {

    @Mock
    private TicketService ticketService;

    private TicketAutoCloseScheduler scheduler;

    @Test
    void shouldAutoCloseResolvedTicketsOlderThanConfiguredThreshold() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-05-20T10:00:00Z"),
                ZoneOffset.UTC
        );

        TicketProperties ticketProperties = new TicketProperties(4);

        scheduler = new TicketAutoCloseScheduler(
                ticketService,
                ticketProperties,
                fixedClock
        );

        Instant expectedThreshold = fixedClock.instant()
                .minus(ticketProperties.autoCloseAfterDays(), ChronoUnit.DAYS);

        doReturn(3).when(ticketService).closeResolvedTicketsOlderThan(expectedThreshold);

        scheduler.autoCloseTickets();

        verify(ticketService).closeResolvedTicketsOlderThan(expectedThreshold);
    }
}