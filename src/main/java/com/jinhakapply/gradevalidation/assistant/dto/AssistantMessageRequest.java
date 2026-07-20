package com.jinhakapply.gradevalidation.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AssistantMessageRequest(
    @NotBlank
    @Size(max = 4000)
    String question,

    @Pattern(regexp = "^[a-zA-Z0-9_-]{0,80}$")
    String conversationId
) {
}
