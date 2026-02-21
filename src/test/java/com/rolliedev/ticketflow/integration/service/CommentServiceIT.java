package com.rolliedev.ticketflow.integration.service;

import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.enums.TicketEventType;
import com.rolliedev.ticketflow.exception.AccessDeniedException;
import com.rolliedev.ticketflow.testsupport.base.AbstractSpringBootIT;
import com.rolliedev.ticketflow.repository.TicketCommentRepository;
import com.rolliedev.ticketflow.service.CommentService;
import com.rolliedev.ticketflow.testsupport.util.DataUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommentServiceIT extends AbstractSpringBootIT {

    @Autowired
    private CommentService commentService;
    @Autowired
    private TicketCommentRepository commentRepo;

    @Test
    void shouldPersistCommentAndRecordEventSuccessfully() {
        commentService.addComment(ticket2.getId(), customer.getId(), "This is a comment");

        List<TicketCommentEntity> ticketComments = commentRepo.findAllByTicketId(ticket2.getId());
        assertThat(ticketComments).hasSize(1);
        assertThat(ticketComments.getFirst().getBody()).isEqualTo("This is a comment");

        List<TicketEventEntity> ticketEvents = eventRepo.findAllByTicketId(ticket2.getId(), Sort.by(Sort.Direction.DESC, "id"));
        assertThat(ticketEvents).hasSize(2);
        assertThat(ticketEvents.getFirst().getEventType()).isEqualTo(TicketEventType.COMMENTED);
    }

    @Test
    void shouldBeAbleToDeleteCommentByOwnerSuccessfully() {
        TicketCommentEntity comment = DataUtils.getTransientTicketComment(ticket2, customer, "This is a comment");
        commentRepo.saveAndFlush(comment);

        commentService.deleteComment(ticket2.getId(), comment.getId(), customer.getId());
        flushAndClear();

        Optional<TicketCommentEntity> maybeComment = commentRepo.findById(comment.getId());
        assertThat(maybeComment).isEmpty();
    }

    @Test
    void shouldBeAbleToDeleteCommentByAdminSuccessfully() {
        TicketCommentEntity comment = DataUtils.getTransientTicketComment(ticket2, customer, "This is a comment");
        commentRepo.saveAndFlush(comment);

        commentService.deleteComment(ticket2.getId(), comment.getId(), admin.getId());
        flushAndClear();

        Optional<TicketCommentEntity> maybeComment = commentRepo.findById(comment.getId());
        assertThat(maybeComment).isEmpty();
    }

    @Test
    void shouldThrowExceptionWhenDeletingCommentByNonOwnerOrAdmin() {
        TicketCommentEntity comment = DataUtils.getTransientTicketComment(ticket2, customer, "This is a comment");
        commentRepo.saveAndFlush(comment);

        AccessDeniedException actualException = assertThrows(AccessDeniedException.class, () -> {
            commentService.deleteComment(ticket2.getId(), comment.getId(), agent.getId());
        });

        assertThat(actualException).hasMessage("Only admins or the comment author can delete a comment");
        flushAndClear();

        Optional<TicketCommentEntity> maybeComment = commentRepo.findById(comment.getId());
        assertThat(maybeComment).isPresent();
        assertThat(maybeComment.get().getBody()).isEqualTo("This is a comment");
    }

    @Test
    void shouldReturnAllCommentsOnGivenTicketSuccessfully() {
        List<CommentResponse> actualResult = commentService.getComments(ticket1.getId());

        assertThat(actualResult).hasSize(2);
    }

    @Test
    void shouldReturnEmptyListWhenTicketHasNoComments() {
        List<CommentResponse> actualResult = commentService.getComments(ticket2.getId());

        assertThat(actualResult).isEmpty();
    }
}