package com.jinhakapply.gradevalidation.assistant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(
    boolean enabled,
    Anthropic anthropic,
    Database database,
    @Valid Query query
) {
    public record Anthropic(String apiKey, String model, String baseUrl, String version) {
    }

    public record Database(String url, String username, String password) {
    }

    public record Query(
        @Min(1) @Max(100) int maxRows,
        @Min(1) @Max(5) int timeoutSeconds
    ) {
    }
}
