package com.rolliedev.ticketflow.integration.repository;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.integration.annotation.JpaIT;
import com.rolliedev.ticketflow.repository.TicketCommentRepository;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.util.DataUtils;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JpaIT
@RequiredArgsConstructor
class TicketCommentRepositoryIT {

    private final UserRepository userRepo;
    private final TicketRepository ticketRepo;
    private final TicketCommentRepository commentRepo;

    @Test
    void shouldReturnAllCommentsByTicketIdOrderedByCreatedAtAsc() {
        UserEntity customer = DataUtils.getTransientUser("Clark", "Kent", Role.CUSTOMER);
        userRepo.save(customer);

        TicketEntity ticket = DataUtils.getTransientTicket("Can't log in", "Getting error when logging in with Google", customer);
        TicketCommentEntity comment1 = DataUtils.getTransientTicketComment(ticket, customer, "Yesterday it was working, but today it's not");
        TicketCommentEntity comment2 = DataUtils.getTransientTicketComment(ticket, customer, "I've tried to reset my password multiple times, but it's not working");
        ticket.addComments(comment1, comment2);
        ticketRepo.save(ticket);

        List<TicketCommentEntity> actualResult = commentRepo.findAllByTicketIdOrderByCreatedAtAsc(ticket.getId());

        assertThat(actualResult).hasSize(2);
        List<Long> commentIds = actualResult.stream()
                .map(TicketCommentEntity::getId)
                .toList();
        assertThat(commentIds).containsExactly(comment1.getId(), comment2.getId());
    }
}