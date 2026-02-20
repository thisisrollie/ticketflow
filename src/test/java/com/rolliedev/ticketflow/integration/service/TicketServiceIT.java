package com.rolliedev.ticketflow.integration.service;

import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketEventType;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.InvalidStatusTransitionException;
import com.rolliedev.ticketflow.service.TicketService;
import com.rolliedev.ticketflow.testsupport.base.AbstractSpringBootIT;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketServiceIT extends AbstractSpringBootIT {

    @Autowired
    private TicketService ticketService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldListTicketsMatchingFilterCriteria() {
        TicketSearchFilter searchFilter = TicketSearchFilter.builder()
                .priority(TicketPriority.HIGH)
                .creatorId(customer.getId())
                .build();
        PageRequest pageable = PageRequest.of(0, 10);

        Page<TicketResponse> actualPage = ticketService.listTickets(searchFilter, pageable);

        assertThat(actualPage.getContent()).hasSize(1);
        assertThat(actualPage.getContent()).allSatisfy(ticket -> {
            assertThat(ticket.priority()).isEqualTo(TicketPriority.HIGH);
            assertThat(ticket.createdBy().id()).isEqualTo(customer.getId());
        });
    }

    @Test
    void shouldReturnEmptyPageWhenNoTicketsMatchFilterCriteria() {
        TicketSearchFilter searchFilter = TicketSearchFilter.builder()
                .priority(TicketPriority.LOW)
                .creatorId(customer.getId())
                .build();
        PageRequest pageable = PageRequest.of(0, 10);

        Page<TicketResponse> actualPage = ticketService.listTickets(searchFilter, pageable);

        assertThat(actualPage.getContent()).isEmpty();
    }

    @Test
    void shouldReturnAllTicketsWhenSearchFilterIsEmpty() {
        TicketSearchFilter emptyFilter = TicketSearchFilter.builder().build();
        PageRequest pageable = PageRequest.of(0, 10);

        Page<TicketResponse> actualPage = ticketService.listTickets(emptyFilter, pageable);

        assertThat(actualPage.getContent()).hasSize(3);
    }

    @Test
    void shouldCreateTicketAndRecordTicketEventSuccessfully() {
        CreateTicketRequest createRequest = new CreateTicketRequest("dummy_title", "dummy_description", customer.getId());

        TicketResponse actualResult = ticketService.createTicket(createRequest);

        List<TicketEntity> allTickets = ticketRepo.findAll();
        assertThat(allTickets).hasSize(4);

        assertThat(actualResult.id()).isNotNull();
        assertThat(actualResult.title()).isEqualTo("dummy_title");
        assertThat(actualResult.description()).isEqualTo("dummy_description");
        assertThat(actualResult.status()).isEqualTo(TicketStatus.NEW);
        assertThat(actualResult.priority()).isEqualTo(TicketPriority.MEDIUM);

        List<TicketEventEntity> eventsForTicket = eventRepo.findAllByTicketId(actualResult.id(), Sort.unsorted());
        assertThat(eventsForTicket).hasSize(1);
        assertThat(eventsForTicket.getFirst().getEventType()).isEqualTo(TicketEventType.CREATED);
        assertThat(eventsForTicket.getFirst().getPayload()).containsEntry("ticketId", actualResult.id().toString());
    }

    @Test
    void shouldAssignTicketToAgentSuccessfully() {
        ticketService.assignTicket(ticket2.getId(), agent.getId(), agent.getId());
        flushAndClear();

        TicketEntity actualTicket = ticketRepo.findById(ticket2.getId()).orElseThrow();
        assertThat(actualTicket.getAssignedTo()).isNotNull();
        assertThat(actualTicket.getAssignedTo().getId()).isEqualTo(agent.getId());
        assertThat(actualTicket.getStatus()).isEqualTo(TicketStatus.NEW);

        List<TicketEventEntity> ticketEvents = eventRepo.findAllByTicketId(ticket2.getId(), Sort.by(Sort.Direction.DESC, "id"));
        assertThat(ticketEvents).hasSize(2);
        assertThat(ticketEvents.getFirst().getEventType()).isEqualTo(TicketEventType.ASSIGNED);
        assertThat(ticketEvents.getFirst().getPayload()).containsEntry("previousAssigneeId", null);
        assertThat(ticketEvents.getFirst().getPayload()).containsEntry("assigneeId", agent.getId().toString());
    }

    @Test
    void shouldAssignTicketToAnotherAgentSuccessfully() {
        UserEntity anotherAgent = DataUtils.getTransientUser("Oliver", "Queen", Role.AGENT);
        userRepo.saveAndFlush(anotherAgent);

        ticketService.assignTicket(ticket1.getId(), agent.getId(), anotherAgent.getId());
        flushAndClear();

        TicketEntity actualTicket = ticketRepo.findById(ticket1.getId()).orElseThrow();
        assertThat(actualTicket.getAssignedTo().getId()).isEqualTo(anotherAgent.getId());

        List<TicketEventEntity> ticketEvents = eventRepo.findAllByTicketId(ticket1.getId(), Sort.by(Sort.Direction.DESC, "id"));
        assertThat(ticketEvents).hasSize(7);
        assertThat(ticketEvents.getFirst().getEventType()).isEqualTo(TicketEventType.ASSIGNED);
        assertThat(ticketEvents.getFirst().getPayload()).containsEntry("previousAssigneeId", agent.getId().toString());
        assertThat(ticketEvents.getFirst().getPayload()).containsEntry("assigneeId", anotherAgent.getId().toString());
    }

    @Test
    void shouldDoNothingWhenAssigningTicketToTheSameAgent() {
        ticketService.assignTicket(ticket1.getId(), agent.getId(), agent.getId());
        flushAndClear();

        TicketEntity actualTicket = ticketRepo.findById(ticket1.getId()).orElseThrow();
        assertThat(actualTicket.getAssignedTo().getId()).isEqualTo(agent.getId());

        List<TicketEventEntity> ticketEvents = eventRepo.findAllByTicketId(ticket1.getId(), Sort.unsorted());
        assertThat(ticketEvents).hasSize(6);
    }

    @Test
    void shouldStartProgressOnUnassignedTicketSuccessfully() {
        ticketService.startProgressOnTicket(ticket2.getId(), agent.getId());
        flushAndClear();

        TicketEntity actualTicket = ticketRepo.findById(ticket2.getId()).orElseThrow();
        assertThat(actualTicket.getAssignedTo().getId()).isEqualTo(agent.getId());
        assertThat(actualTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        List<TicketEventEntity> ticketEvents = eventRepo.findAllByTicketId(ticket2.getId(), Sort.by(Sort.Direction.DESC, "id"));
        assertThat(ticketEvents).hasSize(3);
        assertThat(ticketEvents)
                .extracting(TicketEventEntity::getEventType)
                .containsExactly(TicketEventType.STATUS_CHANGED, TicketEventType.ASSIGNED, TicketEventType.CREATED);
    }

    @Test
    void shouldChangeTicketStatusToWaitingCustomerWhenAgentRequestsCustomerInfo() {
        final TicketStatus currentStatus = ticket1.getStatus();

        ticketService.requestCustomerInfo(ticket1.getId(), agent.getId());
        flushAndClear();

        TicketEntity actualTicket = ticketRepo.findById(ticket1.getId()).orElseThrow();
        assertThat(actualTicket.getStatus()).isEqualTo(TicketStatus.WAITING_CUSTOMER);

        List<TicketEventEntity> ticketEvents = eventRepo.findAllByTicketId(ticket1.getId(), Sort.by(Sort.Direction.DESC, "id"));
        assertThat(ticketEvents.getFirst().getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        assertThat(ticketEvents.getFirst().getPayload()).containsEntry("oldStatus", currentStatus.name());
    }

    @Test
    void shouldSetResolvedAtWhenTicketStatusChangedToResolved() {
        assertNull(ticket1.getResolvedAt());

        ticketService.resolveTicket(ticket1.getId(), agent.getId());
        flushAndClear();

        TicketEntity actualTicket = ticketRepo.findById(ticket1.getId()).orElseThrow();
        assertThat(actualTicket.getResolvedAt()).isNotNull();
    }

    @Test
    void shouldThrowExceptionWhenTryingToResolveTicketThatIsNotInProgress() {
        TicketEntity newTicket = DataUtils.getTransientTicket("dummy_title", "dummy_description", TicketStatus.NEW, TicketPriority.MEDIUM, customer, null);
        ticketRepo.saveAndFlush(newTicket);

        InvalidStatusTransitionException actualException = assertThrows(InvalidStatusTransitionException.class, () -> {
            ticketService.resolveTicket(newTicket.getId(), agent.getId());
        });

        assertThat(actualException).hasMessage(new InvalidStatusTransitionException(TicketStatus.NEW, TicketStatus.RESOLVED).getMessage());
    }

    @Test
    void shouldCloseTicketSuccessfully() {
        ticket1.setStatus(TicketStatus.RESOLVED);

        ticketService.closeTicketByCustomer(ticket1.getId(), customer.getId());
        flushAndClear();

        TicketEntity actualTicket = ticketRepo.findById(ticket1.getId()).orElseThrow();
        assertThat(actualTicket.getStatus()).isEqualTo(TicketStatus.CLOSED);

        List<TicketEventEntity> ticketEvents = eventRepo.findAllByTicketId(ticket1.getId(), Sort.by(Sort.Direction.DESC, "id"));
        assertThat(ticketEvents.getFirst().getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        assertThat(ticketEvents.getFirst().getPayload()).containsEntry("oldStatus", TicketStatus.RESOLVED.name());
    }

    @Test
    void shouldThrowExceptionWhenTryingToCloseTicketThatIsNotResolved() {
        final TicketStatus currentTicketStatus = ticket1.getStatus();

        InvalidStatusTransitionException actualException = assertThrows(InvalidStatusTransitionException.class, () -> {
            ticketService.closeTicketByCustomer(ticket1.getId(), customer.getId());
        });

        assertThat(actualException).hasMessage(new InvalidStatusTransitionException(currentTicketStatus, TicketStatus.CLOSED).getMessage());
    }

    @Test
    void shouldChangeTicketPrioritySuccessfully() {
        final TicketPriority currentPriority = ticket1.getPriority();

        ticketService.changePriority(ticket1.getId(), agent.getId(), TicketPriority.CRITICAL);
        flushAndClear();

        TicketEntity actualTicket = ticketRepo.findById(ticket1.getId()).orElseThrow();
        assertThat(actualTicket.getPriority()).isEqualTo(TicketPriority.CRITICAL);

        List<TicketEventEntity> ticketEvents = eventRepo.findAllByTicketId(ticket1.getId(), Sort.by(Sort.Direction.DESC, "id"));
        assertThat(ticketEvents.getFirst().getEventType()).isEqualTo(TicketEventType.PRIORITY_CHANGED);
        assertThat(ticketEvents.getFirst().getPayload()).containsEntry("oldPriority", currentPriority.name());
    }

    @Test
    @SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
    @Sql(scripts = "classpath:sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldThrowOptimisticLockingExceptionWhenConcurrentUpdate() {
        Long ticketId = null;

        // Setup ticket and user for other transactions
        try (EntityManager setUpEm = entityManagerFactory.createEntityManager()) {
            try {
                setUpEm.getTransaction().begin();

                UserEntity user = DataUtils.getTransientUser("Lana", "Lang", Role.CUSTOMER);
                setUpEm.persist(user);

                TicketEntity ticket = DataUtils.getTransientTicket("test_title", "test_desc", TicketStatus.NEW, TicketPriority.MEDIUM, user, null);
                setUpEm.persist(ticket);

                setUpEm.getTransaction().commit();
                ticketId = ticket.getId();
            } catch (Exception e) {
                if (setUpEm.getTransaction().isActive()) setUpEm.getTransaction().rollback();
                throw new RuntimeException("Failed to create ticket and user for the test", e);
            }
        }

        Throwable failure = null;
        try {
            // Simulate two concurrent transactions
            try (EntityManager em1 = entityManagerFactory.createEntityManager();
                 EntityManager em2 = entityManagerFactory.createEntityManager()) {

                em1.getTransaction().begin();
                TicketEntity ticketInTx1 = em1.find(TicketEntity.class, ticketId);

                em2.getTransaction().begin();
                TicketEntity ticketInTx2 = em2.find(TicketEntity.class, ticketId);

                ticketInTx1.setPriority(TicketPriority.CRITICAL);
                em1.getTransaction().commit();

                ticketInTx2.setPriority(TicketPriority.LOW);
                RollbackException actualException = assertThrows(RollbackException.class, () -> {
                    em2.getTransaction().commit();
                });
                if (em2.getTransaction().isActive()) em2.getTransaction().rollback();

                assertThat(actualException.getCause()).isInstanceOf(OptimisticLockException.class);

                TicketEntity actualTicket = ticketRepo.findById(ticketId).orElseThrow();
                assertThat(actualTicket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
                assertThat(actualTicket.getVersion()).isEqualTo(1);
            } catch (Throwable t) {
                failure = new RuntimeException("Failed to simulate concurrent transactions", t);
            }
        } finally {
            // Clean up after the test
            try (EntityManager cleanUpEm = entityManagerFactory.createEntityManager()) {
                try {
                    cleanUpEm.getTransaction().begin();

                    cleanUpEm.createQuery("DELETE FROM TicketEntity").executeUpdate();
                    cleanUpEm.createQuery("DELETE FROM UserEntity").executeUpdate();

                    cleanUpEm.getTransaction().commit();
                } catch (Exception e) {
                    if (cleanUpEm.getTransaction().isActive()) cleanUpEm.getTransaction().rollback();
                    if (failure == null) failure = new RuntimeException("Failed to clean up after the test", e);
                }
            }
        }

        if (failure != null) {
            throw new RuntimeException(failure);
        }
    }
}