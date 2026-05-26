package com.rolliedev.ticketflow.integration.repository;

import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.SlaStatus;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.testsupport.annotation.JpaIT;
import com.rolliedev.ticketflow.testsupport.container.AbstractPostgresContainerTest;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@JpaIT
@RequiredArgsConstructor
public class TicketRepositorySlaIT extends AbstractPostgresContainerTest {

    private static final Instant NOW = Instant.parse("2026-05-10T10:00:00Z");
    private static final Instant PAST_DEADLINE = Instant.parse("2026-05-10T09:00:00Z");
    private static final Instant FUTURE_DEADLINE = Instant.parse("2026-05-10T11:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-05-09T10:00:00Z");
    private static final Set<TicketStatus> SLA_CHECK_EXCLUDED_STATUSES = Set.of(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final EntityManager entityManager;

    private UserEntity customer;

    @BeforeEach
    void setUp() {
        customer = userRepository.save(DataUtils.getTransientUser("test", "test", Role.CUSTOMER));
    }

    @Test
    void shouldFindOnlyTicketsWithOverdueFirstResponseSla() {
        TicketEntity expectedTicket = saveTicket(
                "expected overdue first response",
                TicketStatus.NEW,
                SlaStatus.ON_TRACK,
                PAST_DEADLINE,
                null,
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because first response deadline is in the future",
                TicketStatus.NEW,
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                null,
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because first response deadline is exactly now",
                TicketStatus.NEW,
                SlaStatus.ON_TRACK,
                NOW,
                null,
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because first response already happened",
                TicketStatus.NEW,
                SlaStatus.ON_TRACK,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z"),
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because response SLA is already breached",
                TicketStatus.NEW,
                SlaStatus.BREACHED,
                PAST_DEADLINE,
                null,
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because response SLA is already met",
                TicketStatus.NEW,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:00:00Z"),
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because ticket waits for customer",
                TicketStatus.WAITING_CUSTOMER,
                SlaStatus.ON_TRACK,
                PAST_DEADLINE,
                null,
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because ticket is resolved",
                TicketStatus.RESOLVED,
                SlaStatus.ON_TRACK,
                PAST_DEADLINE,
                null,
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                Instant.parse("2026-05-10T10:00:00Z")
        );

        saveTicket(
                "not overdue because ticket is closed",
                TicketStatus.CLOSED,
                SlaStatus.ON_TRACK,
                PAST_DEADLINE,
                null,
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                null
        );

        flushAndClear();

        List<TicketEntity> actualResult = ticketRepository.findTicketsWithOverdueFirstResponseSla(
                SlaStatus.ON_TRACK,
                NOW,
                SLA_CHECK_EXCLUDED_STATUSES
        );

        assertThat(actualResult)
                .extracting(TicketEntity::getId)
                .containsExactly(expectedTicket.getId());
    }

    @Test
    void shouldFindOnlyTicketsWithOverdueResolutionSla() {
        TicketEntity expectedTicket = saveTicket(
                "expected overdue resolution",
                TicketStatus.IN_PROGRESS,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T07:30:00Z"),
                SlaStatus.ON_TRACK,
                PAST_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because resolution deadline is in future",
                TicketStatus.IN_PROGRESS,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z"),
                SlaStatus.ON_TRACK,
                FUTURE_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because resolution deadline is exactly now",
                TicketStatus.IN_PROGRESS,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z"),
                SlaStatus.ON_TRACK,
                NOW,
                null
        );

        saveTicket(
                "not overdue because ticket is already resolved",
                TicketStatus.RESOLVED,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z"),
                SlaStatus.ON_TRACK,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z")
        );

        saveTicket(
                "not overdue because resolvedAt is already set",
                TicketStatus.IN_PROGRESS,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z"),
                SlaStatus.ON_TRACK,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z")
        );

        saveTicket(
                "not overdue because resolution SLA is already breached",
                TicketStatus.IN_PROGRESS,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z"),
                SlaStatus.BREACHED,
                PAST_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because resolution SLA is already met",
                TicketStatus.IN_PROGRESS,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z"),
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z")
        );

        saveTicket(
                "not overdue because resolution SLA is paused",
                TicketStatus.IN_PROGRESS,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z"),
                SlaStatus.PAUSED,
                PAST_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because ticket waits for customer",
                TicketStatus.WAITING_CUSTOMER,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z"),
                SlaStatus.ON_TRACK,
                PAST_DEADLINE,
                null
        );

        saveTicket(
                "not overdue because ticket is closed",
                TicketStatus.CLOSED,
                SlaStatus.MET,
                PAST_DEADLINE,
                Instant.parse("2026-05-10T08:30:00Z"),
                SlaStatus.ON_TRACK,
                PAST_DEADLINE,
                null
        );

        flushAndClear();

        List<TicketEntity> actualResult = ticketRepository.findTicketsWithOverdueResolutionSla(
                SlaStatus.ON_TRACK,
                NOW,
                SLA_CHECK_EXCLUDED_STATUSES
        );

        assertThat(actualResult)
                .extracting(TicketEntity::getId)
                .containsExactly(expectedTicket.getId());
    }

    private TicketEntity saveTicket(String title,
                                    TicketStatus status,
                                    SlaStatus responseSlaStatus,
                                    Instant firstResponseDeadline,
                                    Instant firstRespondedAt,
                                    SlaStatus resolutionSlaStatus,
                                    Instant resolutionDeadline,
                                    Instant resolvedAt) {
        TicketEntity ticket = TicketEntity.builder()
                .title(title)
                .description("Test ticket description")
                .status(status)
                .priority(TicketPriority.MEDIUM)
                .createdBy(customer)
                .build();

        ticket.setCreatedAt(CREATED_AT);

        ticket.setResponseSlaStatus(responseSlaStatus);
        ticket.setFirstResponseDeadline(firstResponseDeadline);
        ticket.setFirstRespondedAt(firstRespondedAt);

        ticket.setResolutionSlaStatus(resolutionSlaStatus);
        ticket.setResolutionDeadline(resolutionDeadline);
        ticket.setResolvedAt(resolvedAt);

        return ticketRepository.save(ticket);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
