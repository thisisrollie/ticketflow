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
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.mapper.TicketResponseMapper;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TicketService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketEventService eventService;
    private final TicketResponseMapper ticketMapper;

    public Optional<TicketResponse> findTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .map(ticketMapper::toDto);
    }

    public Page<TicketResponse> listTickets(TicketSearchFilter ticketFilter, Pageable pageable) {
        Predicate predicate = TicketSearchFilter.buildPredicate(ticketFilter);
        if (predicate != null) {
            return ticketRepository.findAll(predicate, pageable)
                    .map(ticketMapper::toDto);
        }
        return ticketRepository.findAll(pageable)
                .map(ticketMapper::toDto);
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest ticketDto) {
        UserEntity creator = getUserOrThrow(ticketDto.creatorId());
        TicketEntity ticket = TicketEntity.builder()
                .title(ticketDto.title())
                .description(ticketDto.description())
                .status(TicketStatus.NEW)
                .priority(TicketPriority.MEDIUM)
                .createdBy(creator)
                .build();
        TicketEntity savedTicket = ticketRepository.save(ticket);

        eventService.recordCreatedEvent(savedTicket, creator);

        return ticketMapper.toDto(savedTicket);
    }

    @Transactional
    public void assignTicket(Long ticketId, Integer actorId, Integer assigneeId) {
        UserEntity actor = getUserOrThrow(actorId);
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.AGENT) {
            throw new AccessDeniedException("Only agents or admins can assign tickets");
        }

        UserEntity newAssignee = getUserOrThrow(assigneeId);
        if (newAssignee.getRole() != Role.ADMIN && newAssignee.getRole() != Role.AGENT) {
            throw new AccessDeniedException("Only agents or admins can be assigned to tickets");
        }

        TicketEntity ticket = getTicketOrThrow(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleViolationException("Closed tickets cannot be assigned");
        }

        UserEntity currentAssignee = ticket.getAssignedTo();
        if (currentAssignee != null && currentAssignee.getId().equals(newAssignee.getId())) {
            return;
        }
        ticket.setAssignedTo(newAssignee);

        eventService.recordAssignedEvent(ticket, actor, currentAssignee, newAssignee);
    }

    @Transactional
    public void startProgressOnTicket(Long ticketId, Integer actorId) {
        UserEntity actor = getUserOrThrow(actorId);
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.AGENT) {
            throw new AccessDeniedException("Only agents or admins can start progress on tickets");
        }

        TicketEntity ticket = getTicketOrThrow(ticketId);
        TicketStatus currentStatus = ticket.getStatus();

        if (ticket.getAssignedTo() == null) {
            ticket.setAssignedTo(actor);
            eventService.recordAssignedEvent(ticket, actor, null, actor);
        }

        if (!ticket.getAssignedTo().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the ticket assignee can start progress on the ticket");
        }

        currentStatus.assertCanTransitionTo(TicketStatus.IN_PROGRESS);

        if (currentStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(null);
        }
        ticket.setStatus(TicketStatus.IN_PROGRESS);

        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.IN_PROGRESS);
    }

    @Transactional
    public void requestCustomerInfo(Long ticketId, Integer actorId) {
        UserEntity actor = getUserOrThrow(actorId);
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.AGENT) {
            throw new AccessDeniedException("Only agents or admins can request customer info");
        }

        TicketEntity ticket = getTicketOrThrow(ticketId);
        TicketStatus currentStatus = ticket.getStatus();
        currentStatus.assertCanTransitionTo(TicketStatus.WAITING_CUSTOMER);
        ticket.setStatus(TicketStatus.WAITING_CUSTOMER);

        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.WAITING_CUSTOMER);
    }

    @Transactional
    public void resolveTicket(Long ticketId, Integer actorId) {
        UserEntity actor = getUserOrThrow(actorId);
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.AGENT) {
            throw new AccessDeniedException("Only agents or admins can resolve tickets");
        }

        TicketEntity ticket = getTicketOrThrow(ticketId);
        TicketStatus currentStatus = ticket.getStatus();
        currentStatus.assertCanTransitionTo(TicketStatus.RESOLVED);
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(Instant.now());

        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.RESOLVED);
    }

    @Transactional
    public void closeTicketByCustomer(Long ticketId, Integer actorId) {
        UserEntity actor = getUserOrThrow(actorId);
        if (actor.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Only customers can manually close tickets");
        }

        TicketEntity ticket = getTicketOrThrow(ticketId);
        if (!ticket.getCreatedBy().getId().equals(actorId)) {
            throw new AccessDeniedException("Only the ticket creator can close tickets");
        }

        TicketStatus currentStatus = ticket.getStatus();
        currentStatus.assertCanTransitionTo(TicketStatus.CLOSED);
        ticket.setStatus(TicketStatus.CLOSED);

        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.CLOSED);
    }

    @Transactional
    public void changePriority(Long ticketId, Integer actorId, TicketPriority newPriority) {
        UserEntity actor = getUserOrThrow(actorId);
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.AGENT) {
            throw new AccessDeniedException("Only agents or admins can change ticket priority");
        }

        TicketEntity ticket = getTicketOrThrow(ticketId);
        TicketPriority currentPriority = ticket.getPriority();
        ticket.setPriority(newPriority);

        eventService.recordPriorityChangedEvent(ticket, actor, currentPriority, newPriority);
    }

    private UserEntity getUserOrThrow(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
    }

    private TicketEntity getTicketOrThrow(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> ResourceNotFoundException.ticket(ticketId));
    }
}
