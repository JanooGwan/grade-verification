package com.jinhakapply.gradevalidation.assistant.controller;

import com.jinhakapply.gradevalidation.assistant.dto.AssistantMessageRequest;
import com.jinhakapply.gradevalidation.assistant.dto.AssistantMessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "AI Assistant", description = "읽기 전용 데이터베이스 질의 도우미 API")
@RequestMapping("/api/assistant")
public interface AssistantApi {

    @Operation(summary = "데이터베이스 근거 답변 요청")
    @PostMapping("/messages")
    ResponseEntity<AssistantMessageResponse> ask(@Valid @RequestBody AssistantMessageRequest request);
}
