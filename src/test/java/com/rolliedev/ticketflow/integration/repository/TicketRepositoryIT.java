package com.rolliedev.ticketflow.integration.repository;

import com.querydsl.core.types.Predicate;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.enums.SlaStatus;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.querydsl.TicketPredicateBuilder;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.testsupport.base.AbstractJpaIT;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TicketPredicateBuilder.class)
class TicketRepositoryIT extends AbstractJpaIT {

    @Autowired
    private TicketPredicateBuilder ticketPredicateBuilder;

    @Test
    void shouldReturnMatchingTicketsWhenFilterIsProvided() {
        Predicate predicate = ticketPredicateBuilder.buildPredicate(
                TicketSearchFilter.builder()
                        .status(TicketStatus.NEW)
                        .creatorId(customer.getId())
                        .build(),
                new TicketFlowUserDetails(agent)
        );

        Page<TicketEntity> actualResult = ticketRepository.findAll(predicate, PageRequest.of(0, 10));

        assertThat(actualResult.getContent()).hasSize(2);
        assertThat(actualResult.getContent()).allSatisfy(ticket -> {
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
            assertThat(ticket.getCreatedBy().getId()).isEqualTo(customer.getId());
        });
        assertThat(actualResult.getContent())
                .extracting(TicketEntity::getId)
                .contains(ticket2.getId(), ticket3.getId());
    }

    @Test
    void shouldReturnMatchingTicketsWhenFilterByKeyword() {
        TicketEntity ticket4 = DataUtils.getTransientTicket(
                "Can't log in (YouTube)", "Getting error when logging in with Google", TicketStatus.CLOSED, TicketPriority.MEDIUM, customer, null
        );
        TicketEntity ticket5 = DataUtils.getTransientTicket(
                "Billing discrepancy", "YouTube charged me twice for last month", TicketStatus.IN_PROGRESS, TicketPriority.CRITICAL, customer, null
        );
        ticketRepository.saveAll(List.of(ticket4, ticket5));
        flushAndClear();

        Predicate predicate = ticketPredicateBuilder.buildPredicate(
                TicketSearchFilter.builder()
                        .keyword("youtube")
                        .build(),
                new TicketFlowUserDetails(agent)
        );

        Page<TicketEntity> actualResult = ticketRepository.findAll(predicate, PageRequest.of(0, 10));

        assertThat(actualResult.getContent()).hasSize(2);
        assertThat(actualResult.getContent())
                .extracting(TicketEntity::getId)
                .containsExactlyInAnyOrder(ticket4.getId(), ticket5.getId());
    }

    @Test
    void shouldReturnOnlyResolvedTicketsAtOrBeforeThreshold() {
        Instant threshold = Instant.parse("2026-05-15T10:00:00Z");

        TicketEntity oldResolved = saveTicket(
                "Old resolved",
                TicketStatus.RESOLVED,
                threshold.minus(1, ChronoUnit.HOURS)
        );

        TicketEntity exactlyAtThreshold = saveTicket(
                "Exactly threshold",
                TicketStatus.RESOLVED,
                threshold
        );

        saveTicket(
                "Recently resolved",
                TicketStatus.RESOLVED,
                threshold.plus(1, ChronoUnit.SECONDS)
        );

        saveTicket(
                "Resolved without timestamp",
                TicketStatus.RESOLVED,
                null
        );

        saveTicket(
                "In progress with old resolvedAt",
                TicketStatus.IN_PROGRESS,
                threshold.minus(1, ChronoUnit.DAYS)
        );

        saveTicket(
                "Closed with old resolvedAt",
                TicketStatus.CLOSED,
                threshold.minus(1, ChronoUnit.DAYS)
        );

        flushAndClear();

        List<TicketEntity> actualResult = ticketRepository.findResolvedTicketsOlderThan(threshold);

        assertThat(actualResult)
                .extracting(TicketEntity::getId)
                .containsExactlyInAnyOrder(
                        oldResolved.getId(),
                        exactlyAtThreshold.getId()
                );
    }

    private TicketEntity saveTicket(String title, TicketStatus status, Instant resolvedAt) {
        TicketEntity ticket = TicketEntity.builder()
                .title(title)
                .description("Test ticket description")
                .status(status)
                .priority(TicketPriority.MEDIUM)
                .createdBy(customer)
                .resolvedAt(resolvedAt)
                .firstResponseDeadline(Instant.parse("2026-05-16T10:00:00Z"))
                .resolutionDeadline(Instant.parse("2026-05-17T10:00:00Z"))
                .responseSlaStatus(SlaStatus.ON_TRACK)
                .resolutionSlaStatus(status == TicketStatus.RESOLVED ? SlaStatus.PAUSED : SlaStatus.ON_TRACK)
                .build();

        return ticketRepository.save(ticket);
    }
}