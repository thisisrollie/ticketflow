package com.rolliedev.ticketflow.repository;

import com.rolliedev.ticketflow.entity.TicketEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketEventRepository extends JpaRepository<TicketEventEntity, Long> {

    @EntityGraph(attributePaths = {"actor"})
    Page<TicketEventEntity> findAllByTicketId(Long ticketId, Pageable pageable);
}
