package com.rolliedev.ticketflow.dto;

import java.time.Instant;

public record CommentResponse(Long id,
                              UserSummary author,
                              String body,
                              Instant createdAt) {
}
