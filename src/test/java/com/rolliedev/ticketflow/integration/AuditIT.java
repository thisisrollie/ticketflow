package com.rolliedev.ticketflow.integration;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.testsupport.base.AbstractJpaIT;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class AuditIT extends AbstractJpaIT {

    @Autowired
    private EntityManager entityManager;

    @Test
    void checkUserAudit() {
        UserEntity user = DataUtils.getTransientUser("Pete", "Ross", Role.CUSTOMER);

        entityManager.persist(user);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void checkTicketAudit() {
        TicketEntity ticket = DataUtils.getTransientTicket("Test ticket", "Test ticket description", customer);
        entityManager.persist(ticket);

        assertThat(ticket.getId()).isNotNull();
        assertThat(ticket.getCreatedAt()).isNotNull();
        assertThat(ticket.getModifiedAt()).isNotNull();
    }

    @Test
    void checkTicketCommentAudit() {
        TicketCommentEntity comment = DataUtils.getTransientTicketComment(ticket1, customer, "Test comment");

        entityManager.persist(comment);

        assertThat(comment.getId()).isNotNull();
        assertThat(comment.getCreatedAt()).isNotNull();
    }

    @Test
    void checkTicketEventAudit() {
        TicketEventEntity event = DataUtils.getTransientTicketStatusChangedEvent(ticket2, agent, TicketStatus.NEW, TicketStatus.IN_PROGRESS);

        entityManager.persist(event);

        assertThat(event.getId()).isNotNull();
        assertThat(event.getCreatedAt()).isNotNull();
    }
}
