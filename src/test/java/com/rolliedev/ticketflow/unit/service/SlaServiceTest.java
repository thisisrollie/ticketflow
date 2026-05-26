package com.rolliedev.ticketflow.unit.service;

import com.rolliedev.ticketflow.dto.SlaPolicy;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.SlaStatus;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.service.SlaService;
import com.rolliedev.ticketflow.service.TicketEventService;
import com.rolliedev.ticketflow.sla.SlaPolicyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class SlaServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-05-10T10:00:00Z");
    private static final Instant RESPONSE_DEADLINE = Instant.parse("2026-05-11T10:00:00Z");
    private static final Instant RESOLUTION_DEADLINE = Instant.parse("2026-05-13T10:00:00Z");

    @Mock
    private SlaPolicyProvider policyProvider;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketEventService eventService;

    @InjectMocks
    private SlaService slaService;

    @Test
    void shouldInitializeSlaForNewTicket() {
        TicketEntity ticket = TicketEntity.builder()
                .priority(TicketPriority.MEDIUM)
                .build();
        ticket.setCreatedAt(CREATED_AT);

        SlaPolicy mediumPolicy = getSlaPolicy(TicketPriority.MEDIUM);
        doReturn(mediumPolicy).when(policyProvider).getSlaPolicy(TicketPriority.MEDIUM);

        slaService.initializeSlaForNewTicket(ticket);

        assertThat(ticket.getFirstResponseDeadline()).isEqualTo(CREATED_AT.plus(mediumPolicy.firstResponseDueMinutes(), ChronoUnit.MINUTES));
        assertThat(ticket.getResolutionDeadline()).isEqualTo(CREATED_AT.plus(mediumPolicy.resolutionDueMinutes(), ChronoUnit.MINUTES));
        assertThat(ticket.getResponseSlaStatus()).isSameAs(SlaStatus.ON_TRACK);
        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.ON_TRACK);
        assertThat(ticket.getFirstRespondedAt()).isNull();
        assertThat(ticket.getResolvedAt()).isNull();

        verify(policyProvider).getSlaPolicy(TicketPriority.MEDIUM);
    }

    @Test
    void shouldThrowExceptionWhenInitializingSlaAndTicketCreationTimeIsNotSet() {
        TicketEntity ticket = TicketEntity.builder()
                .priority(TicketPriority.MEDIUM)
                .build();

        assertThatThrownBy(() -> slaService.initializeSlaForNewTicket(ticket))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ticket creation time is not set");
    }

    @Test
    void shouldMarkResponseSlaAsMetWhenFirstResponseIsBeforeDeadline() {
        UserEntity actor = mock(UserEntity.class);

        TicketEntity ticket = ticketWithResponseSla(
                SlaStatus.ON_TRACK,
                RESPONSE_DEADLINE,
                RESPONSE_DEADLINE.minus(1, ChronoUnit.HOURS)
        );

        slaService.evaluateFirstResponse(ticket, actor);

        assertThat(ticket.getResponseSlaStatus()).isSameAs(SlaStatus.MET);

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldMarkResponseSlaAsMetWhenFirstResponseIsExactlyAtDeadline() {
        UserEntity actor = mock(UserEntity.class);

        TicketEntity ticket = ticketWithResponseSla(
                SlaStatus.ON_TRACK,
                RESPONSE_DEADLINE,
                RESPONSE_DEADLINE
        );

        slaService.evaluateFirstResponse(ticket, actor);

        assertThat(ticket.getResponseSlaStatus()).isSameAs(SlaStatus.MET);

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldMarkResponseSlaAsBreachedAndRecordEventWhenFirstResponseIsAfterDeadline() {
        UserEntity actor = mock(UserEntity.class);

        TicketEntity ticket = ticketWithResponseSla(
                SlaStatus.ON_TRACK,
                RESPONSE_DEADLINE,
                RESPONSE_DEADLINE.plus(1, ChronoUnit.SECONDS)
        );

        slaService.evaluateFirstResponse(ticket, actor);

        assertThat(ticket.getResponseSlaStatus()).isSameAs(SlaStatus.BREACHED);

        verify(eventService).recordFirstResponseSlaBreachedEvent(ticket, actor);
    }

    @Test
    void shouldDoNothingWhenResponseSlaStatusIsNotOnTrack() {
        UserEntity actor = mock(UserEntity.class);

        TicketEntity ticket = ticketWithResponseSla(
                SlaStatus.BREACHED,
                RESPONSE_DEADLINE,
                RESPONSE_DEADLINE.minus(1, ChronoUnit.HOURS)
        );

        slaService.evaluateFirstResponse(ticket, actor);

        assertThat(ticket.getResponseSlaStatus()).isSameAs(SlaStatus.BREACHED);

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldThrowExceptionWhenFirstResponseTimestampIsNotSet() {
        TicketEntity ticket = TicketEntity.builder()
                .responseSlaStatus(SlaStatus.ON_TRACK)
                .build();
        ticket.setFirstResponseDeadline(RESPONSE_DEADLINE);

        assertThatThrownBy(() -> slaService.evaluateFirstResponse(ticket, mock(UserEntity.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("First response timestamp is not set");
    }

    @Test
    void shouldThrowExceptionWhenFirstResponseDeadlineIsNotSet() {
        TicketEntity ticket = TicketEntity.builder()
                .responseSlaStatus(SlaStatus.ON_TRACK)
                .build();
        ticket.setFirstRespondedAt(RESPONSE_DEADLINE.minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> slaService.evaluateFirstResponse(ticket, mock(UserEntity.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SLA deadline is not set");
    }

    @Test
    void shouldPauseResolutionSlaClockWhenPauseTimeIsBeforeDeadline() {
        UserEntity actor = mock(UserEntity.class);
        Instant pausedAt = RESOLUTION_DEADLINE.minus(1, ChronoUnit.HOURS);

        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.ON_TRACK, RESOLUTION_DEADLINE);

        slaService.pauseResolutionSlaClock(ticket, actor, pausedAt);

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.PAUSED);
        assertThat(ticket.getResolutionSlaPausedAt()).isEqualTo(pausedAt);

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldPauseResolutionSlaClockWhenPauseTimeIsExactlyAtDeadline() {
        UserEntity actor = mock(UserEntity.class);

        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.ON_TRACK, RESOLUTION_DEADLINE);

        slaService.pauseResolutionSlaClock(ticket, actor, RESOLUTION_DEADLINE);

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.PAUSED);
        assertThat(ticket.getResolutionSlaPausedAt()).isEqualTo(RESOLUTION_DEADLINE);

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldMarkResolutionSlaAsBreachedWhenPauseTimeIsAfterDeadline() {
        UserEntity actor = mock(UserEntity.class);
        Instant pausedAt = RESOLUTION_DEADLINE.plus(1, ChronoUnit.SECONDS);

        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.ON_TRACK, RESOLUTION_DEADLINE);

        slaService.pauseResolutionSlaClock(ticket, actor, pausedAt);

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.BREACHED);
        assertThat(ticket.getResolutionSlaPausedAt()).isNull();

        verify(eventService).recordResolutionSlaBreachedEvent(ticket, actor);
    }

    @ParameterizedTest
    @EnumSource(value = SlaStatus.class, names = {"PAUSED", "MET", "BREACHED"})
    void shouldDoNothingWhenTryingToPauseResolutionSlaThatIsNotOnTrack(SlaStatus currentStatus) {
        UserEntity actor = mock(UserEntity.class);

        TicketEntity ticket = ticketWithResolutionSla(currentStatus, RESOLUTION_DEADLINE);
        Instant originalPausedAt = Instant.parse("2026-05-12T10:00:00Z");
        ticket.setResolutionSlaPausedAt(originalPausedAt);

        slaService.pauseResolutionSlaClock(ticket, actor, RESOLUTION_DEADLINE.minus(1, ChronoUnit.HOURS));

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(currentStatus);
        assertThat(ticket.getResolutionSlaPausedAt()).isEqualTo(originalPausedAt);

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldThrowExceptionWhenPauseTimeIsNull() {
        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.ON_TRACK, RESOLUTION_DEADLINE);

        assertThatThrownBy(() -> slaService.pauseResolutionSlaClock(ticket, mock(UserEntity.class), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pause time cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenResolutionDeadlineIsNotSetOnPause() {
        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.ON_TRACK, null);

        assertThatThrownBy(() -> slaService.pauseResolutionSlaClock(ticket, mock(UserEntity.class), CREATED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Resolution SLA deadline is not set");
    }

    @Test
    void shouldResumeResolutionSlaClockAndExtendDeadlineByPausedDuration() {
        Instant pausedAt = Instant.parse("2026-05-12T10:00:00Z");
        Instant resumedAt = Instant.parse("2026-05-12T15:30:00Z");

        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);
        ticket.setResolutionSlaPausedAt(pausedAt);

        slaService.resumeResolutionSlaClock(ticket, resumedAt);

        assertThat(ticket.getResolutionDeadline()).isEqualTo(RESOLUTION_DEADLINE.plus(Duration.between(pausedAt, resumedAt)));
        assertThat(ticket.getResolutionSlaPausedAt()).isNull();
        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.ON_TRACK);
    }

    @Test
    void shouldResumeResolutionSlaClockWhenResumeTimeEqualsPauseTime() {
        Instant pausedAt = Instant.parse("2026-05-12T10:00:00Z");

        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);
        ticket.setResolutionSlaPausedAt(pausedAt);

        slaService.resumeResolutionSlaClock(ticket, pausedAt);

        assertThat(ticket.getResolutionDeadline()).isEqualTo(RESOLUTION_DEADLINE);
        assertThat(ticket.getResolutionSlaPausedAt()).isNull();
        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.ON_TRACK);
    }

    @ParameterizedTest
    @EnumSource(value = SlaStatus.class, names = {"ON_TRACK", "MET", "BREACHED"})
    void shouldDoNothingWhenTryingToResumeResolutionSlaThatIsNotPaused(SlaStatus currentStatus) {
        TicketEntity ticket = ticketWithResolutionSla(currentStatus, RESOLUTION_DEADLINE);

        slaService.resumeResolutionSlaClock(ticket, CREATED_AT);

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(currentStatus);
        assertThat(ticket.getResolutionDeadline()).isEqualTo(RESOLUTION_DEADLINE);
    }

    @Test
    void shouldThrowExceptionWhenResumeTimeIsNull() {
        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);
        ticket.setResolutionSlaPausedAt(CREATED_AT);

        assertThatThrownBy(() -> slaService.resumeResolutionSlaClock(ticket, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Resume time cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenPauseTimeIsNotSetOnResume() {
        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);

        assertThatThrownBy(() -> slaService.resumeResolutionSlaClock(ticket, CREATED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Pause time is not set");
    }

    @Test
    void shouldThrowExceptionWhenResolutionDeadlineIsNotSetOnResume() {
        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, null);
        ticket.setResolutionSlaPausedAt(CREATED_AT);

        assertThatThrownBy(() -> slaService.resumeResolutionSlaClock(ticket, CREATED_AT.plus(1, ChronoUnit.HOURS)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Resolution SLA deadline is not set");
    }

    @Test
    void shouldThrowExceptionWhenResumeTimeIsBeforePauseTime() {
        Instant pausedAt = Instant.parse("2026-05-12T10:00:00Z");
        Instant resumedAt = pausedAt.minus(1, ChronoUnit.SECONDS);

        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);
        ticket.setResolutionSlaPausedAt(pausedAt);

        assertThatThrownBy(() -> slaService.resumeResolutionSlaClock(ticket, resumedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Resume time cannot be before the pause time");

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.PAUSED);
        assertThat(ticket.getResolutionSlaPausedAt()).isEqualTo(pausedAt);
        assertThat(ticket.getResolutionDeadline()).isEqualTo(RESOLUTION_DEADLINE);
    }

    @Test
    void shouldRecalculateActiveDeadlinesWhenPriorityIsChangedWithinTriageWindow() {
        Instant changedAt = CREATED_AT.plus(2, ChronoUnit.HOURS);

        TicketEntity ticket = ticketForPriorityChange(TicketPriority.LOW, SlaStatus.ON_TRACK, SlaStatus.ON_TRACK);

        SlaPolicy lowPolicy = getSlaPolicy(TicketPriority.LOW);
        doReturn(lowPolicy).when(policyProvider).getSlaPolicy(TicketPriority.LOW);

        slaService.updateDeadlinesAfterPriorityChange(ticket, changedAt);

        assertThat(ticket.getFirstResponseDeadline()).isEqualTo(changedAt.plus(lowPolicy.firstResponseDueMinutes(), ChronoUnit.MINUTES));
        assertThat(ticket.getResolutionDeadline()).isEqualTo(changedAt.plus(lowPolicy.resolutionDueMinutes(), ChronoUnit.MINUTES));
    }

    @Test
    void shouldRecalculateActiveDeadlinesWhenPriorityIsChangedExactlyAtTriageWindowBoundary() {
        Instant changedAt = CREATED_AT.plus(4, ChronoUnit.HOURS);

        TicketEntity ticket = ticketForPriorityChange(TicketPriority.HIGH, SlaStatus.ON_TRACK, SlaStatus.ON_TRACK);

        SlaPolicy highPolicy = getSlaPolicy(TicketPriority.HIGH);
        doReturn(highPolicy).when(policyProvider).getSlaPolicy(TicketPriority.HIGH);

        slaService.updateDeadlinesAfterPriorityChange(ticket, changedAt);

        assertThat(ticket.getFirstResponseDeadline()).isEqualTo(changedAt.plus(highPolicy.firstResponseDueMinutes(), ChronoUnit.MINUTES));
        assertThat(ticket.getResolutionDeadline()).isEqualTo(changedAt.plus(highPolicy.resolutionDueMinutes(), ChronoUnit.MINUTES));
    }

    @Test
    void shouldNotExtendDeadlinesWhenPriorityIsChangedAfterTriageWindow() {
        Instant changedAt = CREATED_AT.plus(6, ChronoUnit.HOURS);

        Instant originalFirstResponseDeadline = CREATED_AT.plus(1, ChronoUnit.DAYS);
        Instant originalResolutionDeadline = CREATED_AT.plus(3, ChronoUnit.DAYS);

        TicketEntity ticket = ticketForPriorityChange(TicketPriority.LOW, SlaStatus.ON_TRACK, SlaStatus.ON_TRACK);
        ticket.setFirstResponseDeadline(originalFirstResponseDeadline);
        ticket.setResolutionDeadline(originalResolutionDeadline);

        SlaPolicy lowPolicy = getSlaPolicy(TicketPriority.LOW);
        doReturn(lowPolicy).when(policyProvider).getSlaPolicy(TicketPriority.LOW);

        slaService.updateDeadlinesAfterPriorityChange(ticket, changedAt);

        assertThat(ticket.getFirstResponseDeadline()).isEqualTo(originalFirstResponseDeadline);
        assertThat(ticket.getResolutionDeadline()).isEqualTo(originalResolutionDeadline);
    }

    @Test
    void shouldShortenDeadlinesWhenPriorityIsChangedAfterTriageWindow() {
        Instant changedAt = CREATED_AT.plus(6, ChronoUnit.HOURS);

        TicketEntity ticket = ticketForPriorityChange(TicketPriority.CRITICAL, SlaStatus.ON_TRACK, SlaStatus.ON_TRACK);
        ticket.setFirstResponseDeadline(CREATED_AT.plus(3, ChronoUnit.DAYS));
        ticket.setResolutionDeadline(CREATED_AT.plus(7, ChronoUnit.DAYS));

        SlaPolicy criticalPolicy = getSlaPolicy(TicketPriority.CRITICAL);
        doReturn(criticalPolicy).when(policyProvider).getSlaPolicy(TicketPriority.CRITICAL);

        slaService.updateDeadlinesAfterPriorityChange(ticket, changedAt);

        assertThat(ticket.getFirstResponseDeadline()).isEqualTo(changedAt.plus(criticalPolicy.firstResponseDueMinutes(), ChronoUnit.MINUTES));
        assertThat(ticket.getResolutionDeadline()).isEqualTo(changedAt.plus(criticalPolicy.resolutionDueMinutes(), ChronoUnit.MINUTES));
    }

    @Test
    void shouldNotChangeResponseDeadlineWhenResponseSlaIsAlreadyMet() {
        Instant changedAt = CREATED_AT.plus(1, ChronoUnit.HOURS);
        Instant originalFirstResponseDeadline = CREATED_AT.plus(1, ChronoUnit.DAYS);

        TicketEntity ticket = ticketForPriorityChange(TicketPriority.LOW, SlaStatus.MET, SlaStatus.ON_TRACK);
        ticket.setFirstResponseDeadline(originalFirstResponseDeadline);

        SlaPolicy lowPolicy = getSlaPolicy(TicketPriority.LOW);
        doReturn(lowPolicy).when(policyProvider).getSlaPolicy(TicketPriority.LOW);

        slaService.updateDeadlinesAfterPriorityChange(ticket, changedAt);

        assertThat(ticket.getFirstResponseDeadline()).isEqualTo(originalFirstResponseDeadline);
        assertThat(ticket.getResolutionDeadline()).isEqualTo(changedAt.plus(lowPolicy.resolutionDueMinutes(), ChronoUnit.MINUTES));
    }

    @Test
    void shouldNotChangeResolutionDeadlineWhenResolutionSlaIsAlreadyBreached() {
        Instant changedAt = CREATED_AT.plus(1, ChronoUnit.HOURS);
        Instant originalResolutionDeadline = CREATED_AT.plus(3, ChronoUnit.DAYS);

        TicketEntity ticket = ticketForPriorityChange(TicketPriority.LOW, SlaStatus.ON_TRACK, SlaStatus.BREACHED);
        ticket.setResolutionDeadline(originalResolutionDeadline);

        SlaPolicy lowPolicy = getSlaPolicy(TicketPriority.LOW);
        doReturn(lowPolicy).when(policyProvider).getSlaPolicy(TicketPriority.LOW);

        slaService.updateDeadlinesAfterPriorityChange(ticket, changedAt);

        assertThat(ticket.getFirstResponseDeadline()).isEqualTo(changedAt.plus(lowPolicy.firstResponseDueMinutes(), ChronoUnit.MINUTES));
        assertThat(ticket.getResolutionDeadline()).isEqualTo(originalResolutionDeadline);
    }

    @Test
    void shouldNotChangeResolutionDeadlineWhenResolutionSlaIsPaused() {
        Instant changedAt = CREATED_AT.plus(1, ChronoUnit.HOURS);
        Instant originalResolutionDeadline = CREATED_AT.plus(3, ChronoUnit.DAYS);

        TicketEntity ticket = ticketForPriorityChange(TicketPriority.LOW, SlaStatus.ON_TRACK, SlaStatus.PAUSED);
        ticket.setResolutionDeadline(originalResolutionDeadline);
        ticket.setResolutionSlaPausedAt(CREATED_AT.plus(30, ChronoUnit.MINUTES));

        SlaPolicy lowPolicy = getSlaPolicy(TicketPriority.LOW);
        doReturn(lowPolicy).when(policyProvider).getSlaPolicy(TicketPriority.LOW);

        slaService.updateDeadlinesAfterPriorityChange(ticket, changedAt);

        assertThat(ticket.getFirstResponseDeadline()).isEqualTo(changedAt.plus(lowPolicy.firstResponseDueMinutes(), ChronoUnit.MINUTES));
        assertThat(ticket.getResolutionDeadline()).isEqualTo(originalResolutionDeadline);
        assertThat(ticket.getResolutionSlaPausedAt()).isEqualTo(CREATED_AT.plus(30, ChronoUnit.MINUTES));
    }

    @Test
    void shouldThrowExceptionWhenPriorityChangeTimeIsNull() {
        TicketEntity ticket = ticketForPriorityChange(TicketPriority.LOW, SlaStatus.ON_TRACK, SlaStatus.ON_TRACK);

        assertThatThrownBy(() -> slaService.updateDeadlinesAfterPriorityChange(ticket, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Priority change time cannot be null");
    }

    @Test
    void shouldSetResolutionSlaToOnTrackWhenResolvedTicketIsReopenedBeforeDeadlineByInternalUser() {
        UserEntity actor = mock(UserEntity.class);
        Instant reopenedAt = RESOLUTION_DEADLINE.minus(1, ChronoUnit.HOURS);

        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);
        ticket.setResolvedAt(Instant.parse("2026-05-12T09:00:00Z"));
        ticket.setResolutionSlaPausedAt(Instant.parse("2026-05-12T09:00:00Z"));

        slaService.handleResolvedTicketReopenedByInternalUser(ticket, actor, reopenedAt);

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.ON_TRACK);
        assertThat(ticket.getResolutionSlaPausedAt()).isNull();

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldSetResolutionSlaToOnTrackWhenResolvedTicketIsReopenedExactlyAtDeadlineByInternalUser() {
        UserEntity actor = mock(UserEntity.class);

        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);
        ticket.setResolvedAt(Instant.parse("2026-05-12T09:00:00Z"));
        ticket.setResolutionSlaPausedAt(Instant.parse("2026-05-12T09:00:00Z"));

        slaService.handleResolvedTicketReopenedByInternalUser(ticket, actor, RESOLUTION_DEADLINE);

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.ON_TRACK);
        assertThat(ticket.getResolutionSlaPausedAt()).isNull();

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldMarkResolutionSlaAsBreachedWhenResolvedTicketIsReopenedAfterDeadlineByInternalUser() {
        UserEntity actor = mock(UserEntity.class);
        Instant reopenedAt = RESOLUTION_DEADLINE.plus(1, ChronoUnit.SECONDS);

        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);
        ticket.setResolvedAt(Instant.parse("2026-05-12T09:00:00Z"));
        ticket.setResolutionSlaPausedAt(Instant.parse("2026-05-12T09:00:00Z"));

        slaService.handleResolvedTicketReopenedByInternalUser(ticket, actor, reopenedAt);

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.BREACHED);
        assertThat(ticket.getResolutionSlaPausedAt()).isNull();

        verify(eventService).recordResolutionSlaBreachedEvent(ticket, actor);
    }

    @Test
    void shouldKeepResolutionSlaBreachedWhenResolvedTicketIsReopenedAndSlaWasAlreadyBreached() {
        UserEntity actor = mock(UserEntity.class);

        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.BREACHED, RESOLUTION_DEADLINE);
        ticket.setResolvedAt(Instant.parse("2026-05-12T09:00:00Z"));
        ticket.setResolutionSlaPausedAt(Instant.parse("2026-05-12T09:00:00Z"));

        slaService.handleResolvedTicketReopenedByInternalUser(ticket, actor, RESOLUTION_DEADLINE.minus(1, ChronoUnit.HOURS));

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.BREACHED);
        assertThat(ticket.getResolutionSlaPausedAt()).isNull();

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldThrowExceptionWhenResolvedTicketIsReopenedButResolvedAtIsNotSet() {
        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);

        assertThatThrownBy(() -> slaService.handleResolvedTicketReopenedByInternalUser(
                ticket,
                mock(UserEntity.class),
                RESOLUTION_DEADLINE.minus(1, ChronoUnit.HOURS)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Resolution timestamp is not set");
    }

    @Test
    void shouldThrowExceptionWhenResolvedTicketReopenTimeIsNull() {
        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);
        ticket.setResolvedAt(Instant.parse("2026-05-12T09:00:00Z"));

        assertThatThrownBy(() -> slaService.handleResolvedTicketReopenedByInternalUser(ticket, mock(UserEntity.class), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reopened time cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenResolvedTicketIsReopenedButResolutionDeadlineIsNotSet() {
        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, null);
        ticket.setResolvedAt(Instant.parse("2026-05-12T09:00:00Z"));

        assertThatThrownBy(() -> slaService.handleResolvedTicketReopenedByInternalUser(
                ticket,
                mock(UserEntity.class),
                RESOLUTION_DEADLINE.minus(1, ChronoUnit.HOURS)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Resolution SLA deadline is not set");
    }

    @Test
    void shouldFinalizePausedResolutionSlaAsMetOnClose() {
        TicketEntity ticket = ticketWithResolutionSla(SlaStatus.PAUSED, RESOLUTION_DEADLINE);
        ticket.setResolutionSlaPausedAt(Instant.parse("2026-05-12T09:00:00Z"));

        slaService.finalizeResolutionSlaOnClose(ticket);

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(SlaStatus.MET);
        assertThat(ticket.getResolutionSlaPausedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = SlaStatus.class, names = {"ON_TRACK", "MET", "BREACHED"})
    void shouldDoNothingWhenFinalizingResolutionSlaThatIsNotPaused(SlaStatus currentStatus) {
        TicketEntity ticket = ticketWithResolutionSla(currentStatus, RESOLUTION_DEADLINE);

        slaService.finalizeResolutionSlaOnClose(ticket);

        assertThat(ticket.getResolutionSlaStatus()).isSameAs(currentStatus);
    }

    @Test
    void shouldMarkOverdueFirstResponseSlasAsBreached() {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");

        TicketEntity ticket1 = TicketEntity.builder()
                .id(1L)
                .responseSlaStatus(SlaStatus.ON_TRACK)
                .build();
        TicketEntity ticket2 = TicketEntity.builder()
                .id(2L)
                .responseSlaStatus(SlaStatus.ON_TRACK)
                .build();

        doReturn(List.of(ticket1, ticket2))
                .when(ticketRepository).findTicketsWithOverdueFirstResponseSla(
                        eq(SlaStatus.ON_TRACK),
                        eq(now),
                        any()
                );

        int actualResult = slaService.markOverdueFirstResponseSlasAsBreached(now);

        assertThat(actualResult).isEqualTo(2);
        assertThat(ticket1.getResponseSlaStatus()).isEqualTo(SlaStatus.BREACHED);
        assertThat(ticket2.getResponseSlaStatus()).isEqualTo(SlaStatus.BREACHED);

        verify(eventService).recordFirstResponseSlaBreachedEvent(ticket1);
        verify(eventService).recordFirstResponseSlaBreachedEvent(ticket2);
        verifyNoMoreInteractions(eventService);
    }

    @Test
    void shouldReturnZeroWhenNoOverdueFirstResponseSlasFound() {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");

        doReturn(Collections.emptyList())
                .when(ticketRepository).findTicketsWithOverdueFirstResponseSla(
                        eq(SlaStatus.ON_TRACK),
                        eq(now),
                        any()
                );

        int actualResult = slaService.markOverdueFirstResponseSlasAsBreached(now);

        assertThat(actualResult).isZero();

        verify(eventService, never()).recordFirstResponseSlaBreachedEvent(any(TicketEntity.class));
    }

    @Test
    void shouldMarkOverdueResolutionSlasAsBreached() {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");

        TicketEntity ticket1 = TicketEntity.builder()
                .id(1L)
                .resolutionSlaStatus(SlaStatus.ON_TRACK)
                .build();
        TicketEntity ticket2 = TicketEntity.builder()
                .id(2L)
                .resolutionSlaStatus(SlaStatus.ON_TRACK)
                .build();

        doReturn(List.of(ticket1, ticket2))
                .when(ticketRepository).findTicketsWithOverdueResolutionSla(
                        eq(SlaStatus.ON_TRACK),
                        eq(now),
                        any()
                );

        int actualResult = slaService.markOverdueResolutionSlasAsBreached(now);

        assertThat(actualResult).isEqualTo(2);
        assertThat(ticket1.getResolutionSlaStatus()).isEqualTo(SlaStatus.BREACHED);
        assertThat(ticket2.getResolutionSlaStatus()).isEqualTo(SlaStatus.BREACHED);

        verify(eventService).recordResolutionSlaBreachedEvent(ticket1);
        verify(eventService).recordResolutionSlaBreachedEvent(ticket2);
        verifyNoMoreInteractions(eventService);
    }

    @Test
    void shouldReturnZeroWhenNoOverdueResolutionSlasFound() {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");

        doReturn(Collections.emptyList())
                .when(ticketRepository).findTicketsWithOverdueResolutionSla(
                        eq(SlaStatus.ON_TRACK),
                        eq(now),
                        any()
                );

        int actualResult = slaService.markOverdueResolutionSlasAsBreached(now);

        assertThat(actualResult).isZero();

        verify(eventService, never()).recordResolutionSlaBreachedEvent(any(TicketEntity.class));
    }

    private TicketEntity ticketWithResponseSla(SlaStatus status, Instant deadline, Instant firstRespondedAt) {
        TicketEntity ticket = TicketEntity.builder()
                .responseSlaStatus(status)
                .build();

        ticket.setFirstResponseDeadline(deadline);
        ticket.setFirstRespondedAt(firstRespondedAt);

        return ticket;
    }

    private TicketEntity ticketWithResolutionSla(SlaStatus status, Instant deadline) {
        TicketEntity ticket = TicketEntity.builder()
                .resolutionSlaStatus(status)
                .build();

        ticket.setResolutionDeadline(deadline);

        return ticket;
    }

    private TicketEntity ticketForPriorityChange(
            TicketPriority priority,
            SlaStatus responseSlaStatus,
            SlaStatus resolutionSlaStatus
    ) {
        TicketEntity ticket = TicketEntity.builder()
                .priority(priority)
                .responseSlaStatus(responseSlaStatus)
                .resolutionSlaStatus(resolutionSlaStatus)
                .build();

        ticket.setCreatedAt(CREATED_AT);
        ticket.setFirstResponseDeadline(RESPONSE_DEADLINE);
        ticket.setResolutionDeadline(RESOLUTION_DEADLINE);

        return ticket;
    }

    private SlaPolicy getSlaPolicy(TicketPriority priority) {
        return switch (priority) {
            case LOW -> new SlaPolicy(TicketPriority.LOW, 2880, 10080);
            case MEDIUM -> new SlaPolicy(TicketPriority.MEDIUM, 1440, 4320);
            case HIGH -> new SlaPolicy(TicketPriority.HIGH, 480, 1440);
            case CRITICAL -> new SlaPolicy(TicketPriority.CRITICAL, 120, 480);
        };
    }
}
