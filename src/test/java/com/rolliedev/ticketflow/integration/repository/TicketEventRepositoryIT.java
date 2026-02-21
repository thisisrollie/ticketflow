package com.rolliedev.ticketflow.integration.repository;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.testsupport.base.AbstractJpaIT;
import com.rolliedev.ticketflow.repository.TicketEventRepository;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketEventRepositoryIT extends AbstractJpaIT {

    @Autowired
    private TicketEventRepository ticketEventRepo;
    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldReturnAllEventsByTicketIdOrderedByCreatedAtDesc() {
        // given
        TicketEventEntity event1 = DataUtils.getTransientTicketCreatedEvent(ticket2, customer);
        TicketEventEntity event2 = DataUtils.getTransientTicketCommentedEvent(ticket2, customer, TicketCommentEntity.builder().id(4L).build());
        TicketEventEntity event3 = DataUtils.getTransientTicketStatusChangedEvent(ticket2, customer, ticket2.getStatus(), TicketStatus.CLOSED);
        ticketEventRepo.saveAllAndFlush(List.of(event1, event2, event3));
        entityManager.clear();

        // when
        List<TicketEventEntity> actualResult = ticketEventRepo.findAllByTicketId(ticket2.getId(), Sort.by("createdAt", "id").descending());

        // then
        assertThat(actualResult).hasSize(3);
        List<Long> ticketEventIds = actualResult.stream()
                .map(TicketEventEntity::getId)
                .toList();
        assertThat(ticketEventIds).containsExactly(event3.getId(), event2.getId(), event1.getId());
        assertThat(actualResult.getFirst().getPayload()).containsEntry("newStatus", TicketStatus.CLOSED.name());
    }
}