package com.rolliedev.ticketflow.dto;

import com.rolliedev.ticketflow.entity.enums.Role;

import java.time.Instant;

public record UserResponse(Integer id,
                           String fullName,
                           String email,
                           Role role,
                           Instant createdAt) {
}
