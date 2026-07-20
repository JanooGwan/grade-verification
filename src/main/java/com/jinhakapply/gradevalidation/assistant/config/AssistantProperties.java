package com.jinhakapply.gradevalidation.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(
    boolean enabled,
    Anthropic anthropic,
    Database database,
    Query query
) {
    public record Anthropic(String apiKey, String model, String baseUrl, String version) {
    }

    public record Database(String url, String username, String password) {
    }

    public record Query(int maxRows, int timeoutSeconds) {
    }
}
