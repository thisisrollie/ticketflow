package com.rolliedev.ticketflow.unit.service;

import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidRequestException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.exception.TicketFlowAccessDeniedException;
import com.rolliedev.ticketflow.mapper.CommentResponseMapper;
import com.rolliedev.ticketflow.policy.AccessPolicy;
import com.rolliedev.ticketflow.repository.TicketCommentRepository;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.service.CommentService;
import com.rolliedev.ticketflow.service.TicketEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final Long TICKET_ID = 1L;
    private static final Integer AGENT_ID = 1;
    private static final Integer CUSTOMER_ID = 2;
    private static final Long COMMENT_ID = 10L;
    private static final String COMMENT_TEXT = "test comment";

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketCommentRepository commentRepository;
    @Mock
    private TicketEventService eventService;
    @Mock
    private CommentResponseMapper commentMapper;
    @Mock
    private AccessPolicy accessPolicy;
    @InjectMocks
    private CommentService commentService;

    @Test
    void shouldReturnMappedPageOfCommentsWhenTicketHasComments() {
        PageRequest pageable = PageRequest.of(0, 10);
        TicketCommentEntity comment1 = TicketCommentEntity.builder().id(1L).build();
        TicketCommentEntity comment2 = TicketCommentEntity.builder().id(2L).build();
        CommentResponse response1 = mock(CommentResponse.class);
        CommentResponse response2 = mock(CommentResponse.class);

        doReturn(new PageImpl<>(List.of(comment1, comment2), pageable, 2))
                .when(commentRepository).findAllByTicketIdOrderByCreatedAtAsc(TICKET_ID, pageable);
        doReturn(response1).when(commentMapper).map(comment1);
        doReturn(response2).when(commentMapper).map(comment2);

        Page<CommentResponse> actualResult = commentService.findAllBy(TICKET_ID, pageable);

        assertThat(actualResult.getContent()).containsExactly(response1, response2);
        verify(commentRepository).findAllByTicketIdOrderByCreatedAtAsc(TICKET_ID, pageable);
        verify(commentMapper, times(2)).map(any(TicketCommentEntity.class));
    }

    @Test
    void shouldReturnEmptyPageWhenTicketHasNoComments() {
        PageRequest pageable = PageRequest.of(0, 10);

        doReturn(new PageImpl<>(Collections.emptyList(), pageable, 0))
                .when(commentRepository).findAllByTicketIdOrderByCreatedAtAsc(TICKET_ID, pageable);

        Page<CommentResponse> actualResult = commentService.findAllBy(TICKET_ID, pageable);

        assertThat(actualResult.getContent()).isEmpty();
        assertThat(actualResult.getTotalElements()).isZero();
        verify(commentRepository).findAllByTicketIdOrderByCreatedAtAsc(TICKET_ID, pageable);
        verifyNoInteractions(commentMapper);
    }

    @Test
    void shouldCreateCommentAndRecordEventSuccessfully() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .build();
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .build();
        CommentResponse commentResponse = mock(CommentResponse.class);

        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        ArgumentCaptor<TicketCommentEntity> captor = ArgumentCaptor.forClass(TicketCommentEntity.class);
        doReturn(comment).when(commentRepository).save(captor.capture());
        doReturn(commentResponse).when(commentMapper).map(comment);

        CommentResponse actualResult = commentService.create(TICKET_ID, AGENT_ID, COMMENT_TEXT);

        assertThat(actualResult).isEqualTo(commentResponse);
        assertThat(captor.getValue().getTicket()).isEqualTo(ticket);
        assertThat(captor.getValue().getAuthor()).isEqualTo(agent);
        assertThat(captor.getValue().getBody()).isEqualTo(COMMENT_TEXT);
        verify(commentRepository).save(any(TicketCommentEntity.class));
        verify(eventService).recordCommentedEvent(ticket, agent, comment.getId());
    }

    @Test
    void shouldThrowBusinessRuleViolationExceptionWhenTryingToAddCommentToClosedTicket() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.CLOSED)
                .build();

        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> commentService.create(TICKET_ID, AGENT_ID, COMMENT_TEXT))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Closed tickets cannot be modified");

        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(userRepository, accessPolicy, commentRepository, eventService, commentMapper);
    }

    @Test
    void shouldThrowExceptionWhenTryingToAddCommentToTicketWithInvalidId() {
        doReturn(Optional.empty()).when(ticketRepository).findById(TICKET_ID);

        assertThatThrownBy(() -> commentService.create(TICKET_ID, AGENT_ID, COMMENT_TEXT))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ResourceNotFoundException.ticket(TICKET_ID).getMessage());

        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(userRepository, accessPolicy, commentRepository, eventService, commentMapper);
    }

    @Test
    void shouldThrowExceptionWhenCustomerTryToAddCommentToForeignTicket() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .createdBy(UserEntity.builder().id(99).role(Role.CUSTOMER).build())
                .build();
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();

        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);
        doThrow(new TicketFlowAccessDeniedException("Customers cannot add comments to tickets they did not create"))
                .when(accessPolicy).requireTicketOwnerIfCustomer(customer, ticket, "Customers cannot add comments to tickets they did not create");

        assertThatThrownBy(() -> commentService.create(TICKET_ID, CUSTOMER_ID, COMMENT_TEXT))
                .isInstanceOf(TicketFlowAccessDeniedException.class)
                .hasMessage("Customers cannot add comments to tickets they did not create");

        verify(ticketRepository).findById(TICKET_ID);
        verify(userRepository).findById(CUSTOMER_ID);
        verifyNoInteractions(commentRepository, eventService, commentMapper);
    }

    @Test
    @DisplayName("When customer adds comment on ticket with WAITING_CUSTOMER status, it should move ticket back to IN_PROGRESS state and record status change event")
    void shouldChangeStatusToInProgressAndRecordStatusChangeEventWhenCustomerAddCommentToTicketWithWaitingCustomerStatus() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.WAITING_CUSTOMER)
                .createdBy(customer)
                .build();
        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .build();

        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);
        doReturn(comment).when(commentRepository).save(any(TicketCommentEntity.class));

        commentService.create(ticket.getId(), customer.getId(), COMMENT_TEXT);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        verify(ticketRepository).findById(TICKET_ID);
        verify(userRepository).findById(CUSTOMER_ID);
        verify(commentRepository).save(any(TicketCommentEntity.class));
        verify(eventService).recordCommentedEvent(ticket, customer, comment.getId());
        verify(eventService).recordStatusChangedEvent(ticket, customer, TicketStatus.WAITING_CUSTOMER, TicketStatus.IN_PROGRESS);
        verify(commentMapper).map(comment);
    }

    @Test
    @DisplayName("When customer adds comment on ticket with RESOLVED status, it should move ticket back to IN_PROGRESS state and record status change event")
    void shouldChangeStatusToInProgressAndRecordStatusChangeEventWhenCustomerAddCommentToTicketWithResolvedStatus() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.RESOLVED)
                .createdBy(customer)
                .resolvedAt(Instant.now())
                .build();
        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .build();

        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);
        doReturn(comment).when(commentRepository).save(any(TicketCommentEntity.class));

        commentService.create(ticket.getId(), customer.getId(), COMMENT_TEXT);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getResolvedAt()).isNull();

        verify(ticketRepository).findById(TICKET_ID);
        verify(userRepository).findById(CUSTOMER_ID);
        verify(commentRepository).save(any(TicketCommentEntity.class));
        verify(eventService).recordCommentedEvent(ticket, customer, comment.getId());
        verify(eventService).recordStatusChangedEvent(ticket, customer, TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS);
        verify(commentMapper).map(comment);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"NEW", "IN_PROGRESS"}, mode = EnumSource.Mode.INCLUDE)
    void shouldAddCommentAndRecordCommentEventOnlyWhenCustomerCommentOnTicketWithGivenStatuses(TicketStatus ticketStatus) {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(ticketStatus)
                .createdBy(customer)
                .build();
        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .build();

        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);
        doReturn(comment).when(commentRepository).save(any(TicketCommentEntity.class));

        commentService.create(ticket.getId(), customer.getId(), COMMENT_TEXT);

        verify(ticketRepository).findById(TICKET_ID);
        verify(userRepository).findById(CUSTOMER_ID);
        verify(commentRepository).save(any(TicketCommentEntity.class));
        verify(eventService).recordCommentedEvent(ticket, customer, comment.getId());
        verify(commentMapper).map(comment);
        verifyNoMoreInteractions(eventService);
    }

    @Test
    void shouldDeleteCommentAndRecordDeleteCommentEventSuccessfully() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .createdBy(customer)
                .build();
        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .ticket(ticket)
                .author(customer)
                .build();

        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);
        doReturn(Optional.of(comment)).when(commentRepository).findWithTicketById(COMMENT_ID);

        commentService.delete(ticket.getId(), comment.getId(), customer.getId());

        verify(commentRepository).findWithTicketById(COMMENT_ID);
        verify(userRepository).findById(CUSTOMER_ID);
        verify(commentRepository).delete(comment);
        verify(eventService).recordCommentDeletedEvent(ticket, customer, comment.getId());
    }

    @Test
    void shouldThrowExceptionIfCommentWithGivenIdDoesNotExist() {
        doReturn(Optional.empty()).when(commentRepository).findWithTicketById(COMMENT_ID);

        assertThatThrownBy(() -> commentService.delete(TICKET_ID, COMMENT_ID, CUSTOMER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ResourceNotFoundException.comment(COMMENT_ID).getMessage());

        verify(commentRepository).findWithTicketById(COMMENT_ID);
        verifyNoMoreInteractions(commentRepository);
        verifyNoInteractions(userRepository, eventService);
    }

    @Test
    void shouldThrowExceptionIfCommentDoesNotBelongToGivenTicket() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .build();
        TicketEntity anotherTicket = TicketEntity.builder()
                .id(99L)
                .build();
        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .ticket(ticket)
                .build();

        doReturn(Optional.of(comment)).when(commentRepository).findWithTicketById(COMMENT_ID);

        assertThatThrownBy(() -> commentService.delete(anotherTicket.getId(), COMMENT_ID, CUSTOMER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Comment does not belong to the given ticket");

        verify(commentRepository).findWithTicketById(COMMENT_ID);
        verifyNoMoreInteractions(commentRepository);
        verifyNoInteractions(userRepository, eventService);
    }

    @Test
    void shouldThrowExceptionWhenTryingToDeleteCommentOfClosedTicket() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.CLOSED)
                .build();
        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .ticket(ticket)
                .build();

        doReturn(Optional.of(comment)).when(commentRepository).findWithTicketById(COMMENT_ID);

        assertThatThrownBy(() -> commentService.delete(TICKET_ID, COMMENT_ID, CUSTOMER_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Closed tickets cannot be modified");

        verify(commentRepository).findWithTicketById(COMMENT_ID);
        verifyNoMoreInteractions(commentRepository);
        verifyNoInteractions(userRepository, eventService);
    }

    @Test
    void shouldThrowExceptionWhenUserWhoIsNotAdminNorAuthorOfCommentTryToDeleteComment() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .build();
        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .ticket(ticket)
                .author(UserEntity.builder()
                        .id(CUSTOMER_ID)
                        .build())
                .build();
        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();

        doReturn(Optional.of(comment)).when(commentRepository).findWithTicketById(COMMENT_ID);
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);
        doThrow(new TicketFlowAccessDeniedException("Only admins or the comment author can delete a comment"))
                .when(accessPolicy).requireAdminOrCommentAuthor(agent, comment, "Only admins or the comment author can delete a comment");

        assertThatThrownBy(() -> commentService.delete(TICKET_ID, COMMENT_ID, AGENT_ID))
                .isInstanceOf(TicketFlowAccessDeniedException.class)
                .hasMessage("Only admins or the comment author can delete a comment");

        verify(commentRepository).findWithTicketById(COMMENT_ID);
        verify(userRepository).findById(AGENT_ID);
        verifyNoMoreInteractions(commentRepository);
        verifyNoInteractions(eventService);
    }
}