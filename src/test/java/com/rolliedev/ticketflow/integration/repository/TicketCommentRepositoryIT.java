package com.rolliedev.ticketflow.integration.repository;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.repository.TicketCommentRepository;
import com.rolliedev.ticketflow.testsupport.base.AbstractJpaIT;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCommentRepositoryIT extends AbstractJpaIT {

    @Autowired
    private TicketCommentRepository commentRepository;

    @Test
    void shouldReturnAllCommentsByTicketIdOrderedByCreatedAtAsc() {
        TicketCommentEntity comment1 = DataUtils.getTransientTicketComment(ticket1, customer, "first comment");
        TicketCommentEntity comment2 = DataUtils.getTransientTicketComment(ticket1, customer, "second comment");
        ticket1.addComments(comment1, comment2);
        ticketRepository.save(ticket1);
        flushAndClear();

        Page<TicketCommentEntity> actualResult = commentRepository.findAllByTicketIdOrderByCreatedAtAsc(ticket1.getId(), PageRequest.of(0, 10));

        assertThat(actualResult.getContent()).hasSize(2);

        assertThat(actualResult.getContent())
                .allSatisfy(comment -> assertThat(comment.getTicket().getId()).isEqualTo(ticket1.getId()));

        assertThat(actualResult.getContent())
                .extracting(TicketCommentEntity::getBody)
                .containsExactlyInAnyOrder("first comment", "second comment");
    }
}