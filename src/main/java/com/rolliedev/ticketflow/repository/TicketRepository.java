package com.rolliedev.ticketflow.repository;

import com.querydsl.core.types.Predicate;
import com.rolliedev.ticketflow.entity.TicketEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

public interface TicketRepository extends
        JpaRepository<TicketEntity, Long>,
        QuerydslPredicateExecutor<TicketEntity> {

    @EntityGraph(attributePaths = {"createdBy", "assignedTo"})
    Page<TicketEntity> findAll(Predicate predicate, Pageable pageable);
}
