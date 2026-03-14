package com.rolliedev.ticketflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class CreateCommentRequest {
    @NotNull
    Integer authorId;
    @NotBlank
    String text;
}
