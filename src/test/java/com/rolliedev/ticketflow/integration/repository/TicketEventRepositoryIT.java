package com.rolliedev.ticketflow.integration.repository;

import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketEventType;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.integration.annotation.JpaIT;
import com.rolliedev.ticketflow.repository.TicketEventRepository;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.util.DataUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JpaIT
@RequiredArgsConstructor
class TicketEventRepositoryIT {

    private final UserRepository userRepo;
    private final TicketRepository ticketRepo;
    private final TicketEventRepository ticketEventRepo;
    private final EntityManager entityManager;

    @Test
    void shouldReturnAllEventsByTicketIdOrderedByCreatedAtDesc() {
        // given
        UserEntity customer = DataUtils.getTransientUser("Clark", "Kent", Role.CUSTOMER);
        userRepo.save(customer);

        TicketEntity ticket = DataUtils.getTransientTicket("Can't log in", "Getting error when logging in with Google", customer);
        ticketRepo.save(ticket);

        TicketEventEntity event1 = DataUtils.getTransientTicketEvent(ticket, customer, TicketEventType.CREATED, Map.of("ticketId", ticket.getId().intValue()));
        TicketEventEntity event2 = DataUtils.getTransientTicketEvent(ticket, customer, TicketEventType.COMMENTED, Map.of("commentId", 4));
        TicketEventEntity event3 = DataUtils.getTransientTicketEvent(ticket, customer, TicketEventType.STATUS_CHANGED, Map.of("newStatus", TicketStatus.CLOSED.name()));
        ticketEventRepo.saveAllAndFlush(List.of(event1, event2, event3));
        entityManager.clear();

        // when
        List<TicketEventEntity> actualResult = ticketEventRepo.findAllByTicketId(ticket.getId(), Sort.by("createdAt", "id").descending());

        // then
        assertThat(actualResult).hasSize(3);
        List<Long> ticketEventIds = actualResult.stream()
                .map(TicketEventEntity::getId)
                .toList();
        assertThat(ticketEventIds).containsExactly(event3.getId(), event2.getId(), event1.getId());
        assertThat(actualResult.getFirst().getPayload()).containsEntry("newStatus", TicketStatus.CLOSED.name());
    }
}