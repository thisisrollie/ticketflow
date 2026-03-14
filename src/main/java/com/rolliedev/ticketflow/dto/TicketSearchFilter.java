package com.rolliedev.ticketflow.dto;

import com.querydsl.core.types.Predicate;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.querydsl.QPredicates;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static com.rolliedev.ticketflow.entity.QTicketEntity.ticketEntity;

@Builder
public record TicketSearchFilter(String keyword,
                                 TicketStatus status,
                                 TicketPriority priority,
                                 Integer creatorId,
                                 Integer assigneeId,
                                 LocalDate createdBefore,
                                 LocalDate createdAfter) {

    public static Predicate buildPredicate(TicketSearchFilter filter) {
        return QPredicates.builder()
                .add(filter.keyword, TicketSearchFilter::keywordPredicate)
                .add(filter.status, ticketEntity.status::eq)
                .add(filter.priority, ticketEntity.priority::eq)
                .add(filter.creatorId, ticketEntity.createdBy.id::eq)
                .add(filter.assigneeId, ticketEntity.assignedTo.id::eq)
                .add(startOfNextDay(filter.createdBefore), ticketEntity.createdAt::before)
                .add(startOfDay(filter.createdAfter), ticketEntity.createdAt::after)
                .build();
    }

    private static Predicate keywordPredicate(String keyword) {
        return QPredicates.builder()
                .add(keyword, ticketEntity.title::containsIgnoreCase)
                .add(keyword, ticketEntity.description::containsIgnoreCase)
                .buildOr();
    }

    private static Instant startOfNextDay(LocalDate date) {
        return startOfDay(date != null ? date.plusDays(1L) : null);
    }

    private static Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
