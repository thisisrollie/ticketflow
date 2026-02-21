package com.rolliedev.ticketflow.integration.repository;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.testsupport.base.AbstractJpaIT;
import com.rolliedev.ticketflow.repository.TicketCommentRepository;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCommentRepositoryIT extends AbstractJpaIT {

    @Autowired
    private TicketCommentRepository commentRepo;

    @Test
    void shouldReturnAllCommentsByTicketIdOrderedByCreatedAtAsc() {
        TicketEntity newTicket = DataUtils.getTransientTicket("test ticket", "test description", customer);
        TicketCommentEntity comment1 = DataUtils.getTransientTicketComment(newTicket, customer, "Test comment 1");
        TicketCommentEntity comment2 = DataUtils.getTransientTicketComment(newTicket, customer, "Test comment 2");
        newTicket.addComments(comment1, comment2);
        ticketRepo.saveAndFlush(newTicket);

        List<TicketCommentEntity> actualResult = commentRepo.findAllByTicketIdOrderByCreatedAtAsc(newTicket.getId());

        assertThat(actualResult).hasSize(2);
        List<Long> commentIds = actualResult.stream()
                .map(TicketCommentEntity::getId)
                .toList();
        assertThat(commentIds).containsExactly(comment1.getId(), comment2.getId());
    }
}