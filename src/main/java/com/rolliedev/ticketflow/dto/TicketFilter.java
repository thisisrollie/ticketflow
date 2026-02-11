package com.rolliedev.ticketflow.dto;

import com.querydsl.core.types.Predicate;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.querydsl.QPredicates;
import lombok.Builder;

import java.time.Instant;

import static com.rolliedev.ticketflow.entity.QTicketEntity.ticketEntity;

@Builder
public record TicketFilter(String keyword,
                           TicketStatus status,
                           TicketPriority priority,
                           Integer creatorId,
                           Integer assigneeId,
                           Instant createdBefore,
                           Instant createdAfter) {

    public static Predicate buildPredicate(TicketFilter filter) {
        return QPredicates.builder()
                .add(filter.keyword, TicketFilter::keywordPredicate)
                .add(filter.status, ticketEntity.status::eq)
                .add(filter.priority, ticketEntity.priority::eq)
                .add(filter.creatorId, ticketEntity.createdBy.id::eq)
                .add(filter.assigneeId, ticketEntity.assignedTo.id::eq)
                .add(filter.createdBefore, ticketEntity.createdAt::before)
                .add(filter.createdAfter, ticketEntity.createdAt::after)
                .build();
    }

    private static Predicate keywordPredicate(String keyword) {
        return QPredicates.builder()
                .add(keyword, ticketEntity.title::containsIgnoreCase)
                .add(keyword, ticketEntity.description::containsIgnoreCase)
                .buildOr();
    }
}
