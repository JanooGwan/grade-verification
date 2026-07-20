package com.jinhakapply.gradevalidation.assistant.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class SensitiveQuestionPolicy {

    private static final List<String> BLOCKED_PHRASES = List.of(
        "비밀번호", "패스워드", "암호", "api key", "apikey", "secret", "시크릿",
        "access token", "refresh token", "credential", "접속 정보", "접속정보",
        "db 계정", "database account", "connection string", "환경 변수", "환경변수",
        ".env", "system prompt", "시스템 프롬프트", "지침을 무시", "ignore previous"
    );

    public boolean isBlocked(String question) {
        String normalized = question.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return BLOCKED_PHRASES.stream().anyMatch(normalized::contains);
    }
}
