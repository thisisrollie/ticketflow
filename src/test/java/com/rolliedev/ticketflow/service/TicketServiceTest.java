package com.rolliedev.ticketflow.service;

import com.querydsl.core.types.Predicate;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.AccessDeniedException;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidStatusTransitionException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.mapper.TicketResponseMapper;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
    private TicketResponseMapper ticketMapper;
    @InjectMocks
    private TicketService ticketService;

    @Test
    void shouldFindTicketSuccessfully() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);
        TicketResponse expectedResponse = new TicketResponse(ticket.getId(), ticket.getTitle(), ticket.getDescription(), ticket.getStatus(), ticket.getPriority(), null, null, null, null, null);
        doReturn(expectedResponse).when(ticketMapper).toDto(ticket);

        Optional<TicketResponse> actualResult = ticketService.findTicket(TICKET_ID);

        assertThat(actualResult).isPresent();
        assertThat(actualResult.get().id()).isEqualTo(expectedResponse.id());

        verify(ticketRepository).findById(TICKET_ID);
        verify(ticketMapper).toDto(ticket);
    }

    @Test
    void shouldReturnEmptyOptionalWhenTicketNotFound() {
        doReturn(Optional.empty()).when(ticketRepository).findById(TICKET_ID);

        Optional<TicketResponse> actualResult = ticketService.findTicket(TICKET_ID);

        assertThat(actualResult).isEmpty();

        verify(ticketRepository).findById(TICKET_ID);
        verify(ticketMapper, never()).toDto(any(TicketEntity.class));
    }

    @Test
    void shouldCallFindAllWhenSearchFilterIsEmpty() {
        TicketSearchFilter searchFilter = TicketSearchFilter.builder().build();
        Pageable pageable = PageRequest.of(0, 10);
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .build();
        doReturn(new PageImpl<>(List.of(ticket))).when(ticketRepository).findAll(pageable);

        ticketService.listTickets(searchFilter, pageable);

        verify(ticketRepository).findAll(pageable);
        verify(ticketMapper).toDto(any(TicketEntity.class));
        verify(ticketRepository, never()).findAll(any(Predicate.class), eq(pageable));
    }

    @Test
    void shouldCallFindAllByPredicateWhenSearchFilterIsNotEmpty() {
        TicketSearchFilter searchFilter = TicketSearchFilter.builder()
                .creatorId(CUSTOMER_ID)
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .build();
        doReturn(new PageImpl<>(List.of(ticket))).when(ticketRepository).findAll(any(Predicate.class), eq(pageable));

        ticketService.listTickets(searchFilter, pageable);

        verify(ticketRepository).findAll(any(Predicate.class), eq(pageable));
        verify(ticketMapper).toDto(ticket);
        verify(ticketRepository, never()).findAll(pageable);
    }

    @Test
    void shouldReturnEmptyListWhenNoTicketsFound() {
        TicketSearchFilter searchFilter = TicketSearchFilter.builder()
                .creatorId(CUSTOMER_ID)
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        doReturn(new PageImpl<TicketEntity>(Collections.emptyList())).when(ticketRepository).findAll(any(Predicate.class), eq(pageable));

        ticketService.listTickets(searchFilter, pageable);

        verify(ticketRepository).findAll(any(Predicate.class), eq(pageable));
        verify(ticketMapper, never()).toDto(any(TicketEntity.class));
    }

    @Test
    void shouldCreateTicketAndRecordTicketEventSuccessfully() {
        CreateTicketRequest createRequest = new CreateTicketRequest(
                "Can't log in",
                "Getting error when logging in with Google",
                CUSTOMER_ID
        );

        UserEntity creator = UserEntity.builder()
                .id(CUSTOMER_ID)
                .build();
        doReturn(Optional.of(creator)).when(userRepository).findById(CUSTOMER_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .build();
        ArgumentCaptor<TicketEntity> argumentCaptor = ArgumentCaptor.forClass(TicketEntity.class);
        doReturn(ticket).when(ticketRepository).save(argumentCaptor.capture());

        ticketService.createTicket(createRequest);

        assertThat(argumentCaptor.getValue().getTitle()).isEqualTo(createRequest.title());
        assertThat(argumentCaptor.getValue().getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(argumentCaptor.getValue().getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(argumentCaptor.getValue().getCreatedBy()).isEqualTo(creator);

        verify(userRepository).findById(CUSTOMER_ID);
        verify(ticketRepository).save(argumentCaptor.capture());
        verify(eventService).recordCreatedEvent(ticket, creator);
        verify(ticketMapper).toDto(ticket);
    }

    @Test
    void shouldNotCreateTicketAndThrowExceptionWhenUserNotFound() {
        CreateTicketRequest createRequest = new CreateTicketRequest(
                "Can't log in",
                "Getting error when logging in with Google",
                CUSTOMER_ID
        );
        doReturn(Optional.empty()).when(userRepository).findById(CUSTOMER_ID);

        ResourceNotFoundException actualException = assertThrows(ResourceNotFoundException.class, () -> ticketService.createTicket(createRequest));

        assertThat(actualException).hasMessage(ResourceNotFoundException.user(CUSTOMER_ID).getMessage());

        verifyNoInteractions(ticketRepository, eventService, ticketMapper);
    }

    @Test
    void shouldAssignTicketSuccessfully() {
        // given
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);

        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.NEW)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        // when
        ticketService.assignTicket(ticket.getId(), admin.getId(), agent.getId());

        // then
        assertThat(ticket.getAssignedTo()).isEqualTo(agent);

        verify(eventService).recordAssignedEvent(ticket, admin, null, agent);
    }

    @Test
    void shouldThrowExceptionWhenUserWhoIsNotAdminNorAgentTryToAssignTicket() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> ticketService.assignTicket(TICKET_ID, customer.getId(), AGENT_ID));

        assertThat(actualException).hasMessage("Only agents or admins can assign tickets");

        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(ticketRepository, eventService);
    }

    @Test
    void shouldThrowExceptionWhenAssigningTicketToUserWhoIsNotAgentNorAdmin() {
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);

        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> ticketService.assignTicket(TICKET_ID, admin.getId(), customer.getId()));

        assertThat(actualException).hasMessage("Only agents or admins can be assigned to tickets");

        verify(userRepository, times(2)).findById(Mockito.anyInt());
        verifyNoInteractions(ticketRepository, eventService);
    }

    @Test
    void shouldThrowExceptionWhenAssigningClosedTicket() {
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);

        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketEntity closedTicket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.CLOSED)
                .build();
        doReturn(Optional.of(closedTicket)).when(ticketRepository).findById(TICKET_ID);

        BusinessRuleViolationException actualException = assertThrows(BusinessRuleViolationException.class, () -> ticketService.assignTicket(closedTicket.getId(), admin.getId(), agent.getId()));

        assertThat(actualException).hasMessage("Closed tickets cannot be assigned");
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
        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);

        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketEntity newTicket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.NEW)
                .assignedTo(agent)
                .build();
        doReturn(Optional.of(newTicket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.assignTicket(newTicket.getId(), admin.getId(), agent.getId());

        verify(userRepository, times(2)).findById(Mockito.anyInt());
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(eventService);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"NEW", "WAITING_CUSTOMER", "RESOLVED"}, mode = EnumSource.Mode.INCLUDE)
    void shouldStartProgressOnTicketWithGivenStatusesSuccessfully(TicketStatus currentStatus) {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(currentStatus)
                .resolvedAt(currentStatus == TicketStatus.RESOLVED ? Instant.now() : null)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.startProgressOnTicket(ticket.getId(), agent.getId());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        if (currentStatus == TicketStatus.RESOLVED) {
            assertThat(ticket.getResolvedAt()).isNull();
        }

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verify(eventService).recordStatusChangedEvent(ticket, agent, currentStatus, TicketStatus.IN_PROGRESS);
    }

    @Test
    void shouldThrowExceptionIfUserWhoIsNotAgentNorAdminTryToStartProgressOnTicket() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> ticketService.startProgressOnTicket(TICKET_ID, customer.getId()));

        assertThat(actualException).hasMessage("Only agents or admins can start progress on tickets");

        verify(userRepository).findById(CUSTOMER_ID);
        verifyNoInteractions(ticketRepository, eventService);
    }

    @Test
    void shouldThrowExceptionIfUserWhoIsNotAssignedToTicketTryToStartProgressOnAssignedTicket() {
        UserEntity admin = UserEntity.builder()
                .id(ADMIN_ID)
                .role(Role.ADMIN)
                .build();
        doReturn(Optional.of(admin)).when(userRepository).findById(ADMIN_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.NEW)
                .assignedTo(UserEntity.builder()
                        .id(AGENT_ID)
                        .build())
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> ticketService.startProgressOnTicket(ticket.getId(), admin.getId()));

        assertThat(actualException).hasMessage("Only the ticket assignee can start progress on the ticket");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);

        verify(userRepository).findById(ADMIN_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(eventService);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"CLOSED", "IN_PROGRESS"}, mode = EnumSource.Mode.INCLUDE)
    void shouldThrowExceptionIfTryingToStartProgressOnTicketThatIsClosedOrAlreadyInProgress(TicketStatus currentStatus) {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(currentStatus)
                .assignedTo(agent)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        InvalidStatusTransitionException actualException = assertThrows(InvalidStatusTransitionException.class, () -> ticketService.startProgressOnTicket(ticket.getId(), agent.getId()));

        String expectedMessage = new InvalidStatusTransitionException(currentStatus, TicketStatus.IN_PROGRESS).getMessage();
        assertThat(actualException).hasMessage(expectedMessage);
        assertThat(ticket.getStatus()).isEqualTo(currentStatus);

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(eventService);
    }

    @Test
    void shouldChangeTicketStatusToWaitingCustomerWhenAgentRequestsCustomerInfo() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketStatus currentTicketStatus = TicketStatus.IN_PROGRESS;
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(currentTicketStatus)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.requestCustomerInfo(ticket.getId(), agent.getId());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.WAITING_CUSTOMER);

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verify(eventService).recordStatusChangedEvent(ticket, agent, currentTicketStatus, TicketStatus.WAITING_CUSTOMER);
    }

    @Test
    void shouldThrowExceptionIfUserWhoIsNotAgentNorAdminTryToRequestCustomerInfo() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> ticketService.requestCustomerInfo(TICKET_ID, customer.getId()));

        assertThat(actualException).hasMessage("Only agents or admins can request customer info");

        verify(userRepository).findById(CUSTOMER_ID);
        verifyNoInteractions(ticketRepository, eventService);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"NEW", "WAITING_CUSTOMER", "RESOLVED", "CLOSED"}, mode = EnumSource.Mode.INCLUDE)
    void shouldThrowExceptionIfTryingToDoInvalidTransitionWhenChangingTicketStatusToWaitingCustomer(TicketStatus currentStatus) {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(currentStatus)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        InvalidStatusTransitionException actualException = assertThrows(InvalidStatusTransitionException.class, () -> ticketService.requestCustomerInfo(ticket.getId(), agent.getId()));

        String expectedMessage = new InvalidStatusTransitionException(currentStatus, TicketStatus.WAITING_CUSTOMER).getMessage();
        assertThat(actualException).hasMessage(expectedMessage);
        assertThat(ticket.getStatus()).isEqualTo(currentStatus);

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(eventService);
    }

    @Test
    void shouldResolveTicketSuccessfully() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.resolveTicket(ticket.getId(), agent.getId());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verify(eventService).recordStatusChangedEvent(ticket, agent, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);
    }

    @Test
    void shouldThrowExceptionIfUserWhoIsNotAgentNorAdminTryToResolveTicket() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> ticketService.resolveTicket(TICKET_ID, customer.getId()));

        assertThat(actualException).hasMessage("Only agents or admins can resolve tickets");

        verify(userRepository).findById(CUSTOMER_ID);
        verifyNoInteractions(ticketRepository, eventService);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"NEW", "RESOLVED", "CLOSED"}, mode = EnumSource.Mode.INCLUDE)
    void shouldThrowExceptionIfTryingToDoInvalidTransitionWhenChangingTicketStatusToResolved(TicketStatus currentStatus) {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(currentStatus)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        InvalidStatusTransitionException actualException = assertThrows(InvalidStatusTransitionException.class, () -> ticketService.resolveTicket(ticket.getId(), agent.getId()));

        String expectedMessage = new InvalidStatusTransitionException(currentStatus, TicketStatus.RESOLVED).getMessage();
        assertThat(actualException).hasMessage(expectedMessage);
        assertThat(ticket.getStatus()).isEqualTo(currentStatus);
        assertThat(ticket.getResolvedAt()).isNull();

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(eventService);
    }

    @Test
    void shouldCloseTicketByCustomerSuccessfully() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.RESOLVED)
                .createdBy(customer)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.closeTicketByCustomer(ticket.getId(), customer.getId());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);

        verify(userRepository).findById(CUSTOMER_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verify(eventService).recordStatusChangedEvent(ticket, customer, TicketStatus.RESOLVED, TicketStatus.CLOSED);
    }

    @Test
    void shouldThrowExceptionIfUserWhoIsNotCustomerTryToManuallyCloseTicket() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> ticketService.closeTicketByCustomer(TICKET_ID, agent.getId()));

        assertThat(actualException).hasMessage("Only customers can manually close tickets");

        verify(userRepository).findById(AGENT_ID);
        verifyNoInteractions(ticketRepository, eventService);
    }

    @Test
    void shouldThrowExceptionIfCustomerTryToCloseForeignTicket() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .createdBy(UserEntity.builder()
                        .id(99)
                        .role(Role.CUSTOMER)
                        .build())
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> ticketService.closeTicketByCustomer(ticket.getId(), customer.getId()));

        assertThat(actualException).hasMessage("Only the ticket creator can close tickets");

        verify(userRepository).findById(CUSTOMER_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(eventService);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"RESOLVED"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldThrowExceptionIfTryingToDoInvalidTransitionWhenChangingTicketStatusToClosed(TicketStatus currentStatus) {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(currentStatus)
                .createdBy(customer)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        InvalidStatusTransitionException actualException = assertThrows(InvalidStatusTransitionException.class, () -> ticketService.closeTicketByCustomer(ticket.getId(), customer.getId()));

        String expectedMessage = new InvalidStatusTransitionException(currentStatus, TicketStatus.CLOSED).getMessage();
        assertThat(actualException).hasMessage(expectedMessage);
        assertThat(ticket.getStatus()).isEqualTo(currentStatus);

        verify(userRepository).findById(CUSTOMER_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(eventService);
    }

    @Test
    void shouldChangeTicketPrioritySuccessfully() {
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketPriority ticketPriority = TicketPriority.MEDIUM;
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .priority(ticketPriority)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        ticketService.changePriority(ticket.getId(), agent.getId(), TicketPriority.HIGH);

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);

        verify(userRepository).findById(AGENT_ID);
        verify(ticketRepository).findById(TICKET_ID);
        verify(eventService).recordPriorityChangedEvent(ticket, agent, ticketPriority, TicketPriority.HIGH);
    }

    @Test
    void shouldThrowExceptionIfUserWhoIsNotAgentNorAdminTryToChangeTicketPriority() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> ticketService.changePriority(TICKET_ID, customer.getId(), TicketPriority.HIGH));

        assertThat(actualException).hasMessage("Only agents or admins can change ticket priority");

        verify(userRepository).findById(CUSTOMER_ID);
        verifyNoInteractions(ticketRepository, eventService);
    }
}