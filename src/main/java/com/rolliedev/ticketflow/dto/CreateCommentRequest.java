package com.rolliedev.ticketflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Value;

@Value
public class CreateCommentRequest {
    @NotNull
    Integer authorId;
    @NotBlank
    @Size(max = 4000)
    String text;
}
