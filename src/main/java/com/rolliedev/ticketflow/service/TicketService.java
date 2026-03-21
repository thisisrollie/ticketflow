package com.rolliedev.ticketflow.service;

import com.querydsl.core.types.Predicate;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.mapper.TicketResponseMapper;
import com.rolliedev.ticketflow.policy.AccessPolicy;
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
    private final TicketResponseMapper ticketResponseMapper;
    private final AccessPolicy accessPolicy;

    public Page<TicketResponse> findAll(TicketSearchFilter filter, Pageable pageable) {
        Predicate predicate = TicketSearchFilter.buildPredicate(filter);
        return ticketRepository.findAll(predicate, pageable)
                .map(ticketResponseMapper::map);
    }

    public Optional<TicketResponse> findById(Long id) {
        return ticketRepository.findById(id)
                .map(ticketResponseMapper::map);
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest ticketDto) {
        UserEntity creator = getUser(ticketDto.creatorId());
        TicketEntity ticket = TicketEntity.builder()
                .title(ticketDto.title())
                .description(ticketDto.description())
                .status(TicketStatus.NEW)
                .priority(TicketPriority.MEDIUM)
                .createdBy(creator)
                .build();
        TicketEntity saved = ticketRepository.save(ticket);
        eventService.recordCreatedEvent(saved, creator);
        return ticketResponseMapper.map(saved);
    }

    @Transactional
    public TicketResponse assign(Long ticketId, Integer actorId, Integer assigneeId) {
        UserEntity actor = getUser(actorId);
        accessPolicy.requireAgentOrAdmin(actor, "Only agents or admins can assign tickets");

        UserEntity newAssignee = getUser(assigneeId);
        accessPolicy.requireAgentOrAdmin(newAssignee, "Only agents or admins can be assigned to tickets");

        TicketEntity ticket = getTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleViolationException("Closed tickets cannot be assigned");
        }

        UserEntity currentAssignee = ticket.getAssignedTo();
        if (currentAssignee != null && currentAssignee.getId().equals(newAssignee.getId())) {
            return ticketResponseMapper.map(ticket);
        }
        ticket.setAssignedTo(newAssignee);

        eventService.recordAssignedEvent(ticket, actor, currentAssignee, newAssignee);

        return ticketResponseMapper.map(ticket);
    }

    @Transactional
    public TicketResponse startProgress(Long ticketId, Integer actorId) {
        UserEntity actor = getUser(actorId);
        accessPolicy.requireAgentOrAdmin(actor, "Only agents or admins can start progress on tickets");

        TicketEntity ticket = getTicket(ticketId);

        // if the ticket is not assigned to anyone, assign it to the actor who started the progress
        if (ticket.getAssignedTo() == null) {
            ticket.setAssignedTo(actor);
            eventService.recordAssignedEvent(ticket, actor, null, actor);
        }

        accessPolicy.requireTicketAssignee(ticket, actor, "Only the ticket assignee can start progress on the ticket");

        TicketStatus currentStatus = ticket.getStatus();
        if (currentStatus == TicketStatus.IN_PROGRESS) {
            return ticketResponseMapper.map(ticket);
        }

        currentStatus.assertCanTransitionTo(TicketStatus.IN_PROGRESS);

        if (currentStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(null);
        }

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.IN_PROGRESS);

        return ticketResponseMapper.map(ticket);
    }

    @Transactional
    public TicketResponse requestCustomerInfo(Long ticketId, Integer actorId) {
        UserEntity actor = getUser(actorId);
        accessPolicy.requireAgentOrAdmin(actor, "Only agents or admins can request customer info");

        TicketEntity ticket = getTicket(ticketId);
        TicketStatus currentStatus = ticket.getStatus();
        if (currentStatus == TicketStatus.WAITING_CUSTOMER) {
            return ticketResponseMapper.map(ticket);
        }

        currentStatus.assertCanTransitionTo(TicketStatus.WAITING_CUSTOMER);

        ticket.setStatus(TicketStatus.WAITING_CUSTOMER);
        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.WAITING_CUSTOMER);

        return ticketResponseMapper.map(ticket);
    }

    @Transactional
    public TicketResponse resolve(Long ticketId, Integer actorId) {
        UserEntity actor = getUser(actorId);
        accessPolicy.requireAgentOrAdmin(actor, "Only agents or admins can resolve tickets");

        TicketEntity ticket = getTicket(ticketId);
        TicketStatus currentStatus = ticket.getStatus();
        if (currentStatus == TicketStatus.RESOLVED) {
            return ticketResponseMapper.map(ticket);
        }

        currentStatus.assertCanTransitionTo(TicketStatus.RESOLVED);

        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(Instant.now());
        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.RESOLVED);

        return ticketResponseMapper.map(ticket);
    }

    @Transactional
    public TicketResponse closeByCustomer(Long ticketId, Integer actorId) {
        UserEntity actor = getUser(actorId);
        accessPolicy.requireCustomer(actor, "Only customers can manually close tickets");

        TicketEntity ticket = getTicket(ticketId);
        accessPolicy.requireTicketOwner(ticket, actor, "Only the ticket creator can close tickets");

        TicketStatus currentStatus = ticket.getStatus();
        if (currentStatus == TicketStatus.CLOSED) {
            return ticketResponseMapper.map(ticket);
        }
        currentStatus.assertCanTransitionTo(TicketStatus.CLOSED);

        ticket.setStatus(TicketStatus.CLOSED);
        eventService.recordStatusChangedEvent(ticket, actor, currentStatus, TicketStatus.CLOSED);

        return ticketResponseMapper.map(ticket);
    }

    @Transactional
    public TicketResponse changePriority(Long ticketId, Integer actorId, TicketPriority newPriority) {
        UserEntity actor = getUser(actorId);
        accessPolicy.requireAgentOrAdmin(actor, "Only agents or admins can change ticket priority");

        TicketEntity ticket = getTicket(ticketId);
        TicketPriority currentPriority = ticket.getPriority();
        if (currentPriority == newPriority) {
            return ticketResponseMapper.map(ticket);
        }

        ticket.setPriority(newPriority);
        eventService.recordPriorityChangedEvent(ticket, actor, currentPriority, newPriority);

        return ticketResponseMapper.map(ticket);
    }

    private UserEntity getUser(Integer userId) {
        return Optional.of(userId)
                .flatMap(userRepository::findById)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
    }

    private TicketEntity getTicket(Long ticketId) {
        return Optional.of(ticketId)
                .flatMap(ticketRepository::findById)
                .orElseThrow(() -> ResourceNotFoundException.ticket(ticketId));
    }
}
