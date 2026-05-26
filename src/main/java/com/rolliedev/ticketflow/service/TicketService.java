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
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidRequestException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.mapper.TicketResponseMapper;
import com.rolliedev.ticketflow.policy.AccessPolicy;
import com.rolliedev.ticketflow.querydsl.TicketPredicateBuilder;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TicketService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketEventService eventService;
    private final TicketResponseMapper ticketResponseMapper;
    private final TicketPredicateBuilder ticketPredicateBuilder;
    private final AccessPolicy accessPolicy;
    private final SlaService slaService;

    public Page<TicketResponse> findAll(TicketSearchFilter filter, Pageable pageable, TicketFlowUserDetails actor) {
        Predicate predicate = ticketPredicateBuilder.buildPredicate(filter, actor);
        return ticketRepository.findAll(predicate, pageable)
                .map(ticketResponseMapper::map);
    }

    public Optional<TicketResponse> findById(Long id, TicketFlowUserDetails actor) {
        Optional<TicketEntity> maybeTicket = ticketRepository.findById(id);
        if (actor.hasAuthority(Role.CUSTOMER)) {
            maybeTicket = maybeTicket.filter(t -> t.getCreatedBy().getId().equals(actor.getId()));
        }
        return maybeTicket.map(ticketResponseMapper::map);
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest ticketDto, Integer creatorId) {
        UserEntity creator = getUser(creatorId);
        TicketEntity ticket = TicketEntity.builder()
                .title(ticketDto.title())
                .description(ticketDto.description())
                .status(TicketStatus.NEW)
                .priority(TicketPriority.MEDIUM)
                .createdBy(creator)
                .build();
        TicketEntity saved = ticketRepository.save(ticket);
        slaService.initializeSlaForNewTicket(saved);
        eventService.recordCreatedEvent(saved, creator);
        return ticketResponseMapper.map(saved);
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMIN', 'AGENT')")
    public TicketResponse assign(Long ticketId, Integer actorId, Integer assigneeId) {
        UserEntity actor = getUser(actorId);

        UserEntity newAssignee = getUser(assigneeId);
        if (newAssignee.getRole() != Role.ADMIN && newAssignee.getRole() != Role.AGENT) {
            throw new InvalidRequestException("Only agents or admins can be assigned to tickets");
        }

        TicketEntity ticket = getTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleViolationException("Closed tickets cannot be assigned");
        }

        UserEntity currentAssignee = ticket.getAssignedTo();
        if (currentAssignee != null) {
            accessPolicy.requireTicketAssigneeOrAdmin(ticket, actor, "Only the ticket assignee or admin can reassign tickets");
        }
        if (currentAssignee != null && currentAssignee.getId().equals(newAssignee.getId())) {
            return ticketResponseMapper.map(ticket);
        }

        ticket.setAssignedTo(newAssignee);

        eventService.recordAssignedEvent(ticket, actor, currentAssignee, newAssignee);

        return ticketResponseMapper.map(ticket);
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMIN', 'AGENT')")
    public TicketResponse startProgress(Long ticketId, Integer actorId) {
        UserEntity actor = getUser(actorId);

        TicketEntity ticket = getTicket(ticketId);
        // if the ticket is not assigned to anyone, assign it to the actor who started the progress
        if (ticket.getAssignedTo() == null) {
            ticket.setAssignedTo(actor);
            eventService.recordAssignedEvent(ticket, actor, null, actor);
        }

        accessPolicy.requireTicketAssigneeOrAdmin(ticket, actor, "Only the ticket assignee or admin can start progress on the ticket");

        TicketStatus currentStatus = ticket.getStatus();
        if (currentStatus == TicketStatus.IN_PROGRESS) {
            return ticketResponseMapper.map(ticket);
        }

        currentStatus.assertCanTransitionTo(TicketStatus.IN_PROGRESS);

        Instant transitionedAt = Instant.now();

        if (currentStatus == TicketStatus.WAITING_CUSTOMER) {
            slaService.resumeResolutionSlaClock(ticket, transitionedAt);
        }

        if (currentStatus == TicketStatus.RESOLVED) {
            slaService.handleResolvedTicketReopenedByInternalUser(ticket, actor, transitionedAt);
            ticket.setResolvedAt(null);
        }

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.IN_PROGRESS);

        return ticketResponseMapper.map(ticket);
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMIN', 'AGENT')")
    public TicketResponse requestCustomerInfo(Long ticketId, Integer actorId) {
        UserEntity actor = getUser(actorId);

        TicketEntity ticket = getTicket(ticketId);
        accessPolicy.requireTicketAssigneeOrAdmin(ticket, actor, "Only the ticket assignee or admin can request customer info");

        TicketStatus currentStatus = ticket.getStatus();
        if (currentStatus == TicketStatus.WAITING_CUSTOMER) {
            return ticketResponseMapper.map(ticket);
        }

        currentStatus.assertCanTransitionTo(TicketStatus.WAITING_CUSTOMER);

        Instant requestedAt = Instant.now();

        handleFirstResponseIfNeeded(ticket, actor, requestedAt);

        slaService.pauseResolutionSlaClock(ticket, actor, requestedAt);

        ticket.setStatus(TicketStatus.WAITING_CUSTOMER);
        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.WAITING_CUSTOMER);

        return ticketResponseMapper.map(ticket);
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMIN', 'AGENT')")
    public TicketResponse resolve(Long ticketId, Integer actorId) {
        UserEntity actor = getUser(actorId);

        TicketEntity ticket = getTicket(ticketId);
        accessPolicy.requireTicketAssigneeOrAdmin(ticket, actor, "Only the ticket assignee or admin can resolve tickets");

        TicketStatus currentStatus = ticket.getStatus();
        if (currentStatus == TicketStatus.RESOLVED) {
            return ticketResponseMapper.map(ticket);
        }

        currentStatus.assertCanTransitionTo(TicketStatus.RESOLVED);

        Instant resolvedAt = Instant.now();

        handleFirstResponseIfNeeded(ticket, actor, resolvedAt);

        if (currentStatus == TicketStatus.WAITING_CUSTOMER) {
            slaService.resumeResolutionSlaClock(ticket, resolvedAt);
        }

        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(resolvedAt);

        slaService.pauseResolutionSlaClock(ticket, actor, resolvedAt);

        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.RESOLVED);

        return ticketResponseMapper.map(ticket);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public TicketResponse closeByCustomer(Long ticketId, Integer actorId) {
        UserEntity actor = getUser(actorId);

        TicketEntity ticket = getTicket(ticketId);
        accessPolicy.requireTicketOwner(ticket, actor, "Only the ticket creator can close tickets");

        TicketStatus currentStatus = ticket.getStatus();
        if (currentStatus == TicketStatus.CLOSED) {
            return ticketResponseMapper.map(ticket);
        }
        currentStatus.assertCanTransitionTo(TicketStatus.CLOSED);

        ticket.setStatus(TicketStatus.CLOSED);
        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.CLOSED);

        slaService.finalizeResolutionSlaOnClose(ticket);

        return ticketResponseMapper.map(ticket);
    }

    @Transactional
    public int closeResolvedTicketsOlderThan(Instant threshold) {
        if (threshold == null) {
            throw new IllegalArgumentException("Auto-close threshold cannot be null");
        }

        List<TicketEntity> ticketsToClose = ticketRepository.findResolvedTicketsOlderThan(threshold);

        ticketsToClose.forEach(ticket -> {
            ticket.setStatus(TicketStatus.CLOSED);
            eventService.recordStatusChangedEvent(ticket, TicketStatus.RESOLVED, TicketStatus.CLOSED);

            slaService.finalizeResolutionSlaOnClose(ticket);
        });

        return ticketsToClose.size();
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMIN', 'AGENT')")
    public TicketResponse changePriority(Long ticketId, Integer actorId, TicketPriority newPriority) {
        UserEntity actor = getUser(actorId);

        TicketEntity ticket = getTicket(ticketId);

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleViolationException("Closed tickets cannot be modified");
        }

        accessPolicy.requireTicketAssigneeOrAdmin(ticket, actor, "Only the ticket assignee or admin can change ticket priority");

        TicketPriority oldPriority = ticket.getPriority();
        if (oldPriority == newPriority) {
            return ticketResponseMapper.map(ticket);
        }

        Instant changedAt = Instant.now();
        ticket.setPriority(newPriority);
        slaService.updateDeadlinesAfterPriorityChange(ticket, changedAt);

        eventService.recordPriorityChangedEvent(ticket, actor, oldPriority, newPriority);

        return ticketResponseMapper.map(ticket);
    }

    private UserEntity getUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
    }

    private TicketEntity getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> ResourceNotFoundException.ticket(ticketId));
    }

    private void handleFirstResponseIfNeeded(TicketEntity ticket, UserEntity actor, Instant respondedAt) {
        if (ticket.getFirstRespondedAt() == null) {
            ticket.setFirstRespondedAt(respondedAt);
            slaService.evaluateFirstResponse(ticket, actor);
        }
    }
}
