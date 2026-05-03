package com.rolliedev.ticketflow.integration.repository;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.repository.TicketEventRepository;
import com.rolliedev.ticketflow.testsupport.base.AbstractJpaIT;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketEventRepositoryIT extends AbstractJpaIT {

    @Autowired
    private TicketEventRepository eventRepository;

    @Test
    void shouldReturnAllEventsByTicketIdOrderedByCreatedAtDesc() {
        // given
        TicketEventEntity event1 = DataUtils.getTransientTicketCreatedEvent(ticket2, customer);
        TicketEventEntity event2 = DataUtils.getTransientTicketCommentedEvent(ticket2, customer, TicketCommentEntity.builder().id(4L).build());
        TicketEventEntity event3 = DataUtils.getTransientTicketStatusChangedEvent(ticket2, customer, ticket2.getStatus(), TicketStatus.CLOSED);
        eventRepository.saveAll(List.of(event1, event2, event3));
        flushAndClear();

        // when
        Page<TicketEventEntity> actualResult = eventRepository.findAllByTicketId(ticket2.getId(),
                PageRequest.of(0, 10, Sort.Direction.DESC, "createdAt", "id"));

        // then
        assertThat(actualResult.getContent()).hasSize(3);
        List<Long> ticketEventIds = actualResult.getContent().stream()
                .map(TicketEventEntity::getId)
                .toList();
        assertThat(ticketEventIds).containsExactly(event3.getId(), event2.getId(), event1.getId());
        assertThat(actualResult.getContent().getFirst().getPayload()).containsEntry("newStatus", TicketStatus.CLOSED.name());
    }
}