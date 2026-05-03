package com.rolliedev.ticketflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicRegistrationRequest(
        @NotBlank @Size(max = 64) String firstName,
        @NotBlank @Size(max = 64) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String rawPassword) {
}
