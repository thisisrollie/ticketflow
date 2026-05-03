package com.rolliedev.ticketflow.integration.service;

import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.TicketEventType;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidRequestException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.exception.TicketFlowAccessDeniedException;
import com.rolliedev.ticketflow.repository.TicketCommentRepository;
import com.rolliedev.ticketflow.service.CommentService;
import com.rolliedev.ticketflow.testsupport.base.AbstractSpringBootIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentServiceIT extends AbstractSpringBootIT {

    @Autowired
    private CommentService commentService;
    @Autowired
    private TicketCommentRepository commentRepo;

    private UserEntity admin, agent, customer;
    private TicketEntity ticket1, ticket2;
    private TicketCommentEntity comment1;

    @BeforeEach
    void setUp() {
        admin = userRepository.findByEmail("lex.luthor@gmail.com").orElseThrow();
        agent = userRepository.findByEmail("bruce.wayne@gmail.com").orElseThrow();
        customer = userRepository.findByEmail("clark.kent@gmail.com").orElseThrow();

        ticket1 = ticketRepository.findById(1L).orElseThrow();
        ticket2 = ticketRepository.findById(2L).orElseThrow();

        comment1 = commentRepo.findById(1L).orElseThrow();
    }

    @Test
    void shouldReturnMappedPageOfCommentsOnGivenTicket() {
        Page<CommentResponse> actualResult = commentService.findAllBy(ticket1.getId(), PageRequest.of(0, 10));

        assertThat(actualResult.getTotalElements()).isEqualTo(2);
        assertThat(actualResult.getContent())
                .extracting(CommentResponse::id)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void shouldReturnEmptyPageWhenTicketHasNoComments() {
        Page<CommentResponse> actualResult = commentService.findAllBy(ticket2.getId(), PageRequest.of(0, 10));

        assertThat(actualResult.getTotalElements()).isZero();
    }

    @Test
    void shouldCreateCommentAndRecordEventWhenCustomerCommentsOnOwnTicket() {
        CommentResponse actualResult = commentService.create(
                ticket2.getId(),
                customer.getId(),
                "I still need help with this billing issue"
        );
        flushAndClear();

        TicketCommentEntity persisted = commentRepo.findById(actualResult.id()).orElseThrow();

        assertThat(persisted.getTicket().getId()).isEqualTo(ticket2.getId());
        assertThat(persisted.getAuthor().getId()).isEqualTo(customer.getId());
        assertThat(persisted.getBody()).isEqualTo("I still need help with this billing issue");

        boolean commentedEventExists = eventRepository
                .findAllByTicketId(ticket2.getId(), Pageable.unpaged())
                .stream()
                .anyMatch(e -> e.getEventType() == TicketEventType.COMMENTED);

        assertThat(commentedEventExists).isTrue();
    }

    @Test
    void shouldCreateCommentAndRecordEventWhenAdminCommentsOnTicket() {
        CommentResponse actualResult = commentService.create(
                ticket2.getId(),
                admin.getId(),
                "I am investigating this issue");
        flushAndClear();

        TicketCommentEntity persisted = commentRepo.findById(actualResult.id()).orElseThrow();

        assertThat(persisted.getTicket().getId()).isEqualTo(ticket2.getId());
        assertThat(persisted.getAuthor().getId()).isEqualTo(admin.getId());
        assertThat(persisted.getBody()).isEqualTo("I am investigating this issue");

        boolean commentedEventExists = eventRepository.findAllByTicketId(ticket2.getId(), Pageable.unpaged())
                .stream()
                .anyMatch(e -> e.getEventType() == TicketEventType.COMMENTED);

        assertThat(commentedEventExists).isTrue();
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenTicketDoesNotExist() {
        assertThatThrownBy(() -> commentService.create(999L, customer.getId(), "Test comment"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ResourceNotFoundException.ticket(999L).getMessage());
    }

    @Test
    void shouldThrowBusinessRuleViolationExceptionWhenCommentingOnClosedTicket() {
        ticket2.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(ticket2);
        flushAndClear();

        assertThatThrownBy(() -> commentService.create(ticket2.getId(), customer.getId(), "Test comment"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Closed tickets cannot be modified");
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenCustomerCommentsOnAnotherCustomersTicket() {
        TicketEntity anotherCustomerTicket = ticketRepository.findById(4L).orElseThrow();

        assertThatThrownBy(() -> commentService.create(anotherCustomerTicket.getId(), customer.getId(), "Test comment"))
                .isInstanceOf(TicketFlowAccessDeniedException.class)
                .hasMessage("Customers cannot add comments to tickets they did not create");
    }

    @Test
    void shouldChangeTicketStatusToInProgressAndRecordEventWhenCustomerCommentsOnWaitingCustomerTicket() {
        ticket1.setStatus(TicketStatus.WAITING_CUSTOMER);
        ticketRepository.save(ticket1);
        flushAndClear();

        commentService.create(ticket1.getId(), customer.getId(), "Test comment");
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket1.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        Page<TicketEventEntity> lastTwoTicketEvents = eventRepository.findAllByTicketId(ticket1.getId(), PageRequest.of(0, 2, Sort.Direction.DESC, "id"));
        assertThat(lastTwoTicketEvents)
                .extracting(TicketEventEntity::getEventType)
                .containsExactlyInAnyOrder(TicketEventType.COMMENTED, TicketEventType.STATUS_CHANGED);
    }

    @Test
    void shouldReopenResolvedTicketAndClearResolvedAtWhenCustomerCommentsOnResolvedTicket() {
        ticket1.setStatus(TicketStatus.RESOLVED);
        ticket1.setResolvedAt(Instant.now());
        ticketRepository.save(ticket1);
        flushAndClear();

        commentService.create(ticket1.getId(), customer.getId(), "Test comment");
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket1.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(updated.getResolvedAt()).isNull();

        Page<TicketEventEntity> lastTwoTicketEvents = eventRepository.findAllByTicketId(ticket1.getId(), PageRequest.of(0, 2, Sort.Direction.DESC, "id"));
        assertThat(lastTwoTicketEvents)
                .extracting(TicketEventEntity::getEventType)
                .containsExactlyInAnyOrder(TicketEventType.COMMENTED, TicketEventType.STATUS_CHANGED);
    }

    @Test
    void shouldDeleteCommentAndRecordEventWhenActorIsCommentAuthor() {
        commentService.delete(ticket1.getId(), comment1.getId(), customer.getId());
        flushAndClear();

        assertThat(commentRepo.findById(comment1.getId())).isEmpty();

        boolean deletedEventExists = eventRepository
                .findAllByTicketId(ticket1.getId(), PageRequest.of(0, 1, Sort.Direction.DESC, "id"))
                .stream()
                .anyMatch(e -> e.getEventType() == TicketEventType.COMMENT_DELETED);

        assertThat(deletedEventExists).isTrue();
    }

    @Test
    void shouldDeleteCommentAndRecordEventWhenActorIsAdmin() {
        commentService.delete(ticket1.getId(), comment1.getId(), admin.getId());
        flushAndClear();

        assertThat(commentRepo.findById(comment1.getId())).isEmpty();

        boolean deletedEventExists = eventRepository
                .findAllByTicketId(ticket1.getId(), PageRequest.of(0, 1, Sort.Direction.DESC, "id"))
                .stream()
                .anyMatch(e -> e.getEventType() == TicketEventType.COMMENT_DELETED);

        assertThat(deletedEventExists).isTrue();
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenCommentDoesNotExist() {
        assertThatThrownBy(() -> commentService.delete(ticket1.getId(), 999L, admin.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ResourceNotFoundException.comment(999L).getMessage());
    }

    @Test
    void shouldThrowInvalidRequestExceptionWhenCommentDoesNotBelongToGivenTicket() {
        assertThatThrownBy(() -> commentService.delete(ticket2.getId(), comment1.getId(), admin.getId()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Comment does not belong to the given ticket");
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenActorDoesNotExist() {
        assertThatThrownBy(() -> commentService.delete(ticket1.getId(), comment1.getId(), 999))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ResourceNotFoundException.user(999).getMessage());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenActorIsNotAdminOrCommentAuthor() {
        assertThatThrownBy(() -> commentService.delete(ticket1.getId(), comment1.getId(), agent.getId()))
                .isInstanceOf(TicketFlowAccessDeniedException.class)
                .hasMessageContaining("Only admins or the comment author can delete a comment");
    }

    @Test
    void shouldThrowBusinessRuleViolationExceptionWhenDeletingCommentFromClosedTicket() {
        ticket1.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(ticket1);
        flushAndClear();

        assertThatThrownBy(() -> commentService.delete(ticket1.getId(), comment1.getId(), customer.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Closed tickets cannot be modified");
    }
}