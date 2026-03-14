package com.rolliedev.ticketflow.dto;

import com.rolliedev.ticketflow.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(@NotBlank @Size(max = 64) String firstName,
                                @NotBlank @Size(max = 64) String lastName,
                                @NotBlank @Email @Size(max = 255) String email,
                                @NotNull Role role) {
}
