package com.rolliedev.ticketflow.exception;

// For "not found" cases — maps to HTTP 404
public class ResourceNotFoundException extends TicketFlowException {

    private final String resourceName;
    private final Object resourceId;

    public ResourceNotFoundException(String resourceName, Object resourceId) {
        super("%s not found with id: %s".formatted(resourceName, resourceId));
        this.resourceName = resourceName;
        this.resourceId = resourceId;
    }

    public static ResourceNotFoundException ticket(Long id) {
        return new ResourceNotFoundException("Ticket", id);
    }

    public static ResourceNotFoundException user(Integer id) {
        return new ResourceNotFoundException("User", id);
    }

    public static ResourceNotFoundException comment(Long id) {
        return new ResourceNotFoundException("Comment", id);
    }
}
