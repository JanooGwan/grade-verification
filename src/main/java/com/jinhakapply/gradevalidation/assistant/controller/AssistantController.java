package com.jinhakapply.gradevalidation.assistant.controller;

import com.jinhakapply.gradevalidation.assistant.dto.AssistantMessageRequest;
import com.jinhakapply.gradevalidation.assistant.dto.AssistantMessageResponse;
import com.jinhakapply.gradevalidation.assistant.service.AssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AssistantController implements AssistantApi {

    private final AssistantService assistantService;

    @Override
    public ResponseEntity<AssistantMessageResponse> ask(AssistantMessageRequest request) {
        return ResponseEntity.ok(assistantService.ask(request));
    }
}
