package com.rolliedev.ticketflow.repository;

import com.querydsl.core.types.Predicate;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.enums.SlaStatus;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TicketRepository extends
        JpaRepository<TicketEntity, Long>,
        QuerydslPredicateExecutor<TicketEntity> {

    @EntityGraph(attributePaths = {"createdBy", "assignedTo"})
    Page<TicketEntity> findAll(Predicate predicate, Pageable pageable);

    @Query("select t from TicketEntity t " +
           "where t.responseSlaStatus = :slaStatus " +
           "and t.firstResponseDeadline < :now " +
           "and t.firstRespondedAt is null " +
           "and t.status not in :excludedStatuses")
    List<TicketEntity> findTicketsWithOverdueFirstResponseSla(
            @Param("slaStatus") SlaStatus slaStatus,
            @Param("now") Instant now,
            @Param("excludedStatuses") Collection<TicketStatus> excludedStatuses
    );

    @Query("select t from TicketEntity t " +
           "where t.resolutionSlaStatus = :slaStatus " +
           "and t.resolutionDeadline < :now " +
           "and t.resolvedAt is null " +
           "and t.status not in :excludedStatuses")
    List<TicketEntity> findTicketsWithOverdueResolutionSla(
            @Param("slaStatus") SlaStatus slaStatus,
            @Param("now") Instant now,
            @Param("excludedStatuses") Collection<TicketStatus> excludedStatuses
    );

    @Query("select t from TicketEntity t " +
           "where t.status = com.rolliedev.ticketflow.entity.enums.TicketStatus.RESOLVED " +
           "and t.resolvedAt is not null " +
           "and t.resolvedAt <= :threshold")
    List<TicketEntity> findResolvedTicketsOlderThan(@Param("threshold") Instant threshold);
}
