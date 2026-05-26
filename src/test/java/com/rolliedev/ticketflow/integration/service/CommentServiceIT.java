package com.rolliedev.ticketflow.integration.service;

import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.SlaStatus;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentServiceIT extends AbstractSpringBootIT {

    @Autowired
    private CommentService commentService;
    @Autowired
    private TicketCommentRepository commentRepository;

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

        comment1 = commentRepository.findById(1L).orElseThrow();
    }

    @Test
    void shouldReturnMappedPageOfCommentsOnGivenTicket() {
        Page<CommentResponse> actualResult = commentService.findAllBy(ticket1.getId(), PageRequest.of(0, 10));

        assertThat(actualResult.getTotalElements()).isEqualTo(3);
        assertThat(actualResult.getContent())
                .extracting(CommentResponse::id)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void shouldReturnEmptyPageWhenTicketHasNoComments() {
        Page<CommentResponse> actualResult = commentService.findAllBy(ticket2.getId(), PageRequest.of(0, 10));

        assertThat(actualResult.getTotalElements()).isZero();
    }

    @Test
    void shouldCreateCommentRecordEventAndNotAffectSlaWhenCustomerCommentsOnOwnTicket() {
        prepareNewTicketWithActiveSlas(ticket2);

        CommentResponse actualResult = commentService.create(
                ticket2.getId(),
                customer.getId(),
                "I still need help with this billing issue"
        );
        flushAndClear();

        TicketCommentEntity persisted = commentRepository.findById(actualResult.id()).orElseThrow();
        TicketEntity updatedTicket = ticketRepository.findById(ticket2.getId()).orElseThrow();

        assertThat(persisted.getTicket().getId()).isEqualTo(ticket2.getId());
        assertThat(persisted.getAuthor().getId()).isEqualTo(customer.getId());
        assertThat(persisted.getBody()).isEqualTo("I still need help with this billing issue");

        assertThat(updatedTicket.getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(updatedTicket.getFirstRespondedAt()).isNull();
        assertThat(updatedTicket.getResponseSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(updatedTicket.getResolutionSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(updatedTicket.getResolutionSlaPausedAt()).isNull();

        assertThat(hasEvent(ticket2.getId(), TicketEventType.COMMENTED)).isTrue();
    }

    @Test
    void shouldCreateCommentSetFirstResponseAndRecordEventWhenAdminCommentsOnTicket() {
        prepareNewTicketWithActiveSlas(ticket2);

        CommentResponse actualResult = commentService.create(
                ticket2.getId(),
                admin.getId(),
                "I am investigating this issue"
        );
        flushAndClear();

        TicketCommentEntity persisted = commentRepository.findById(actualResult.id()).orElseThrow();
        TicketEntity updatedTicket = ticketRepository.findById(ticket2.getId()).orElseThrow();

        assertThat(persisted.getTicket().getId()).isEqualTo(ticket2.getId());
        assertThat(persisted.getAuthor().getId()).isEqualTo(admin.getId());
        assertThat(persisted.getBody()).isEqualTo("I am investigating this issue");

        assertThat(updatedTicket.getFirstRespondedAt()).isNotNull();
        assertThat(updatedTicket.getResponseSlaStatus()).isEqualTo(SlaStatus.MET);
        assertThat(updatedTicket.getResolutionSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(updatedTicket.getResolutionSlaPausedAt()).isNull();

        assertThat(hasEvent(ticket2.getId(), TicketEventType.COMMENTED)).isTrue();
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
    void shouldChangeTicketStatusToInProgressResumeResolutionSlaAndRecordEventsWhenCustomerCommentsOnWaitingCustomerTicket() {
        Instant originalDeadline = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant pausedAt = Instant.now().minus(2, ChronoUnit.HOURS);

        ticket1.setStatus(TicketStatus.WAITING_CUSTOMER);
        ticket1.setAssignedTo(agent);
        ticket1.setResolvedAt(null);
        ticket1.setFirstRespondedAt(Instant.now().minus(3, ChronoUnit.HOURS));
        ticket1.setFirstResponseDeadline(Instant.now().plus(1, ChronoUnit.DAYS));
        ticket1.setResponseSlaStatus(SlaStatus.MET);
        ticket1.setResolutionDeadline(originalDeadline);
        ticket1.setResolutionSlaStatus(SlaStatus.PAUSED);
        ticket1.setResolutionSlaPausedAt(pausedAt);
        ticketRepository.save(ticket1);
        flushAndClear();

        commentService.create(ticket1.getId(), customer.getId(), "Test comment");
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket1.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(updated.getResolvedAt()).isNull();
        assertThat(updated.getResponseSlaStatus()).isEqualTo(SlaStatus.MET);
        assertThat(updated.getResolutionSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(updated.getResolutionSlaPausedAt()).isNull();
        assertThat(updated.getResolutionDeadline()).isAfter(originalDeadline);

        assertThat(latestEvents(ticket1.getId(), 2))
                .extracting(TicketEventEntity::getEventType)
                .containsExactlyInAnyOrder(TicketEventType.COMMENTED, TicketEventType.STATUS_CHANGED);
    }

    @Test
    void shouldReopenResolvedTicketClearResolvedAtResumeResolutionSlaAndRecordEventsWhenCustomerCommentsOnResolvedTicket() {
        Instant originalDeadline = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant resolvedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        ticket1.setStatus(TicketStatus.RESOLVED);
        ticket1.setAssignedTo(agent);
        ticket1.setResolvedAt(resolvedAt);
        ticket1.setFirstRespondedAt(resolvedAt);
        ticket1.setFirstResponseDeadline(Instant.now().plus(1, ChronoUnit.DAYS));
        ticket1.setResponseSlaStatus(SlaStatus.MET);
        ticket1.setResolutionDeadline(originalDeadline);
        ticket1.setResolutionSlaStatus(SlaStatus.PAUSED);
        ticket1.setResolutionSlaPausedAt(resolvedAt);
        ticketRepository.save(ticket1);
        flushAndClear();

        commentService.create(ticket1.getId(), customer.getId(), "Test comment");
        flushAndClear();

        TicketEntity updated = ticketRepository.findById(ticket1.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(updated.getResolvedAt()).isNull();
        assertThat(updated.getResponseSlaStatus()).isEqualTo(SlaStatus.MET);
        assertThat(updated.getResolutionSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(updated.getResolutionSlaPausedAt()).isNull();
        assertThat(updated.getResolutionDeadline()).isAfter(originalDeadline);

        assertThat(latestEvents(ticket1.getId(), 2))
                .extracting(TicketEventEntity::getEventType)
                .containsExactlyInAnyOrder(TicketEventType.COMMENTED, TicketEventType.STATUS_CHANGED);
    }

    @Test
    void shouldDeleteCommentAndRecordEventWhenActorIsCommentAuthor() {
        commentService.delete(ticket1.getId(), comment1.getId(), agent.getId());
        flushAndClear();

        assertThat(commentRepository.findById(comment1.getId())).isEmpty();
        assertThat(latestEvent(ticket1.getId()).getEventType()).isEqualTo(TicketEventType.COMMENT_DELETED);
    }

    @Test
    void shouldDeleteCommentAndRecordEventWhenActorIsAdmin() {
        commentService.delete(ticket1.getId(), comment1.getId(), admin.getId());
        flushAndClear();

        assertThat(commentRepository.findById(comment1.getId())).isEmpty();
        assertThat(latestEvent(ticket1.getId()).getEventType()).isEqualTo(TicketEventType.COMMENT_DELETED);
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
        assertThatThrownBy(() -> commentService.delete(ticket1.getId(), comment1.getId(), customer.getId()))
                .isInstanceOf(TicketFlowAccessDeniedException.class)
                .hasMessageContaining("Only admins or the comment author can delete a comment");
    }

    @Test
    void shouldThrowBusinessRuleViolationExceptionWhenDeletingCommentFromClosedTicket() {
        ticket1.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(ticket1);
        flushAndClear();

        assertThatThrownBy(() -> commentService.delete(ticket1.getId(), comment1.getId(), agent.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Closed tickets cannot be modified");
    }

    private void prepareNewTicketWithActiveSlas(TicketEntity ticket) {
        Instant now = Instant.now();

        ticket.setStatus(TicketStatus.NEW);
        ticket.setAssignedTo(null);
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

    private TicketEventEntity latestEvent(Long ticketId) {
        return latestEvents(ticketId, 1).getFirst();
    }

    private List<TicketEventEntity> latestEvents(Long ticketId, int count) {
        return eventRepository
                .findAllByTicketId(
                        ticketId,
                        PageRequest.of(0, count, Sort.by(Sort.Direction.DESC, "id"))
                )
                .getContent();
    }
}