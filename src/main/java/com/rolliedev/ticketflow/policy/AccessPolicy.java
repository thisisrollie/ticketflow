package com.rolliedev.ticketflow.policy;

import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.exception.TicketFlowAccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class AccessPolicy {

    public void requireAgentOrAdmin(UserEntity actor, String message) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.AGENT) {
            throw new TicketFlowAccessDeniedException(message);
        }
    }

    public void requireTicketAssignee(TicketEntity ticket, UserEntity actor, String message) {
        UserEntity assignee = ticket.getAssignedTo();
        if (assignee == null || !assignee.getId().equals(actor.getId())) {
            throw new TicketFlowAccessDeniedException(message);
        }
    }

    public void requireCustomer(UserEntity actor, String message) {
        if (actor.getRole() != Role.CUSTOMER) {
            throw new TicketFlowAccessDeniedException(message);
        }
    }

    public void requireTicketOwner(TicketEntity ticket, UserEntity actor, String message) {
        UserEntity owner = ticket.getCreatedBy();
        if (!owner.getId().equals(actor.getId())) {
            throw new TicketFlowAccessDeniedException(message);
        }
    }

    public void requireTicketOwnerIfCustomer(UserEntity actor, TicketEntity ticket, String message) {
        UserEntity ticketOwner = ticket.getCreatedBy();
        if (actor.getRole() == Role.CUSTOMER && !ticketOwner.getId().equals(actor.getId())) {
            throw new TicketFlowAccessDeniedException(message);
        }
    }

    public void requireAdminOrCommentAuthor(UserEntity actor, TicketCommentEntity comment, String message) {
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isAuthor = comment.getAuthor().getId().equals(actor.getId());

        if (!isAdmin && !isAuthor) {
            throw new TicketFlowAccessDeniedException(message);
        }
    }
}
