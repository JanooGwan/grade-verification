package com.jinhakapply.gradevalidation.admission.dto;

import java.util.List;

import com.jinhakapply.gradevalidation.admission.domain.RuleMatchStatus;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;

public record RuleMatchResponse(
    RuleMatchStatus status,
    String message,
    Long matchedRuleId,
    List<RuleCandidate> candidates
) {
    public static RuleMatchResponse matched(EvaluationRule rule) {
        return new RuleMatchResponse(
            RuleMatchStatus.MATCHED,
            "지원 정보에 맞는 게시 규칙을 찾았습니다.",
            rule.getId(),
            List.of(RuleCandidate.from(rule))
        );
    }

    public static RuleMatchResponse notFound() {
        return new RuleMatchResponse(
            RuleMatchStatus.NOT_FOUND,
            "이 대학·연도·전형·모집단위에 게시된 규칙이 없습니다.",
            null,
            List.of()
        );
    }

    public static RuleMatchResponse conflict(List<EvaluationRule> rules) {
        return new RuleMatchResponse(
            RuleMatchStatus.CONFLICT,
            "적용 가능한 게시 규칙이 여러 개입니다. 규칙 관리자에서 중복 규칙을 정리해 주세요.",
            null,
            rules.stream().map(RuleCandidate::from).toList()
        );
    }

    public record RuleCandidate(
        Long ruleId,
        String name,
        int version,
        String admissionType,
        String recruitmentUnit,
        String sourceDocument,
        String sourcePages
    ) {
        private static RuleCandidate from(EvaluationRule rule) {
            return new RuleCandidate(
                rule.getId(), rule.getName(), rule.getVersion(), rule.getAdmissionType(),
                rule.getRecruitmentUnit(), rule.getSourceDocument(), rule.getSourcePages()
            );
        }
    }
}
