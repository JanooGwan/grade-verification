package com.jinhakapply.gradevalidation.assistant.dto;

import java.util.List;

public record AssistantMessageResponse(
    String answer,
    boolean blocked,
    List<String> sourceTables,
    int rowCount,
    String conversationId
) {
    public static AssistantMessageResponse blocked(String answer, String conversationId) {
        return new AssistantMessageResponse(answer, true, List.of(), 0, conversationId);
    }
}
