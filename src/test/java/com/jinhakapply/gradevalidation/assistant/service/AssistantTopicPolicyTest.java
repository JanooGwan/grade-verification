package com.jinhakapply.gradevalidation.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssistantTopicPolicyTest {

    private final AssistantTopicPolicy policy = new AssistantTopicPolicy();

    @Test
    void allowsAdmissionAndGradeVerificationQuestions() {
        assertThat(policy.isInScope("한신대 학생부 반영 규칙을 알려줘")).isTrue();
        assertThat(policy.isInScope("지원자별 학교폭력 감점 결과를 보여줘")).isTrue();
        assertThat(policy.isInScope("evaluation_rule 테이블은 어떤 역할이야?")).isTrue();
    }

    @Test
    void blocksClearExternalTopicsAndQuestionsWithoutAdmissionContext() {
        assertThat(policy.isInScope("오늘 날씨는 어때?")).isFalse();
        assertThat(policy.isInScope("대학 근처 오늘 날씨를 알려줘")).isFalse();
        assertThat(policy.isInScope("지금 원달러 환율은?")).isFalse();
        assertThat(policy.isInScope("최신 경제 주요 뉴스를 요약해줘")).isFalse();
        assertThat(policy.isInScope("자바 정렬 방법을 알려줘")).isFalse();
    }
}
