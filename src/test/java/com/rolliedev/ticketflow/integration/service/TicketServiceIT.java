package com.rolliedev.ticketflow.integration.service;

import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.SlaStatus;
import com.rolliedev.ticketflow.entity.enums.TicketEventType;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidRequestException;
import com.rolliedev.ticketflow.exception.InvalidStatusTransitionException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.exception.TicketFlowAccessDeniedException;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.TicketService;
import com.rolliedev.ticketflow.testsupport.base.AbstractSpringBootIT;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketServiceIT extends AbstractSpringBootIT {

    @Autowired
    private TicketService ticketService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UserEntity admin, agent, customer;
    private TicketEntity ticket1, ticket2;

    @BeforeEach
    void setUp() {
        admin = userRepository.findByEmail("lex.luthor@gmail.com").orElseThrow();
        agent = userRepository.findByEmail("bruce.wayne@gmail.com").orElseThrow();
        customer = userRepository.findByEmail("clark.kent@gmail.com").orElseThrow();

        ticket1 = ticketRepository.findById(1L).orElseThrow();
        ticket2 = ticketRepository.findById(2L).orElseThrow();
    }

    @Test
    void shouldReturnAllTicketsWhenActorIsAdmin() {
        TicketFlowUserDetails adminDetails = new TicketFlowUserDetails(admin);
        TicketSearchFilter emptyFilter = TicketSearchFilter.builder().build();

        Page<TicketResponse> actualResult = ticketService.findAll(emptyFilter, PageRequest.of(0, 10), adminDetails);

        assertThat(actualResult.getTotalElements()).isEqualTo(5);
        assertThat(actualResult.getContent())
                .extracting(TicketResponse::id)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void shouldReturnOnlyOwnTicketsWhenActorIsCustomer() {
        TicketFlowUserDetails customerDetails = new TicketFlowUserDetails(customer);
        TicketSearchFilter emptyFilter = TicketSearchFilter.builder().build();

        Page<TicketResponse> actualResult = ticketService.findAll(emptyFilter, PageRequest.of(0, 10), customerDetails);

        assertThat(actualResult.getTotalElements()).isEqualTo(3);
        assertThat(actualResult.getContent())
                .extracting(TicketResponse::id)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void shouldReturnOnlyTicketsWithMatchingStatusWhenStatusFilterIsProvided() {
        TicketFlowUserDetails adminDetails = new TicketFlowUserDetails(admin);
        TicketSearchFilter filter = TicketSearchFilter.builder()
                .status(TicketStatus.NEW)
                .build();

        Page<TicketResponse> actualResult = ticketService.findAll(filter, PageRequest.of(0, 10), adminDetails);

        assertThat(actualResult.getTotalElements()).isEqualTo(2);
        assertThat(actualResult.getContent())
                .extracting(TicketResponse::status)
                .containsOnly(TicketStatus.NEW);
    }

    @Test
    void shouldReturnOnlyTicketsWithMatchingPriorityWhenPriorityFilterIsProvided() {
        TicketFlowUserDetails adminDetails = new TicketFlowUserDetails(admin);
        TicketSearchFilter filter = TicketSearchFilter.builder()
                .priority(TicketPriority.HIGH)
                .build();

        Page<TicketResponse> actualResult = ticketService.findAll(filter, PageRequest.of(0, 10), adminDetails);

        assertThat(actualResult.getTotalElements()).isEqualTo(1);
        assertThat(actualResult.getContent().getFirst().id()).isEqualTo(1L);
    }

    @Test
    void shouldReturnTicketWhenActorIsAdmin() {
        TicketFlowUserDetails adminDetails = new TicketFlowUserDetails(admin);

        Optional<TicketResponse> actualResult = ticketService.findById(ticket1.getId(), adminDetails);

        assertThat(actualResult).isPresent();
        assertThat(actualResult.get().id()).isEqualTo(ticket1.getId());
    }

    @Test
    void shouldReturnOwnTicketWhenActorIsCustomer() {
        TicketFlowUserDetails customerDetails = new TicketFlowUserDetails(customer);

        Optional<TicketResponse> actualResult = ticketService.findById(ticket1.getId(), customerDetails);

        assertThat(actualResult).isPresent();
        assertThat(actualResult.get().id()).isEqualTo(ticket1.getId());
    }

    @Test
    void shouldReturnEmptyOptionalWhenActorIsCustomerAndTicketIsNotOwnedByThem() {
        TicketFlowUserDetails customerDetails = new TicketFlowUserDetails(customer);
        TicketEntity notCustomerTicket = ticketRepository.findById(4L).orElseThrow();

        Optional<TicketResponse> actualResult = ticketService.findById(notCustomerTicket.getId(), customerDetails);

        assertThat(actualResult).isEmpty();
    }

    @Test
    void shouldReturnEmptyOptionalWhenTicketDoesNotExist() {
        TicketFlowUserDetails adminDetails = new TicketFlowUserDetails(admin);

        Optional<TicketResponse> actualResult = ticketService.findById(999L, adminDetails);

        assertThat(actualResult).isEmpty();
    }

    @Test
    void shouldPersistTicketWithCorrectDefaultsInitializeSlaAndRecordCreatedEvent() {
        CreateTicketRequest request = new CreateTicketRequest(
                "New issue",
                "Something is not working"
        );

        TicketResponse actualResult = ticketService.create(request, customer.getId());
        flushAndClear();

        TicketEntity persisted = ticketRepository.findById(actualResult.id()).orElseThrow();

        assertThat(persisted.getTitle()).isEqualTo("New issue");
        assertThat(persisted.getDescription()).isEqualTo("Something is not working");
        assertThat(persisted.getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(persisted.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(persisted.getCreatedBy().getId()).isEqualTo(customer.getId());
        assertThat(persisted.getAssignedTo()).isNull();

        assertThat(persisted.getFirstRespondedAt()).isNull();
        assertThat(persisted.getResolvedAt()).isNull();
        assertThat(persisted.getFirstResponseDeadline()).isNotNull();
        assertThat(persisted.getResolutionDeadline()).isNotNull();
        assertThat(persisted.getResponseSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(persisted.getResolutionSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(persisted.getResolutionSlaPausedAt()).isNull();

        assertThat(persisted.getFirstResponseDeadline()).isAfter(persisted.getCreatedAt());
        assertThat(persisted.getResolutionDeadline()).isAfter(persisted.getCreatedAt());

        assertThat(hasEvent(persisted.getId(), TicketEventType.CREATED)).isTrue();
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenCreatorDoesNotExistDuringCreate() {
        CreateTicketRequest request = new CreateTicketRequest("Title", "Description");

        assertThatThrownBy(() -> ticketService.create(request, 999))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void shouldAssignUnassignedTicketAndRecordEventWhenActorIsAdmin() {
        // ticket2 is unassigned
        ticketService.assign(ticket2.getId(), admin.getId(), agent.getId());
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket2.getId()).orElseThrow();

        assertThat(updated.getAssignedTo().getId()).isEqualTo(agent.getId());
        assertThat(hasEvent(ticket2.getId(), TicketEventType.ASSIGNED)).isTrue();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void shouldReassignTicketWhenActorIsAdmin() {
        // ticket1 is assigned to agent — admin can reassign
        ticketService.assign(ticket1.getId(), admin.getId(), admin.getId());
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket1.getId()).orElseThrow();

        assertThat(updated.getAssignedTo().getId()).isEqualTo(admin.getId());
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldThrowAccessDeniedExceptionWhenAgentTriesToReassignTicketNotOwnedByThem() {
        UserEntity anotherAgent = DataUtils.getTransientUser("Will", "Smith", Role.AGENT);
        userRepository.saveAndFlush(anotherAgent);

        assertThatThrownBy(() -> ticketService.assign(ticket1.getId(), anotherAgent.getId(), anotherAgent.getId()))
                .isInstanceOf(TicketFlowAccessDeniedException.class)
                .hasMessage("Only the ticket assignee or admin can reassign tickets");
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void shouldThrowInvalidRequestExceptionWhenAssigningToCustomer() {
        assertThatThrownBy(() -> ticketService.assign(ticket2.getId(), admin.getId(), customer.getId()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only agents or admins can be assigned to tickets");
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void shouldThrowBusinessRuleViolationExceptionWhenAssigningToClosedTicket() {
        ticket2.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(ticket2);
        flushAndClear();

        assertThatThrownBy(() -> ticketService.assign(ticket2.getId(), admin.getId(), agent.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Closed tickets cannot be assigned");
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void shouldNotRecordEventWhenAssigneeIsAlreadyAssigned() {
        long eventCountBefore = eventCount(ticket1.getId());

        ticketService.assign(ticket1.getId(), admin.getId(), agent.getId());
        flushAndClear();

        long eventCountAfter = eventCount(ticket1.getId());

        assertThat(eventCountAfter).isEqualTo(eventCountBefore);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldAutoAssignTicketAndChangeStatusToInProgressWhenTicketIsNewAndUnassigned() {
        // ticket2 is NEW and unassigned
        ticketService.startProgress(ticket2.getId(), agent.getId());
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket2.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(updated.getAssignedTo().getId()).isEqualTo(agent.getId());

        assertThat(events(ticket2.getId()))
                .extracting(TicketEventEntity::getEventType)
                .contains(TicketEventType.ASSIGNED, TicketEventType.STATUS_CHANGED);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldResumeResolutionSlaAndExtendDeadlineWhenWaitingCustomerTicketIsStartedAgain() {
        Instant originalDeadline = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant pausedAt = Instant.now().minus(2, ChronoUnit.HOURS);

        ticket1.setStatus(TicketStatus.WAITING_CUSTOMER);
        ticket1.setAssignedTo(agent);
        ticket1.setResolutionDeadline(originalDeadline);
        ticket1.setResolutionSlaStatus(SlaStatus.PAUSED);
        ticket1.setResolutionSlaPausedAt(pausedAt);
        ticket1.setResolvedAt(null);
        ticketRepository.saveAndFlush(ticket1);
        flushAndClear();

        ticketService.startProgress(ticket1.getId(), agent.getId());
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket1.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(updated.getResolutionSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(updated.getResolutionSlaPausedAt()).isNull();
        assertThat(updated.getResolutionDeadline()).isAfter(originalDeadline);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldClearResolvedAtAndResumeInternalWorkWithoutExtendingDeadlineWhenResolvedTicketIsStartedAgain() {
        Instant originalDeadline = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant resolvedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        ticket2.setStatus(TicketStatus.RESOLVED);
        ticket2.setAssignedTo(agent);
        ticket2.setResolvedAt(resolvedAt);
        ticket2.setResolutionDeadline(originalDeadline);
        ticket2.setResolutionSlaStatus(SlaStatus.PAUSED);
        ticket2.setResolutionSlaPausedAt(resolvedAt);
        ticketRepository.saveAndFlush(ticket2);
        flushAndClear();

        ticketService.startProgress(ticket2.getId(), agent.getId());
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket2.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(updated.getResolvedAt()).isNull();
        assertThat(updated.getResolutionSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(updated.getResolutionSlaPausedAt()).isNull();
        assertThat(updated.getResolutionDeadline()).isEqualTo(originalDeadline);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldNotRecordNewEventWhenTicketIsAlreadyInProgress() {
        // ticket1 is already IN_PROGRESS
        long eventCountBefore = eventCount(ticket1.getId());

        ticketService.startProgress(ticket1.getId(), agent.getId());

        long eventCountAfter = eventCount(ticket1.getId());

        assertThat(eventCountAfter).isEqualTo(eventCountBefore);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldChangeStatusToWaitingCustomerSetFirstResponseAndPauseResolutionSlaWhenTicketIsInProgress() {
        prepareInProgressTicketWithActiveSlas(ticket1);

        ticketService.requestCustomerInfo(ticket1.getId(), agent.getId());
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket1.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.WAITING_CUSTOMER);
        assertThat(updated.getFirstRespondedAt()).isNotNull();
        assertThat(updated.getResponseSlaStatus()).isEqualTo(SlaStatus.MET);
        assertThat(updated.getResolutionSlaStatus()).isEqualTo(SlaStatus.PAUSED);
        assertThat(updated.getResolutionSlaPausedAt()).isNotNull();

        assertThat(latestEvent(ticket1.getId()).getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldNotRecordNewEventWhenTicketIsAlreadyWaitingForCustomer() {
        ticket1.setStatus(TicketStatus.WAITING_CUSTOMER);
        ticketRepository.save(ticket1);
        flushAndClear();

        long eventCountBefore = eventCount(ticket1.getId());

        ticketService.requestCustomerInfo(ticket1.getId(), agent.getId());

        long eventCountAfter = eventCount(ticket1.getId());

        assertThat(eventCountAfter).isEqualTo(eventCountBefore);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldThrowInvalidStatusTransitionExceptionWhenChangingNewTicketToWaitingCustomer() {
        ticket2.setAssignedTo(agent);
        ticketRepository.save(ticket2);
        flushAndClear();

        assertThatThrownBy(() -> ticketService.requestCustomerInfo(ticket2.getId(), agent.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage(new InvalidStatusTransitionException(TicketStatus.NEW, TicketStatus.WAITING_CUSTOMER).getMessage());
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldResolveTicketSetResolvedAtFirstResponseAndPauseResolutionSlaWhenActorCanResolveTicket() {
        prepareInProgressTicketWithActiveSlas(ticket1);

        ticketService.resolve(ticket1.getId(), agent.getId());
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket1.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(updated.getResolvedAt()).isNotNull();
        assertThat(updated.getFirstRespondedAt()).isEqualTo(updated.getResolvedAt());
        assertThat(updated.getResponseSlaStatus()).isEqualTo(SlaStatus.MET);
        assertThat(updated.getResolutionSlaStatus()).isEqualTo(SlaStatus.PAUSED);
        assertThat(updated.getResolutionSlaPausedAt()).isEqualTo(updated.getResolvedAt());

        assertThat(latestEvent(ticket1.getId()).getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldResolveWaitingCustomerTicketByResumingThenPausingResolutionSlaAgain() {
        Instant originalDeadline = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant pausedAt = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant firstRespondedAt = Instant.now().minus(3, ChronoUnit.HOURS);

        ticket1.setStatus(TicketStatus.WAITING_CUSTOMER);
        ticket1.setAssignedTo(agent);
        ticket1.setFirstRespondedAt(firstRespondedAt);
        ticket1.setFirstResponseDeadline(Instant.now().plus(1, ChronoUnit.DAYS));
        ticket1.setResponseSlaStatus(SlaStatus.MET);
        ticket1.setResolutionDeadline(originalDeadline);
        ticket1.setResolutionSlaStatus(SlaStatus.PAUSED);
        ticket1.setResolutionSlaPausedAt(pausedAt);
        ticket1.setResolvedAt(null);
        ticketRepository.save(ticket1);
        flushAndClear();

        ticketService.resolve(ticket1.getId(), agent.getId());
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket1.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(updated.getResolvedAt()).isNotNull();
        assertThat(updated.getFirstRespondedAt()).isEqualTo(firstRespondedAt);
        assertThat(updated.getResolutionSlaStatus()).isEqualTo(SlaStatus.PAUSED);
        assertThat(updated.getResolutionSlaPausedAt()).isEqualTo(updated.getResolvedAt());
        assertThat(updated.getResolutionDeadline()).isAfter(originalDeadline);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldNotRecordNewEventWhenTicketIsAlreadyResolved() {
        ticket1.setStatus(TicketStatus.RESOLVED);
        ticket1.setResolvedAt(Instant.now());
        ticketRepository.save(ticket1);
        flushAndClear();

        long eventCountBefore = eventCount(ticket1.getId());

        ticketService.resolve(ticket1.getId(), agent.getId());

        long eventCountAfter = eventCount(ticket1.getId());

        assertThat(eventCountAfter).isEqualTo(eventCountBefore);
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void shouldCloseTicketAndMarkResolutionSlaAsMetWhenResolvedTicketIsClosedByCustomer() {
        Instant resolvedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        ticket1.setStatus(TicketStatus.RESOLVED);
        ticket1.setResolvedAt(resolvedAt);
        ticket1.setResolutionDeadline(Instant.now().plus(2, ChronoUnit.DAYS));
        ticket1.setResolutionSlaStatus(SlaStatus.PAUSED);
        ticket1.setResolutionSlaPausedAt(resolvedAt);
        ticketRepository.save(ticket1);
        flushAndClear();

        ticketService.closeByCustomer(ticket1.getId(), customer.getId());
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket1.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(updated.getResolutionSlaStatus()).isEqualTo(SlaStatus.MET);
        assertThat(updated.getResolutionSlaPausedAt()).isNull();
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void shouldNotRecordNewEventWhenTicketIsAlreadyClosed() {
        ticket1.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(ticket1);
        flushAndClear();

        long eventCountBefore = eventCount(ticket1.getId());

        ticketService.closeByCustomer(ticket1.getId(), customer.getId());

        long eventCountAfter = eventCount(ticket1.getId());

        assertThat(eventCountAfter).isEqualTo(eventCountBefore);
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void shouldThrowInvalidStatusTransitionExceptionWhenCustomerClosesTicketThatIsNotResolved() {
        assertThatThrownBy(() -> ticketService.closeByCustomer(ticket1.getId(), customer.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage(new InvalidStatusTransitionException(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED).getMessage());
    }

    @Test
    void shouldAutoCloseResolvedTicketsOlderThanThresholdAndFinalizeResolutionSla() {
        Instant threshold = Instant.parse("2026-02-25T00:00:00Z");
        Instant oldResolvedAt = threshold.minus(1, ChronoUnit.HOURS);
        Instant recentResolvedAt = threshold.plus(1, ChronoUnit.HOURS);

        ticket1.setStatus(TicketStatus.RESOLVED);
        ticket1.setResolvedAt(oldResolvedAt);
        ticket1.setResolutionSlaStatus(SlaStatus.PAUSED);
        ticket1.setResolutionSlaPausedAt(oldResolvedAt);
        ticketRepository.save(ticket1);

        ticket2.setStatus(TicketStatus.RESOLVED);
        ticket2.setResolvedAt(recentResolvedAt);
        ticket2.setResolutionSlaStatus(SlaStatus.PAUSED);
        ticket2.setResolutionSlaPausedAt(recentResolvedAt);
        ticketRepository.save(ticket2);

        flushAndClear();

        int closedCount = ticketService.closeResolvedTicketsOlderThan(threshold);
        flushAndClear();

        TicketEntity closedTicket = ticketRepository.findById(ticket1.getId()).orElseThrow();
        TicketEntity stillResolvedTicket = ticketRepository.findById(ticket2.getId()).orElseThrow();

        assertThat(closedCount).isEqualTo(1);

        assertThat(closedTicket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(closedTicket.getResolutionSlaStatus()).isEqualTo(SlaStatus.MET);
        assertThat(closedTicket.getResolutionSlaPausedAt()).isNull();

        assertThat(stillResolvedTicket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(stillResolvedTicket.getResolutionSlaStatus()).isEqualTo(SlaStatus.PAUSED);
        assertThat(stillResolvedTicket.getResolutionSlaPausedAt()).isNotNull();
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldChangePriorityRecordEventAndUpdateActiveSlaDeadlinesWhenActorCanModifyTicket() {
        CreateTicketRequest request = new CreateTicketRequest(
                "Priority change SLA test",
                "Testing priority change inside triage window"
        );

        TicketResponse createdResponse = ticketService.create(request, customer.getId());
        flushAndClear();

        TicketEntity ticket = ticketRepository.findById(createdResponse.id()).orElseThrow();

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setAssignedTo(agent);
        ticket.setResolutionSlaPausedAt(null);
        ticket.setResolvedAt(null);

        ticketRepository.save(ticket);
        flushAndClear();

        TicketEntity preparedTicket = ticketRepository.findById(createdResponse.id()).orElseThrow();

        Instant originalFirstResponseDeadline = preparedTicket.getFirstResponseDeadline();
        Instant originalResolutionDeadline = preparedTicket.getResolutionDeadline();

        ticketService.changePriority(preparedTicket.getId(), agent.getId(), TicketPriority.LOW);
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(preparedTicket.getId()).orElseThrow();

        assertThat(updated.getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(updated.getFirstResponseDeadline()).isAfter(originalFirstResponseDeadline);
        assertThat(updated.getResolutionDeadline()).isAfter(originalResolutionDeadline);

        assertThat(latestEvent(updated.getId()).getEventType()).isEqualTo(TicketEventType.PRIORITY_CHANGED);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldNotRecordNewEventWhenPriorityIsAlreadyTheSame() {
        long eventCountBefore = eventCount(ticket1.getId());

        ticketService.changePriority(ticket1.getId(), agent.getId(), TicketPriority.HIGH);

        long eventCountAfter = eventCount(ticket1.getId());

        assertThat(eventCountAfter).isEqualTo(eventCountBefore);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldThrowAccessDeniedExceptionWhenActorIsNotAssigneeOrAdmin() {
        assertThatThrownBy(() -> ticketService.changePriority(ticket2.getId(), agent.getId(), TicketPriority.LOW))
                .isInstanceOf(TicketFlowAccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "AGENT")
    void shouldThrowBusinessRuleViolationExceptionWhenChangingPriorityOfClosedTicket() {
        ticket1.setStatus(TicketStatus.CLOSED);
        ticket1.setAssignedTo(agent);
        ticketRepository.save(ticket1);
        flushAndClear();

        assertThatThrownBy(() -> ticketService.changePriority(ticket1.getId(), agent.getId(), TicketPriority.LOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Closed tickets cannot be modified");
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

                Instant createdAt = Instant.now();

                TicketEntity ticket = DataUtils.getTransientTicket(
                        "test_title",
                        "test_desc",
                        TicketStatus.NEW,
                        TicketPriority.MEDIUM,
                        user,
                        null
                );

                ticket.setCreatedAt(createdAt);
                ticket.setFirstResponseDeadline(createdAt.plus(1, ChronoUnit.DAYS));
                ticket.setResolutionDeadline(createdAt.plus(3, ChronoUnit.DAYS));
                ticket.setResponseSlaStatus(SlaStatus.ON_TRACK);
                ticket.setResolutionSlaStatus(SlaStatus.ON_TRACK);
                ticket.setResolutionSlaPausedAt(null);

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

                TicketEntity actualTicket = ticketRepository.findById(ticketId).orElseThrow();
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

    private void prepareInProgressTicketWithActiveSlas(TicketEntity ticket) {
        Instant now = Instant.now();

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setAssignedTo(agent);
        ticket.setFirstRespondedAt(null);
        ticket.setFirstResponseDeadline(now.plus(1, ChronoUnit.HOURS));
        ticket.setResponseSlaStatus(SlaStatus.ON_TRACK);
        ticket.setResolutionDeadline(now.plus(2, ChronoUnit.DAYS));
        ticket.setResolutionSlaStatus(SlaStatus.ON_TRACK);
        ticket.setResolutionSlaPausedAt(null);
        ticket.setResolvedAt(null);

        ticketRepository.save(ticket);
        flushAndClear();
    }

    private boolean hasEvent(Long ticketId, TicketEventType eventType) {
        return events(ticketId).stream()
                .anyMatch(e -> e.getEventType() == eventType);
    }

    private List<TicketEventEntity> events(Long ticketId) {
        return eventRepository
                .findAllByTicketId(ticketId, Pageable.unpaged())
                .getContent();
    }

    private long eventCount(Long ticketId) {
        return eventRepository
                .findAllByTicketId(ticketId, Pageable.unpaged())
                .getTotalElements();
    }

    private TicketEventEntity latestEvent(Long ticketId) {
        return eventRepository
                .findAllByTicketId(
                        ticketId,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"))
                )
                .getContent()
                .getFirst();
    }
}