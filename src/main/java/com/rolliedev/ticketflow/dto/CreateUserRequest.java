package com.rolliedev.ticketflow.dto;

import com.rolliedev.ticketflow.entity.enums.Role;

public record CreateUserRequest(String firstName,
                                String lastName,
                                String email,
                                Role role) {
}
