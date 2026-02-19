package com.rolliedev.ticketflow.service;

import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.AccessDeniedException;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.mapper.CommentResponseMapper;
import com.rolliedev.ticketflow.repository.TicketCommentRepository;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final Long TICKET_ID = 1L;
    private static final Integer AGENT_ID = 1;
    private static final Integer CUSTOMER_ID = 2;
    private static final Long COMMENT_ID = 1L;
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
    @InjectMocks
    private CommentService commentService;

    @Test
    void shouldAddCommentAndRecordEventSuccessfully() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .build();
        ArgumentCaptor<TicketCommentEntity> argumentCaptor = ArgumentCaptor.forClass(TicketCommentEntity.class);
        doReturn(comment).when(commentRepository).save(argumentCaptor.capture());

        commentService.addComment(ticket.getId(), agent.getId(), COMMENT_TEXT);

        assertThat(argumentCaptor.getValue().getTicket()).isEqualTo(ticket);
        assertThat(argumentCaptor.getValue().getAuthor()).isEqualTo(agent);
        assertThat(argumentCaptor.getValue().getBody()).isEqualTo(COMMENT_TEXT);

        verify(ticketRepository).findById(TICKET_ID);
        verify(userRepository).findById(AGENT_ID);
        verify(commentRepository).save(argumentCaptor.capture());
        verify(eventService).recordCommentedEvent(ticket, agent, comment.getId());
        verify(commentMapper).toDto(comment);
    }

    @Test
    void shouldThrowExceptionWhenTryingToAddCommentToClosedTicket() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.CLOSED)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        BusinessRuleViolationException actualException = assertThrows(BusinessRuleViolationException.class, () -> commentService.addComment(ticket.getId(), AGENT_ID, COMMENT_TEXT));

        assertThat(actualException).hasMessage("Closed tickets cannot be modified");

        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(userRepository, commentRepository, eventService, commentMapper);
    }

    @Test
    void shouldThrowExceptionWhenTryingToAddCommentToTicketWithInvalidId() {
        doReturn(Optional.empty()).when(ticketRepository).findById(TICKET_ID);

        ResourceNotFoundException actualException = assertThrows(ResourceNotFoundException.class, () -> commentService.addComment(TICKET_ID, AGENT_ID, COMMENT_TEXT));

        assertThat(actualException).hasMessage(ResourceNotFoundException.ticket(TICKET_ID).getMessage());

        verify(ticketRepository).findById(TICKET_ID);
        verifyNoInteractions(userRepository, commentRepository, eventService, commentMapper);
    }

    @Test
    void shouldThrowExceptionWhenCustomerTryToAddCommentToForeignTicket() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.IN_PROGRESS)
                .createdBy(UserEntity.builder()
                        .id(99)
                        .role(Role.CUSTOMER)
                        .build())
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> commentService.addComment(ticket.getId(), customer.getId(), COMMENT_TEXT));

        assertThat(actualException).hasMessage("Customers cannot add comments to tickets they did not create");

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
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.WAITING_CUSTOMER)
                .createdBy(customer)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .build();
        doReturn(comment).when(commentRepository).save(any(TicketCommentEntity.class));

        commentService.addComment(ticket.getId(), customer.getId(), COMMENT_TEXT);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        verify(ticketRepository).findById(TICKET_ID);
        verify(userRepository).findById(CUSTOMER_ID);
        verify(commentRepository).save(any(TicketCommentEntity.class));
        verify(eventService).recordCommentedEvent(ticket, customer, comment.getId());
        verify(eventService).recordStatusChangedEvent(ticket, customer, TicketStatus.WAITING_CUSTOMER, TicketStatus.IN_PROGRESS);
        verify(commentMapper).toDto(comment);
    }

    @Test
    @DisplayName("When customer adds comment on ticket with RESOLVED status, it should move ticket back to IN_PROGRESS state and record status change event")
    void shouldChangeStatusToInProgressAndRecordStatusChangeEventWhenCustomerAddCommentToTicketWithResolvedStatus() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(TicketStatus.RESOLVED)
                .createdBy(customer)
                .resolvedAt(Instant.now())
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .build();
        doReturn(comment).when(commentRepository).save(any(TicketCommentEntity.class));

        commentService.addComment(ticket.getId(), customer.getId(), COMMENT_TEXT);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getResolvedAt()).isNull();

        verify(ticketRepository).findById(TICKET_ID);
        verify(userRepository).findById(CUSTOMER_ID);
        verify(commentRepository).save(any(TicketCommentEntity.class));
        verify(eventService).recordCommentedEvent(ticket, customer, comment.getId());
        verify(eventService).recordStatusChangedEvent(ticket, customer, TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS);
        verify(commentMapper).toDto(comment);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"NEW", "IN_PROGRESS"}, mode = EnumSource.Mode.INCLUDE)
    void shouldAddCommentAndRecordCommentEventOnlyWhenCustomerCommentOnTicketWithGivenStatuses(TicketStatus ticketStatus) {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .status(ticketStatus)
                .createdBy(customer)
                .build();
        doReturn(Optional.of(ticket)).when(ticketRepository).findById(TICKET_ID);

        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .build();
        doReturn(comment).when(commentRepository).save(any(TicketCommentEntity.class));

        commentService.addComment(ticket.getId(), customer.getId(), COMMENT_TEXT);

        verify(ticketRepository).findById(TICKET_ID);
        verify(userRepository).findById(CUSTOMER_ID);
        verify(commentRepository).save(any(TicketCommentEntity.class));
        verify(eventService).recordCommentedEvent(ticket, customer, comment.getId());
        verify(commentMapper).toDto(comment);
        verifyNoMoreInteractions(eventService);
    }

    @Test
    void shouldDeleteCommentAndRecordDeleteCommentEventSuccessfully() {
        UserEntity customer = UserEntity.builder()
                .id(CUSTOMER_ID)
                .role(Role.CUSTOMER)
                .build();
        doReturn(Optional.of(customer)).when(userRepository).findById(CUSTOMER_ID);

        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .createdBy(customer)
                .build();
        TicketCommentEntity comment = TicketCommentEntity.builder()
                .id(COMMENT_ID)
                .ticket(ticket)
                .author(customer)
                .build();
        doReturn(Optional.of(comment)).when(commentRepository).findWithTicketById(COMMENT_ID);

        commentService.deleteComment(ticket.getId(), comment.getId(), customer.getId());

        verify(commentRepository).findWithTicketById(COMMENT_ID);
        verify(userRepository).findById(CUSTOMER_ID);
        verify(commentRepository).delete(comment);
        verify(eventService).recordCommentDeletedEvent(ticket, customer, comment.getId());
    }

    @Test
    void shouldThrowExceptionIfCommentWithGivenIdDoesNotExist() {
        doReturn(Optional.empty()).when(commentRepository).findWithTicketById(COMMENT_ID);

        ResourceNotFoundException actualException = assertThrows(ResourceNotFoundException.class, () -> commentService.deleteComment(TICKET_ID, COMMENT_ID, CUSTOMER_ID));

        assertThat(actualException).hasMessage(ResourceNotFoundException.comment(COMMENT_ID).getMessage());

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

        BusinessRuleViolationException actualException = assertThrows(BusinessRuleViolationException.class, () -> commentService.deleteComment(anotherTicket.getId(), comment.getId(), CUSTOMER_ID));

        assertThat(actualException).hasMessage("Comment does not belong to the given ticket");

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

        BusinessRuleViolationException actualException = assertThrows(BusinessRuleViolationException.class, () -> commentService.deleteComment(ticket.getId(), comment.getId(), CUSTOMER_ID));

        assertThat(actualException).hasMessage("Closed tickets cannot be modified");

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
        doReturn(Optional.of(comment)).when(commentRepository).findWithTicketById(COMMENT_ID);

        UserEntity agent = UserEntity.builder()
                .id(AGENT_ID)
                .role(Role.AGENT)
                .build();
        doReturn(Optional.of(agent)).when(userRepository).findById(AGENT_ID);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> commentService.deleteComment(ticket.getId(), comment.getId(), agent.getId()));

        assertThat(actualException).hasMessage("Only admins or the comment author can delete a comment");

        verify(commentRepository).findWithTicketById(COMMENT_ID);
        verify(userRepository).findById(AGENT_ID);
        verifyNoMoreInteractions(commentRepository);
        verifyNoInteractions(eventService);
    }

    @Test
    void shouldReturnAllCommentsOnGivenTicket() {
        List<TicketCommentEntity> comments = List.of(
                TicketCommentEntity.builder().id(1L).build(),
                TicketCommentEntity.builder().id(2L).build(),
                TicketCommentEntity.builder().id(3L).build()
        );
        doReturn(comments).when(commentRepository).findAllByTicketIdOrderByCreatedAtAsc(TICKET_ID);
        comments.forEach(commentEntity -> {
            CommentResponse dto = new CommentResponse(commentEntity.getId(), null, commentEntity.getBody(), commentEntity.getCreatedAt());
            doReturn(dto).when(commentMapper).toDto(commentEntity);
        });

        List<CommentResponse> actualResult = commentService.getComments(TICKET_ID);

        assertThat(actualResult).hasSize(3);
        assertThat(actualResult).extracting(CommentResponse::id).contains(1L, 2L, 3L);

        verify(commentRepository).findAllByTicketIdOrderByCreatedAtAsc(TICKET_ID);
        verify(commentMapper, times(comments.size())).toDto(any(TicketCommentEntity.class));
    }

    @Test
    void shouldReturnEmptyListWhenTicketHasNoComments() {
        doReturn(Collections.EMPTY_LIST).when(commentRepository).findAllByTicketIdOrderByCreatedAtAsc(TICKET_ID);

        List<CommentResponse> actualResult = commentService.getComments(TICKET_ID);

        assertThat(actualResult).isEmpty();

        verify(commentRepository).findAllByTicketIdOrderByCreatedAtAsc(TICKET_ID);
        verifyNoInteractions(commentMapper);
    }
}