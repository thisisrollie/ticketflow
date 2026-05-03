package com.rolliedev.ticketflow.querydsl;

import com.querydsl.core.types.Predicate;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static com.rolliedev.ticketflow.entity.QTicketEntity.ticketEntity;

@Component
public class TicketPredicateBuilder {

    public Predicate buildPredicate(TicketSearchFilter filter, TicketFlowUserDetails actor) {
        QPredicates builder = QPredicates.builder()
                .add(filter.status(), ticketEntity.status::eq)
                .add(filter.priority(), ticketEntity.priority::eq)
                .add(filter.assigneeId(), ticketEntity.assignedTo.id::eq)
                .add(startOfNextDay(filter.createdBefore()), ticketEntity.createdAt::before)
                .add(startOfDay(filter.createdAfter()), ticketEntity.createdAt::after);

        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            builder.add(filter.keyword(), this::keywordPredicate);
        }
        if (actor.hasAuthority(Role.CUSTOMER)) {
            builder.add(actor.getId(), ticketEntity.createdBy.id::eq);
        } else {
            builder.add(filter.creatorId(), ticketEntity.createdBy.id::eq);
        }

        return builder.build();
    }

    private Predicate keywordPredicate(String keyword) {
        return QPredicates.builder()
                .add(keyword, ticketEntity.title::containsIgnoreCase)
                .add(keyword, ticketEntity.description::containsIgnoreCase)
                .buildOr();
    }

    private Instant startOfNextDay(LocalDate date) {
        return startOfDay(date != null ? date.plusDays(1L) : null);
    }

    private Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
