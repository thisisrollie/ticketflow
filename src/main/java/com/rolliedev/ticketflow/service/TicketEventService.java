package com.rolliedev.ticketflow.service;

import com.rolliedev.ticketflow.dto.TicketEventResponse;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.TicketEventType;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.mapper.TicketEventResponseMapper;
import com.rolliedev.ticketflow.repository.TicketEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TicketEventService {

    private final TicketEventRepository eventRepository;
    private final TicketEventResponseMapper eventMapper;

    public List<TicketEventResponse> getTimeline(Long ticketId) {
        return eventRepository.findAllByTicketId(ticketId, Sort.by("createdAt", "id").descending()).stream()
                .map(eventMapper::toDto)
                .toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreatedEvent(TicketEntity ticket, UserEntity actor) {
        saveTicketEvent(ticket, actor, TicketEventType.CREATED, Map.of(
                "ticketId", ticket.getId().toString(),
                "createdById", actor.getId().toString()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAssignedEvent(TicketEntity ticket, UserEntity actor, UserEntity previousAssignee, UserEntity newAssignee) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("previousAssigneeId", previousAssignee == null ? null : previousAssignee.getId().toString());
        payload.put("assigneeId", newAssignee.getId().toString());

        saveTicketEvent(ticket, actor, TicketEventType.ASSIGNED, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPriorityChangedEvent(TicketEntity ticket, UserEntity actor, TicketPriority oldPriority, TicketPriority newPriority) {
        saveTicketEvent(ticket, actor, TicketEventType.PRIORITY_CHANGED, Map.of(
                "oldPriority", oldPriority.name(),
                "newPriority", newPriority.name()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordStatusChangedEvent(TicketEntity ticket, UserEntity actor, TicketStatus oldStatus, TicketStatus newStatus) {
        saveTicketEvent(ticket, actor, TicketEventType.STATUS_CHANGED, Map.of(
                "oldStatus", oldStatus.name(),
                "newStatus", newStatus.name()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCommentedEvent(TicketEntity ticket, UserEntity actor, Long commentId) {
        saveTicketEvent(ticket, actor, TicketEventType.COMMENTED, Map.of(
                "commentId", commentId.toString()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCommentDeletedEvent(TicketEntity ticket, UserEntity actor, Long commentId) {
        saveTicketEvent(ticket, actor, TicketEventType.COMMENT_DELETED, Map.of(
                "commentId", commentId.toString()
        ));
    }

    private void saveTicketEvent(TicketEntity ticket, UserEntity actor, TicketEventType eventType, Map<String, Object> payload) {
        TicketEventEntity ticketEvent = TicketEventEntity.builder()
                .ticket(ticket)
                .actor(actor)
                .eventType(eventType)
                .payload(payload)
                .build();
        eventRepository.save(ticketEvent);
    }
}
