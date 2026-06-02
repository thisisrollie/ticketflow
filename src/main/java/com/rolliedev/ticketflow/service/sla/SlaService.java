package com.rolliedev.ticketflow.service.sla;

import com.rolliedev.ticketflow.dto.SlaPolicy;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.SlaStatus;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.service.TicketEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SlaService {

    private static final Duration TRIAGE_WINDOW = Duration.ofHours(4);
    private static final Set<TicketStatus> SLA_CHECK_EXCLUDED_STATUSES = Set.of(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final SlaPolicyProvider policyProvider;
    private final TicketRepository ticketRepository;
    private final TicketEventService eventService;

    public void initializeSlaForNewTicket(TicketEntity ticket) {
        if (ticket.getCreatedAt() == null) {
            throw new IllegalStateException("Ticket creation time is not set");
        }

        SlaPolicy slaPolicy = policyProvider.getSlaPolicy(ticket.getPriority());

        ticket.setFirstResponseDeadline(
                ticket.getCreatedAt().plus(slaPolicy.firstResponseDueMinutes(), ChronoUnit.MINUTES)
        );
        ticket.setResolutionDeadline(
                ticket.getCreatedAt().plus(slaPolicy.resolutionDueMinutes(), ChronoUnit.MINUTES)
        );

        ticket.setResponseSlaStatus(SlaStatus.ON_TRACK);
        ticket.setResolutionSlaStatus(SlaStatus.ON_TRACK);
    }

    public void evaluateFirstResponse(TicketEntity ticket, UserEntity actor) {
        if (ticket.getFirstRespondedAt() == null) {
            throw new IllegalStateException("First response timestamp is not set");
        }

        if (ticket.getResponseSlaStatus() != SlaStatus.ON_TRACK) {
            return;
        }

        SlaStatus slaStatus = evaluateSlaStatus(ticket.getFirstRespondedAt(), ticket.getFirstResponseDeadline());
        ticket.setResponseSlaStatus(slaStatus);

        if (slaStatus == SlaStatus.BREACHED) {
            eventService.recordFirstResponseSlaBreachedEvent(ticket, actor);
        }
    }

    public void pauseResolutionSlaClock(TicketEntity ticket, UserEntity actor, Instant pausedAt) {
        if (ticket.getResolutionSlaStatus() != SlaStatus.ON_TRACK) {
            return;
        }

        if (pausedAt == null) {
            throw new IllegalArgumentException("Pause time cannot be null");
        }

        if (ticket.getResolutionDeadline() == null) {
            throw new IllegalStateException("Resolution SLA deadline is not set");
        }

        if (pausedAt.isAfter(ticket.getResolutionDeadline())) {
            ticket.setResolutionSlaStatus(SlaStatus.BREACHED);
            eventService.recordResolutionSlaBreachedEvent(ticket, actor);
        } else {
            ticket.setResolutionSlaStatus(SlaStatus.PAUSED);
            ticket.setResolutionSlaPausedAt(pausedAt);
        }
    }

    public void resumeResolutionSlaClock(TicketEntity ticket, Instant resumedAt) {
        if (ticket.getResolutionSlaStatus() != SlaStatus.PAUSED) {
            return;
        }

        if (resumedAt == null) {
            throw new IllegalArgumentException("Resume time cannot be null");
        }

        if (ticket.getResolutionSlaPausedAt() == null) {
            throw new IllegalStateException("Pause time is not set");
        }

        if (ticket.getResolutionDeadline() == null) {
            throw new IllegalStateException("Resolution SLA deadline is not set");
        }

        Duration pausedDuration = Duration.between(ticket.getResolutionSlaPausedAt(), resumedAt);

        if (pausedDuration.isNegative()) {
            throw new IllegalStateException("Resume time cannot be before the pause time");
        }

        ticket.setResolutionDeadline(ticket.getResolutionDeadline().plus(pausedDuration));
        ticket.setResolutionSlaPausedAt(null);
        ticket.setResolutionSlaStatus(SlaStatus.ON_TRACK);
    }

    public void updateDeadlinesAfterPriorityChange(TicketEntity ticket, Instant changedAt) {
        if (changedAt == null) {
            throw new IllegalArgumentException("Priority change time cannot be null");
        }

        SlaPolicy slaPolicy = policyProvider.getSlaPolicy(ticket.getPriority());

        if (isWithinTriageWindow(ticket, changedAt)) {
            recalculateActiveDeadlines(ticket, changedAt, slaPolicy);
        } else {
            tightenActiveDeadlines(ticket, changedAt, slaPolicy);
        }
    }

    public void handleResolvedTicketReopenedByInternalUser(TicketEntity ticket, UserEntity actor, Instant reopenedAt) {
        if (ticket.getResolvedAt() == null) {
            throw new IllegalStateException("Resolution timestamp is not set");
        }

        if (reopenedAt == null) {
            throw new IllegalArgumentException("Reopened time cannot be null");
        }

        if (ticket.getResolutionDeadline() == null) {
            throw new IllegalStateException("Resolution SLA deadline is not set");
        }

        ticket.setResolutionSlaPausedAt(null);

        if (ticket.getResolutionSlaStatus() == SlaStatus.BREACHED) {
            return;
        }

        if (reopenedAt.isAfter(ticket.getResolutionDeadline())) {
            ticket.setResolutionSlaStatus(SlaStatus.BREACHED);
            eventService.recordResolutionSlaBreachedEvent(ticket, actor);
        } else {
            ticket.setResolutionSlaStatus(SlaStatus.ON_TRACK);
        }
    }

    public void finalizeResolutionSlaOnClose(TicketEntity ticket) {
        if (ticket.getResolutionSlaStatus() == SlaStatus.PAUSED) {
            ticket.setResolutionSlaStatus(SlaStatus.MET);
            ticket.setResolutionSlaPausedAt(null);
        }
    }

    @Transactional
    public int markOverdueFirstResponseSlasAsBreached(Instant now) {
        List<TicketEntity> overdueTickets = ticketRepository.findTicketsWithOverdueFirstResponseSla(
                SlaStatus.ON_TRACK,
                now,
                SLA_CHECK_EXCLUDED_STATUSES
        );

        overdueTickets.forEach(ticket -> {
            ticket.setResponseSlaStatus(SlaStatus.BREACHED);
            eventService.recordFirstResponseSlaBreachedEvent(ticket);
        });

        return overdueTickets.size();
    }

    @Transactional
    public int markOverdueResolutionSlasAsBreached(Instant now) {
        List<TicketEntity> overdueTickets = ticketRepository.findTicketsWithOverdueResolutionSla(
                SlaStatus.ON_TRACK,
                now,
                SLA_CHECK_EXCLUDED_STATUSES
        );

        overdueTickets.forEach(ticket -> {
            ticket.setResolutionSlaStatus(SlaStatus.BREACHED);
            eventService.recordResolutionSlaBreachedEvent(ticket);
        });

        return overdueTickets.size();
    }

    private SlaStatus evaluateSlaStatus(Instant completedAt, Instant deadline) {
        if (deadline == null) {
            throw new IllegalStateException("SLA deadline is not set");
        }

        return completedAt.isAfter(deadline)
                ? SlaStatus.BREACHED
                : SlaStatus.MET;
    }

    private boolean isWithinTriageWindow(TicketEntity ticket, Instant changedAt) {
        Instant triageWindowDeadline = ticket.getCreatedAt().plus(TRIAGE_WINDOW);
        return !changedAt.isAfter(triageWindowDeadline);
    }

    private void recalculateActiveDeadlines(TicketEntity ticket, Instant changedAt, SlaPolicy slaPolicy) {
        if (ticket.getResponseSlaStatus() == SlaStatus.ON_TRACK) {
            ticket.setFirstResponseDeadline(changedAt.plus(slaPolicy.firstResponseDueMinutes(), ChronoUnit.MINUTES));
        }

        if (ticket.getResolutionSlaStatus() == SlaStatus.ON_TRACK) {
            ticket.setResolutionDeadline(changedAt.plus(slaPolicy.resolutionDueMinutes(), ChronoUnit.MINUTES));
        }
    }

    private void tightenActiveDeadlines(TicketEntity ticket, Instant changedAt, SlaPolicy slaPolicy) {
        if (ticket.getResponseSlaStatus() == SlaStatus.ON_TRACK) {
            Instant candidateDeadline = changedAt.plus(slaPolicy.firstResponseDueMinutes(), ChronoUnit.MINUTES);
            ticket.setFirstResponseDeadline(getEarlierInstant(ticket.getFirstResponseDeadline(), candidateDeadline));
        }

        if (ticket.getResolutionSlaStatus() == SlaStatus.ON_TRACK) {
            Instant candidateDeadline = changedAt.plus(slaPolicy.resolutionDueMinutes(), ChronoUnit.MINUTES);
            ticket.setResolutionDeadline(getEarlierInstant(ticket.getResolutionDeadline(), candidateDeadline));
        }
    }

    private Instant getEarlierInstant(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
}
