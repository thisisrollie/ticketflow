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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommentService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketEventService eventService;
    private final CommentResponseMapper commentMapper;

    public List<CommentResponse> findAllBy(Long ticketId) {
        return commentRepository.findAllByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(commentMapper::map)
                .toList();
    }

    @Transactional
    public CommentResponse create(Long ticketId, Integer authorId, String text) {
        TicketEntity ticket = getTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleViolationException("Closed tickets cannot be modified");
        }

        UserEntity author = getUser(authorId);
        if (author.getRole() == Role.CUSTOMER && !ticket.getCreatedBy().getId().equals(author.getId())) {
            throw new AccessDeniedException("Customers cannot add comments to tickets they did not create");
        }

        TicketCommentEntity comment = TicketCommentEntity.builder()
                .ticket(ticket)
                .author(author)
                .body(text)
                .build();
        TicketCommentEntity savedComment = commentRepository.save(comment);

        eventService.recordCommentedEvent(ticket, author, savedComment.getId());

        if (author.getRole() == Role.CUSTOMER) {
            if (ticket.getStatus() == TicketStatus.WAITING_CUSTOMER || ticket.getStatus() == TicketStatus.RESOLVED) {
                TicketStatus currentStatus = ticket.getStatus();
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
            throw new BusinessRuleViolationException("Comment does not belong to the given ticket");
        }

        TicketEntity ticket = comment.getTicket();
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleViolationException("Closed tickets cannot be modified");
        }

        UserEntity actor = getUser(actorId);
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isAuthor = comment.getAuthor().getId().equals(actor.getId());

        if (!isAdmin && !isAuthor) {
            throw new AccessDeniedException("Only admins or the comment author can delete a comment");
        }

        commentRepository.delete(comment);
        commentRepository.flush();

        eventService.recordCommentDeletedEvent(ticket, actor, commentId);
    }

    private TicketEntity getTicket(Long ticketId) {
        return Optional.of(ticketId)
                .flatMap(ticketRepository::findById)
                .orElseThrow(() -> ResourceNotFoundException.ticket(ticketId));
    }

    private UserEntity getUser(Integer userId) {
        return Optional.of(userId)
                .flatMap(userRepository::findById)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
    }
}
