package com.rolliedev.ticketflow.repository;

import com.rolliedev.ticketflow.entity.TicketEventEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketEventRepository extends JpaRepository<TicketEventEntity, Long> {

    List<TicketEventEntity> findAllByTicketId(Long ticketId, Sort sort);
}
