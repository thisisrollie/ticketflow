package com.rolliedev.ticketflow.dto;

public record CreateTicketRequest(String title,
                                  String description,
                                  Integer creatorId) {
}
