package com.rolliedev.ticketflow.repository;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketCommentRepository extends JpaRepository<TicketCommentEntity, Long> {

    @EntityGraph(attributePaths = {"author"})
    List<TicketCommentEntity> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);

    List<TicketCommentEntity> findAllByTicketId(Long ticketId);

    @EntityGraph(attributePaths = {"ticket"})
    Optional<TicketCommentEntity> findWithTicketById(Long id);
}
