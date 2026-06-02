package com.rolliedev.ticketflow.service;

import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidRequestException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.mapper.CommentResponseMapper;
import com.rolliedev.ticketflow.policy.AccessPolicy;
import com.rolliedev.ticketflow.repository.TicketCommentRepository;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.service.sla.SlaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommentService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketEventService eventService;
    private final CommentResponseMapper commentMapper;
    private final AccessPolicy accessPolicy;
    private final SlaService slaService;

    public Page<CommentResponse> findAllBy(Long ticketId, Pageable pageable) {
        return commentRepository.findAllByTicketIdOrderByCreatedAtAsc(ticketId, pageable)
                .map(commentMapper::map);
    }

    @Transactional
    public CommentResponse create(Long ticketId, Integer authorId, String text) {
        TicketEntity ticket = getTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleViolationException("Closed tickets cannot be modified");
        }

        UserEntity author = getUser(authorId);
        accessPolicy.requireTicketOwnerIfCustomer(author, ticket, "Customers cannot add comments to tickets they did not create");

        TicketCommentEntity comment = TicketCommentEntity.builder()
                .ticket(ticket)
                .author(author)
                .body(text)
                .build();
        TicketCommentEntity savedComment = commentRepository.save(comment);

        eventService.recordCommentedEvent(ticket, author, savedComment.getId());

        if (countsAsFirstResponse(ticket, author)) {
            ticket.setFirstRespondedAt(savedComment.getCreatedAt());
            slaService.evaluateFirstResponse(ticket, author);
        }

        if (author.getRole() == Role.CUSTOMER) {
            if (ticket.getStatus() == TicketStatus.WAITING_CUSTOMER || ticket.getStatus() == TicketStatus.RESOLVED) {
                TicketStatus currentStatus = ticket.getStatus();

                currentStatus.assertCanTransitionTo(TicketStatus.IN_PROGRESS);

                slaService.resumeResolutionSlaClock(ticket, savedComment.getCreatedAt());

                if (currentStatus == TicketStatus.RESOLVED) {
                    ticket.setResolvedAt(null);
                }

                ticket.setStatus(TicketStatus.IN_PROGRESS);
                eventService.recordStatusChangedEvent(ticket, author, currentStatus, TicketStatus.IN_PROGRESS);
            }
        }
        return commentMapper.map(savedComment);
    }

    @Transactional
    public void delete(Long ticketId, Long commentId, Integer actorId) {
        TicketCommentEntity comment = commentRepository.findWithTicketById(commentId)
                .orElseThrow(() -> ResourceNotFoundException.comment(commentId));
        if (!comment.getTicket().getId().equals(ticketId)) {
            throw new InvalidRequestException("Comment does not belong to the given ticket");
        }

        TicketEntity ticket = comment.getTicket();
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleViolationException("Closed tickets cannot be modified");
        }

        UserEntity actor = getUser(actorId);
        accessPolicy.requireAdminOrCommentAuthor(actor, comment, "Only admins or the comment author can delete a comment");

        commentRepository.delete(comment);
        eventService.recordCommentDeletedEvent(ticket, actor, commentId);
    }

    private TicketEntity getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> ResourceNotFoundException.ticket(ticketId));
    }

    private UserEntity getUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
    }

    private boolean countsAsFirstResponse(TicketEntity ticket, UserEntity author) {
        return ticket.getFirstRespondedAt() == null &&
               (author.getRole() == Role.ADMIN || author.getRole() == Role.AGENT);
    }
}
