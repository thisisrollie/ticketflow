package com.rolliedev.ticketflow.integration;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.integration.annotation.IT;
import com.rolliedev.ticketflow.repository.TicketCommentRepository;
import com.rolliedev.ticketflow.repository.TicketEventRepository;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.util.DataUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@IT
public abstract class IntegrationTestBase {

    @Autowired
    protected UserRepository userRepo;
    @Autowired
    protected TicketRepository ticketRepo;
    @Autowired
    protected TicketEventRepository eventRepo;
    @Autowired
    protected TicketCommentRepository commentRepo;
    @Autowired
    protected EntityManager entityManager;

    protected UserEntity admin, agent, customer;
    protected TicketEntity ticket1, ticket2, ticket3;

    @BeforeEach
    void setUp() {
        admin = DataUtils.getTransientUser("Lex", "Luthor", Role.ADMIN);
        agent = DataUtils.getTransientUser("Bruce", "Wayne", Role.AGENT);
        customer = DataUtils.getTransientUser("Clark", "Kent", Role.CUSTOMER);
        userRepo.saveAllAndFlush(List.of(admin, agent, customer));

        ticket1 = DataUtils.getTransientTicket("Can't log in", "Getting error when logging in with Google", TicketStatus.IN_PROGRESS, TicketPriority.HIGH, customer, agent);
        ticket2 = DataUtils.getTransientTicket("Billing discrepancy", "Charged twice for last month", TicketStatus.NEW, TicketPriority.MEDIUM, customer, null);
        ticket3 = DataUtils.getTransientTicket("Cannot upgrade my subscription", "I want to upgrade my subscription to premium", TicketStatus.NEW, TicketPriority.MEDIUM, customer, null);
        ticketRepo.saveAllAndFlush(List.of(ticket1, ticket2, ticket3));

        TicketCommentEntity comment1 = DataUtils.getTransientTicketComment(ticket1, customer, "Yesterday it was working, but today it is not");
        TicketCommentEntity comment2 = DataUtils.getTransientTicketComment(ticket1, customer, "I have tried to reset my password multiple times, but it is not working");
        commentRepo.saveAllAndFlush(List.of(comment1, comment2));

        TicketEventEntity createdEventT1 = DataUtils.getTransientTicketCreatedEvent(ticket1, customer);
        TicketEventEntity assignedEventT1 = DataUtils.getTransientTicketAssignedEvent(ticket1, agent, agent, null);
        TicketEventEntity priorityChangedEventT1 = DataUtils.getTransientTicketPriorityChangedEvent(ticket1, agent, TicketPriority.MEDIUM, TicketPriority.HIGH);
        TicketEventEntity statusChangedEventT1 = DataUtils.getTransientTicketStatusChangedEvent(ticket1, agent, TicketStatus.NEW, TicketStatus.IN_PROGRESS);
        TicketEventEntity commentedEvent1T1 = DataUtils.getTransientTicketCommentedEvent(ticket1, customer, comment1);
        TicketEventEntity commentedEvent2T1 = DataUtils.getTransientTicketCommentedEvent(ticket1, customer, comment2);
        TicketEventEntity createdEventT2 = DataUtils.getTransientTicketCreatedEvent(ticket2, customer);
        TicketEventEntity createdEventT3 = DataUtils.getTransientTicketCreatedEvent(ticket3, customer);
        eventRepo.saveAllAndFlush(List.of(createdEventT1, assignedEventT1, priorityChangedEventT1, statusChangedEventT1, commentedEvent1T1, commentedEvent2T1, createdEventT2, createdEventT3));
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
