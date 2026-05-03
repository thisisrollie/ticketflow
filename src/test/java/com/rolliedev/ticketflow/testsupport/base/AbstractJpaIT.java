package com.rolliedev.ticketflow.testsupport.base;

import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.testsupport.annotation.JpaIT;
import com.rolliedev.ticketflow.testsupport.container.AbstractPostgresContainerTest;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@JpaIT
public abstract class AbstractJpaIT extends AbstractPostgresContainerTest {

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TicketRepository ticketRepository;
    @Autowired
    protected EntityManager entityManager;

    protected UserEntity agent, customer;
    protected TicketEntity ticket1, ticket2, ticket3;

    @BeforeEach
    void setUp() {
        agent = DataUtils.getTransientUser("Bruce", "Wayne", Role.AGENT);
        customer = DataUtils.getTransientUser("Clark", "Kent", Role.CUSTOMER);
        userRepository.saveAllAndFlush(List.of(customer, agent));

        ticket1 = DataUtils.getTransientTicket("Can't log in", "Getting error when logging in with Google", TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM, customer, null);
        ticket2 = DataUtils.getTransientTicket("Billing discrepancy", "Charged twice for last month", TicketStatus.NEW, TicketPriority.HIGH, customer, null);
        ticket3 = DataUtils.getTransientTicket("Cannot upgrade my subscription", "I want to upgrade my subscription to premium", TicketStatus.NEW, TicketPriority.MEDIUM, customer, null);
        ticketRepository.saveAllAndFlush(List.of(ticket1, ticket2, ticket3));
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
