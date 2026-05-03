package com.rolliedev.ticketflow.integration;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.testsupport.annotation.JpaIT;
import com.rolliedev.ticketflow.testsupport.container.AbstractPostgresContainerTest;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@JpaIT
public class AuditIT extends AbstractPostgresContainerTest {

    @Autowired
    private EntityManager entityManager;

    private UserEntity agent, customer;
    private TicketEntity ticket;

    @BeforeEach
    void setUp() {
        agent = DataUtils.getTransientUser("Bruce", "Wayne", Role.AGENT);
        customer = DataUtils.getTransientUser("Clark", "Kent", Role.CUSTOMER);
        entityManager.persist(agent);
        entityManager.persist(customer);

        ticket = DataUtils.getTransientTicket("Cannot log in", "Getting error when logging in with Google", customer);
        entityManager.persist(ticket);
    }

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
        TicketCommentEntity comment = DataUtils.getTransientTicketComment(ticket, customer, "Test comment");

        entityManager.persist(comment);

        assertThat(comment.getId()).isNotNull();
        assertThat(comment.getCreatedAt()).isNotNull();
    }

    @Test
    void checkTicketEventAudit() {
        TicketEventEntity event = DataUtils.getTransientTicketStatusChangedEvent(ticket, agent, TicketStatus.NEW, TicketStatus.IN_PROGRESS);

        entityManager.persist(event);

        assertThat(event.getId()).isNotNull();
        assertThat(event.getCreatedAt()).isNotNull();
    }
}
