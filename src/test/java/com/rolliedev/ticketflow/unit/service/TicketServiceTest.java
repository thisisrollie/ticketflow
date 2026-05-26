package com.rolliedev.ticketflow.unit.service;

import com.querydsl.core.types.Predicate;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidRequestException;
import com.rolliedev.ticketflow.exception.InvalidStatusTransitionException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.exception.TicketFlowAccessDeniedException;
import com.rolliedev.ticketflow.mapper.TicketResponseMapper;
import com.rolliedev.ticketflow.policy.AccessPolicy;
import com.rolliedev.ticketflow.querydsl.TicketPredicateBuilder;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.SlaService;
import com.rolliedev.ticketflow.service.TicketEventService;
import com.rolliedev.ticketflow.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final Integer CUSTOMER_ID = 1;
    private static final Integer AGENT_ID = 2;
    private static final Integer ADMIN_ID = 3;
    private static final Long TICKET_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketEventService eventService;
    @Mock
    private TicketResponseMapper ticketResponseMapper;
    @Spy
    private TicketPredicateBuilder ticketPredicateBuilder;
    @Spy
    private AccessPolicy accessPolicy;
    @Mock
    private SlaService slaService;

    @InjectMocks
    private TicketService ticketService;

    @Test
    void shouldFindTicketSuccessfully() {
        TicketFlowUserDetails currentUser = new TicketFlowUserDetails(UserEntity.builder().role(Role.ADMIN).build());

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .build();
        TicketResponse ticketResponse = mock(TicketResponse.class);

        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);
        doReturn(ticketResponse).when(ticketResponseMapper).map(ticket);

        Optional<TicketResponse> actualResult = ticketService.findById(TICKET_ID, currentUser);

        assertThat(actualResult).isPresent();
        assertThat(actualResult.get()).isEqualTo(ticketResponse);

        verify(ticketRepository).findById(TICKET_ID);
        verify(ticketResponseMapper).map(ticket);
    }

    @Test
    void shouldReturnEmptyOptionalWhenTicketNotFound() {
        TicketFlowUserDetails currentUser = new TicketFlowUserDetails(UserEntity.builder().role(Role.ADMIN).build());

        doReturn(Optional.empty()).when(ticketRepository).findById(TICKET_ID);

        Optional<TicketResponse> actualResult = ticketService.findById(TICKET_ID, currentUser);

        assertThat(actualResult).isEmpty();

        verify(ticketRepository).findById(TICKET_ID);
        verify(ticketResponseMapper, never()).map(any(TicketEntity.class));
    }

    @Test
    void shouldFindAllSuccessfullyWhenSearchFilterIsEmpty() {
        TicketFlowUserDetails currentUser = new TicketFlowUserDetails(UserEntity.builder().role(Role.ADMIN).build());

        TicketSearchFilter searchFilter = TicketSearchFilter.builder().build();
        Pageable pageable = PageRequest.of(0, 10);
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .build();

        doReturn(new PageImpl<>(List.of(ticket), pageable, 1))
                .when(ticketRepository).findAll(any(Predicate.class), eq(pageable));

        ticketService.findAll(searchFilter, pageable, currentUser);

        verify(ticketRepository).findAll(any(Predicate.class), eq(pageable));
        verify(ticketResponseMapper).map(any(TicketEntity.class));
    }

    @Test
    void shouldReturnEmptyListWhenNoTicketsFound() {
        TicketFlowUserDetails currentUser = new TicketFlowUserDetails(UserEntity.builder().role(Role.ADMIN).build());

        TicketSearchFilter searchFilter = TicketSearchFilter.builder()
                .creatorId(CUSTOMER_ID)
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        doReturn(new PageImpl<TicketEntity>(Collections.emptyList(), pageable, 0))
                .when(ticketRepository).findAll(any(Predicate.class), eq(pageable));

        ticketService.findAll(searchFilter, pageable, currentUser);

        verify(ticketRepository).findAll(any(Predicate.class), eq(pageable));
        verify(ticketResponseMapper, never()).map(any(TicketEntity.class));
    }

    @Test
    void shouldCreateTicketAndRecordTicketEventSuccessfully() {
        CreateTicketRequest createRequest = new CreateTicketRequest(
                "Can't log in",
                "Getting error when logging in with Google"
        );
        UserEntity creator = UserEntity.builder()
                .id(CUSTOMER_ID)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .createdBy(creator)
                .build();

        doReturn(Optional.of(creator)).when(userRepository).findById(CUSTOMER_ID);
        ArgumentCaptor<TicketEntity> argumentCaptor = ArgumentCaptor.forClass(TicketEntity.class);
        doReturn(ticket).when(ticketRepository).save(argumentCaptor.capture());
        doReturn(mock(TicketResponse.class)).when(ticketResponseMapper).map(ticket);

        ticketService.create(createRequest, creator.getId());

        assertThat(argumentCaptor.getValue().getTitle()).isEqualTo(createRequest.title());
        assertThat(argumentCaptor.getValue().getDescription()).isEqualTo(createRequest.description());
        assertThat(argumentCaptor.getValue().getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(argumentCaptor.getValue().getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(argumentCaptor.getValue().getCreatedBy()).isEqualTo(creator);

        verify(userRepository).findById(CUSTOMER_ID);
        verify(ticketRepository).save(any(TicketEntity.class));
        verify(slaService).initializeSlaForNewTicket(ticket);
        verify(eventService).recordCreatedEvent(ticket, creator);
        verify(ticketResponseMapper).map(ticket);
    }

    @Test
    void shouldNotCreateTicketAndThrowExceptionWhenUserNotFound() {
        CreateTicketRequest createRequest = new CreateTicketRequest(
                "Can't log in",
                "Getting error when logging in with Google"
        );

        doReturn(Optional.empty()).when(userRepository).findById(CUSTOMER_ID);

        assertThatThrownBy(() -> ticketService.create(createRequest, CUSTOMER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ResourceNotFoundException.user(CUSTOMER_ID).getMessage());

        verifyNoInteractions(ticketRepository, slaService, eventService, ticketResponseMapper);
    }

    @Test
    void shouldAssignTicketSuccessfully() {
        // given
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.NEW)
                .build();

        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        // when
        ticketService.assign(ticket.getId(), admin.getId(), agent.getId());

        // then
        assertThat(ticket.getAssignedTo()).isEqualTo(agent);

        verify(eventService).recordAssignedEvent(ticket, admin, null, agent);
    }

    @Test
    void shouldThrowExceptionWhenAssigningTicketToUserWhoIsNotAgentNorAdmin() {
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();

        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        assertThatThrownBy(() -> ticketService.assign(TICKET_ID, ADMIN_ID, CUSTOMER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Only agents or admins can be assigned to tickets");

        verify(userRepository).findById(ADMIN_ID);
        verify(userRepository).findById(CUSTOMER_ID);
        verifyNoInteractions(ticketRepository, eventService);
    }

    @Test
    void shouldThrowExceptionWhenAssigningClosedTicket() {
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        TicketEntity closedTicket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.CLOSED)
                .build();

        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(closedTicket)).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> ticketService.assign(TICKET_ID, ADMIN_ID, AGENT_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Closed tickets cannot be assigned");

        assertThat(closedTicket.getAssignedTo()).isNull();

        verify(userRepository, times(2)).findById(Mockito.anyInt());
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(eventService);
    }

    @Test
    void shouldNotRecordAssignEventWhenAssigningTicketToSameUser() {
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        TicketEntity newAssignedTicket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.NEW)
                .assignedTo(agent)
                .build();

        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(newAssignedTicket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.assign(newAssignedTicket.getId(), admin.getId(), agent.getId());

        verify(userRepository, times(2)).findById(Mockito.anyInt());
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(eventService);
    }

    @Test
    void shouldStartProgressFromNewTicketSuccessfully() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.NEW)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.startProgress(TICKET_ID, AGENT_ID);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getAssignedTo()).isEqualTo(agent);

        verify(eventService).recordAssignedEvent(ticket, agent, null, agent);
        verify(eventService).recordStatusChangedEvent(ticket, agent, TicketStatus.NEW, TicketStatus.IN_PROGRESS);

        verifyNoInteractions(slaService);
    }

    @Test
    void shouldStartProgressFromWaitingCustomerAndResumeResolutionSla() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.WAITING_CUSTOMER)
                .assignedTo(agent)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.startProgress(TICKET_ID, AGENT_ID);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        verify(slaService).resumeResolutionSlaClock(eq(ticket), any(Instant.class));
        verify(slaService, never()).handleResolvedTicketReopenedByInternalUser(any(), any(), any());

        verify(eventService).recordStatusChangedEvent(
                ticket,
                agent,
                TicketStatus.WAITING_CUSTOMER,
                TicketStatus.IN_PROGRESS
        );
    }

    @Test
    void shouldStartProgressFromResolvedAndHandleInternalReopen() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.RESOLVED)
                .assignedTo(agent)
                .resolvedAt(Instant.parse("2026-05-20T10:00:00Z"))
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.startProgress(TICKET_ID, AGENT_ID);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getResolvedAt()).isNull();

        verify(slaService).handleResolvedTicketReopenedByInternalUser(
                eq(ticket),
                eq(agent),
                any(Instant.class)
        );

        verify(slaService, never()).resumeResolutionSlaClock(any(), any());

        verify(eventService).recordStatusChangedEvent(
                ticket,
                agent,
                TicketStatus.RESOLVED,
                TicketStatus.IN_PROGRESS
        );
    }

    @Test
    void shouldThrowExceptionIfUserWhoIsNotAssignedToTicketTryToStartProgressOnAssignedTicket() {
        UserEntity anotherAgent = UserEntity.builder()
                .id(99)
                .role(Role.AGENT)
                .build();

        TicketEntity assignedTicket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.NEW)
                .assignedTo(UserEntity.builder()
                        .id(AGENT_ID)
                        .build())
                .build();

        doReturn(Optional.of(anotherAgent)).when(userRepository).findById(99);
        doReturn(Optional.of(assignedTicket)).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> ticketService.startProgress(assignedTicket.getId(), anotherAgent.getId()))
                .isInstanceOf(TicketFlowAccessDeniedException.class)
                .hasMessage("Only the ticket assignee or admin can start progress on the ticket");
        assertThat(assignedTicket.getStatus()).isEqualTo(TicketStatus.NEW);

        verify(userRepository).findById(99);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService);
    }

    @Test
    void shouldThrowExceptionIfTryingToStartProgressOnTicketThatIsClosed() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.CLOSED)
                .assignedTo(agent)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> ticketService.startProgress(ticket.getId(), agent.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage(new InvalidStatusTransitionException(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS).getMessage());
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService);
    }

    @Test
    void shouldNotRecordEventWhenTryingToStartProgressOnTicketThatIsAlreadyInProgress() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .assignedTo(agent)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.startProgress(ticket.getId(), agent.getId());

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService);
    }

    @Test
    void shouldRequestCustomerInfoCountAsFirstResponseAndPauseResolutionSla() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .assignedTo(agent)
                .firstRespondedAt(null)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.requestCustomerInfo(ticket.getId(), agent.getId());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.WAITING_CUSTOMER);
        assertThat(ticket.getFirstRespondedAt()).isNotNull();

        ArgumentCaptor<Instant> requestedAtCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(slaService).evaluateFirstResponse(ticket, agent);
        verify(slaService).pauseResolutionSlaClock(
                eq(ticket),
                eq(agent),
                requestedAtCaptor.capture()
        );

        assertThat(ticket.getFirstRespondedAt()).isEqualTo(requestedAtCaptor.getValue());

        verify(eventService).recordStatusChangedEvent(
                ticket,
                agent,
                TicketStatus.IN_PROGRESS,
                TicketStatus.WAITING_CUSTOMER
        );
    }

    @Test
    void shouldRequestCustomerInfoWithoutReevaluatingFirstResponseWhenAlreadyResponded() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();

        Instant firstRespondedAt = Instant.parse("2026-05-20T09:00:00Z");

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .assignedTo(agent)
                .firstRespondedAt(firstRespondedAt)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.requestCustomerInfo(ticket.getId(), agent.getId());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.WAITING_CUSTOMER);
        assertThat(ticket.getFirstRespondedAt()).isEqualTo(firstRespondedAt);

        verify(slaService, never()).evaluateFirstResponse(any(), any());
        verify(slaService).pauseResolutionSlaClock(eq(ticket), eq(agent), any(Instant.class));
    }

    @Test
    void shouldThrowExceptionIfAgentWhoIsNotTicketAssigneeNorAdminTryToRequestCustomerInfo() {
        UserEntity anotherAgent = UserEntity.builder()
                .id(99)
                .role(Role.AGENT)
                .build();
        TicketEntity assignedTicket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .assignedTo(UserEntity.builder()
                        .id(AGENT_ID)
                        .role(Role.AGENT)
                        .build())
                .build();

        doReturn(Optional.of(anotherAgent)).when(userRepository).findById(99);
        doReturn(Optional.of(assignedTicket)).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> ticketService.requestCustomerInfo(assignedTicket.getId(), anotherAgent.getId()))
                .isInstanceOf(TicketFlowAccessDeniedException.class)
                .hasMessage("Only the ticket assignee or admin can request customer info");

        verify(userRepository).findById(anotherAgent.getId());
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService, ticketResponseMapper);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"NEW", "RESOLVED", "CLOSED"}, mode = EnumSource.Mode.INCLUDE)
    void shouldThrowExceptionIfTryingToDoInvalidTransitionWhenChangingTicketStatusToWaitingCustomer(TicketStatus currentStatus) {
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(currentStatus)
                .build();

        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> ticketService.requestCustomerInfo(ticket.getId(), admin.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage(new InvalidStatusTransitionException(currentStatus, TicketStatus.WAITING_CUSTOMER).getMessage());
        assertThat(ticket.getStatus()).isEqualTo(currentStatus);

        verify(userRepository).findById(ADMIN_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService);
    }

    @Test
    void shouldNotRecordEventWhenTryingToRequestCustomerInfoOnTicketThatIsAlreadyWaitingCustomer() {
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.WAITING_CUSTOMER)
                .build();

        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.requestCustomerInfo(ticket.getId(), admin.getId());

        verify(userRepository).findById(ADMIN_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService);
    }

    @Test
    void shouldResolveInProgressTicketAndPauseResolutionSla() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .assignedTo(agent)
                .firstRespondedAt(null)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.resolve(ticket.getId(), agent.getId());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();
        assertThat(ticket.getFirstRespondedAt()).isEqualTo(ticket.getResolvedAt());

        verify(slaService).evaluateFirstResponse(ticket, agent);
        verify(slaService, never()).resumeResolutionSlaClock(any(), any());
        verify(slaService).pauseResolutionSlaClock(eq(ticket), eq(agent), eq(ticket.getResolvedAt()));

        verify(eventService).recordStatusChangedEvent(
                ticket,
                agent,
                TicketStatus.IN_PROGRESS,
                TicketStatus.RESOLVED
        );
    }

    @Test
    void shouldResolveWaitingCustomerTicketByResumingThenPausingResolutionSla() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();

        Instant firstRespondedAt = Instant.parse("2026-05-20T09:00:00Z");

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.WAITING_CUSTOMER)
                .assignedTo(agent)
                .firstRespondedAt(firstRespondedAt)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.resolve(ticket.getId(), agent.getId());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();
        assertThat(ticket.getFirstRespondedAt()).isEqualTo(firstRespondedAt);

        InOrder inOrder = inOrder(slaService);

        inOrder.verify(slaService).resumeResolutionSlaClock(eq(ticket), eq(ticket.getResolvedAt()));
        inOrder.verify(slaService).pauseResolutionSlaClock(eq(ticket), eq(agent), eq(ticket.getResolvedAt()));

        verify(slaService, never()).evaluateFirstResponse(any(), any());

        verify(eventService).recordStatusChangedEvent(
                ticket,
                agent,
                TicketStatus.WAITING_CUSTOMER,
                TicketStatus.RESOLVED
        );
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"NEW", "CLOSED"}, mode = EnumSource.Mode.INCLUDE)
    void shouldThrowExceptionIfTryingToDoInvalidTransitionWhenChangingTicketStatusToResolved(TicketStatus currentStatus) {
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(currentStatus)
                .build();

        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> ticketService.resolve(ticket.getId(), admin.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage(new InvalidStatusTransitionException(currentStatus, TicketStatus.RESOLVED).getMessage());
        assertThat(ticket.getStatus()).isEqualTo(currentStatus);
        assertThat(ticket.getResolvedAt()).isNull();

        verify(userRepository).findById(ADMIN_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService);
    }

    @Test
    void shouldNotRecordEventWhenTryingToResolveTicketThatIsAlreadyResolved() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.RESOLVED)
                .assignedTo(agent)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.resolve(ticket.getId(), agent.getId());

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService);
    }

    @Test
    void shouldCloseTicketByCustomerSuccessfully() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.RESOLVED)
                .createdBy(customer)
                .build();

        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.closeByCustomer(ticket.getId(), customer.getId());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);

        verify(userRepository).findById(CUSTOMER_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verify(eventService).recordStatusChangedEvent(
                ticket,
                customer,
                TicketStatus.RESOLVED,
                TicketStatus.CLOSED
        );
        verify(slaService).finalizeResolutionSlaOnClose(ticket);
    }

    @Test
    void shouldThrowExceptionIfCustomerTryToCloseForeignTicket() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .createdBy(UserEntity.builder()
                        .id(99)
                        .role(Role.CUSTOMER)
                        .build())
                .build();

        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> ticketService.closeByCustomer(ticket.getId(), customer.getId()))
                .isInstanceOf(TicketFlowAccessDeniedException.class)
                .hasMessage("Only the ticket creator can close tickets");

        verify(userRepository).findById(CUSTOMER_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"RESOLVED", "CLOSED"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldThrowExceptionIfTryingToDoInvalidTransitionWhenChangingTicketStatusToClosed(TicketStatus currentStatus) {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(currentStatus)
                .createdBy(customer)
                .build();

        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> ticketService.closeByCustomer(ticket.getId(), customer.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage(new InvalidStatusTransitionException(currentStatus, TicketStatus.CLOSED).getMessage());
        assertThat(ticket.getStatus()).isEqualTo(currentStatus);

        verify(userRepository).findById(CUSTOMER_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService);
    }

    @Test
    void shouldNotRecordEventWhenTryingToCloseTicketThatIsAlreadyClosed() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.CLOSED)
                .createdBy(customer)
                .build();

        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.closeByCustomer(ticket.getId(), customer.getId());

        verify(userRepository).findById(CUSTOMER_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(slaService, eventService);
    }

    @Test
    void shouldCloseResolvedTicketsOlderThanThreshold() {
        Instant threshold = Instant.parse("2026-05-20T10:00:00Z");

        TicketEntity ticket1 = TicketEntity.builder()
                .id(1L)
                .status(TicketStatus.RESOLVED)
                .build();

        TicketEntity ticket2 = TicketEntity.builder()
                .id(2L)
                .status(TicketStatus.RESOLVED)
                .build();

        doReturn(List.of(ticket1, ticket2))
                .when(ticketRepository).findResolvedTicketsOlderThan(threshold);

        int closedCount = ticketService.closeResolvedTicketsOlderThan(threshold);

        assertThat(closedCount).isEqualTo(2);
        assertThat(ticket1.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket2.getStatus()).isEqualTo(TicketStatus.CLOSED);

        verify(eventService).recordStatusChangedEvent(ticket1, TicketStatus.RESOLVED, TicketStatus.CLOSED);
        verify(eventService).recordStatusChangedEvent(ticket2, TicketStatus.RESOLVED, TicketStatus.CLOSED);

        verify(slaService).finalizeResolutionSlaOnClose(ticket1);
        verify(slaService).finalizeResolutionSlaOnClose(ticket2);
    }

    @Test
    void shouldReturnZeroWhenNoResolvedTicketsEligibleForAutoClose() {
        Instant threshold = Instant.parse("2026-05-20T10:00:00Z");

        doReturn(Collections.emptyList())
                .when(ticketRepository).findResolvedTicketsOlderThan(threshold);

        int closedCount = ticketService.closeResolvedTicketsOlderThan(threshold);

        assertThat(closedCount).isZero();

        verify(ticketRepository).findResolvedTicketsOlderThan(threshold);
        verifyNoInteractions(eventService, slaService);
    }

    @Test
    void shouldThrowExceptionWhenAutoCloseThresholdIsNull() {
        assertThatThrownBy(() -> ticketService.closeResolvedTicketsOlderThan(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Auto-close threshold cannot be null");

        verifyNoInteractions(ticketRepository, eventService, slaService);
    }

    @Test
    void shouldChangeTicketPrioritySuccessfully() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();

        TicketPriority ticketPriority = TicketPriority.MEDIUM;
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .priority(ticketPriority)
                .status(TicketStatus.IN_PROGRESS)
                .assignedTo(agent)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.changePriority(ticket.getId(), agent.getId(), TicketPriority.HIGH);

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verify(eventService).recordPriorityChangedEvent(ticket, agent, ticketPriority, TicketPriority.HIGH);
        verify(slaService).updateDeadlinesAfterPriorityChange(eq(ticket), any(Instant.class));
    }

    @Test
    void shouldNotRecordEventWhenTryingToChangeTicketPriorityToSameValue() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .priority(TicketPriority.MEDIUM)
                .assignedTo(agent)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.changePriority(ticket.getId(), agent.getId(), ticket.getPriority());

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(eventService, slaService);
    }

    @Test
    void shouldThrowExceptionWhenTryingToChangePriorityOfClosedTicket() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.CLOSED)
                .priority(TicketPriority.MEDIUM)
                .assignedTo(agent)
                .build();

        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> ticketService.changePriority(ticket.getId(), agent.getId(), TicketPriority.HIGH))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Closed tickets cannot be modified");

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.MEDIUM);

        verifyNoInteractions(accessPolicy, eventService, slaService);
    }
}