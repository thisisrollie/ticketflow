package com.rolliedev.ticketflow.repository;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketCommentRepository extends JpaRepository<TicketCommentEntity, Long> {

    @EntityGraph(attributePaths = {"author"})
    Page<TicketCommentEntity> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId, Pageable pageable);

    @EntityGraph(attributePaths = {"ticket"})
    Optional<TicketCommentEntity> findWithTicketById(Long id);
}
