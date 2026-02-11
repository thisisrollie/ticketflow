package com.rolliedev.ticketflow.repository;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCommentRepository extends JpaRepository<TicketCommentEntity, Long> {

    List<TicketCommentEntity> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
