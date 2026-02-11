package com.rolliedev.ticketflow.util;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketEventType;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public final class DataUtils {

    public static UserEntity getTransientUser(String firstName, String lastName, Role role) {
        return UserEntity.builder()
                .fullName(firstName + " " + lastName)
                .email(firstName.toLowerCase() + "." + lastName.toLowerCase() + "@gmail.com")
                .role(role)
                .build();
    }

    /**
     * Returns a basic ticket with status=NEW and priority=MEDIUM and assignedTo=null
     */
    public static TicketEntity getTransientTicket(String title, String description, UserEntity createdBy) {
        return getTransientTicket(title, description, TicketStatus.NEW, TicketPriority.MEDIUM, createdBy, null);
    }

    public static TicketEntity getTransientTicket(String title, String description, TicketStatus status, TicketPriority priority, UserEntity createdBy, UserEntity assignedTo) {
        return TicketEntity.builder()
                .title(title)
                .description(description)
                .status(status)
                .priority(priority)
                .createdBy(createdBy)
                .assignedTo(assignedTo)
                .build();
    }

    public static TicketCommentEntity getTransientTicketComment(TicketEntity ticket, UserEntity author, String body) {
        return TicketCommentEntity.builder()
                .ticket(ticket)
                .author(author)
                .body(body)
                .build();
    }

    public static TicketEventEntity getTransientTicketEvent(TicketEntity ticket, UserEntity actor, TicketEventType eventType, Map<String, Object> payload) {
        return TicketEventEntity.builder()
                .ticket(ticket)
                .actor(actor)
                .eventType(eventType)
                .payload(payload)
                .build();
    }
}
