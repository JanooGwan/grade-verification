package com.jinhakapply.gradevalidation.admission.service;

import static com.jinhakapply.gradevalidation.global.util.TextNormalizer.normalizePolicyText;

import java.util.Set;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import org.springframework.stereotype.Component;

@Component
class EvaluationRuleMatcher {

    private static final Set<String> COMMON_UNIT_NAMES = Set.of(
        "전체", "전체모집단위", "전모집단위", "전체모집학과", "전체학과", "전학과",
        "공통", "모든모집단위"
    );

    boolean matchesAdmissionType(EvaluationRule rule, String trackName, String unitName) {
        String ruleType = normalizePolicyText(rule.getAdmissionType());
        String sourceType = normalizePolicyText(trackName);
        if (ruleType.equals(sourceType)) return true;
        if (!"MJC".equalsIgnoreCase(rule.getUniversity().getCode())) return false;

        if (sourceType.equals("일반실기면접위주")) {
            return isAviation(unitName)
                ? ruleType.contains("일반전형면접위주")
                : isPractical(unitName) && ruleType.contains("일반전형실기위주");
        }
        if (sourceType.equals("특별일반고")) return ruleType.contains("특별전형일반고");
        if (sourceType.equals("특별특성화고")) return ruleType.contains("특별전형특성화고");
        if (sourceType.equals("특별연계교육")) return ruleType.contains("특별전형협약을통한연계교육");
        if (sourceType.equals("특별대학자체기준")) return ruleType.contains("특별전형대학자체기준");
        if (sourceType.equals("농어촌")) return ruleType.contains("특별전형농어촌학생");
        if (sourceType.equals("기회균형")) return ruleType.contains("특별전형기회균형");
        return false;
    }

    boolean matchesRecruitmentUnit(EvaluationRule rule, String unitName) {
        String ruleUnit = normalizePolicyText(rule.getRecruitmentUnit());
        String sourceUnit = normalizePolicyText(unitName);
        if (ruleUnit.equals(sourceUnit) || COMMON_UNIT_NAMES.contains(ruleUnit)) return true;
        if (!"MJC".equalsIgnoreCase(rule.getUniversity().getCode())) return false;
        if (ruleUnit.equals("실기학과")) return isPractical(unitName);
        if (ruleUnit.equals("일반학과")) return !isAviation(unitName) && !isPractical(unitName);
        return false;
    }

    boolean exactlyMatchesRecruitmentUnit(EvaluationRule rule, String unitName) {
        return normalizePolicyText(rule.getRecruitmentUnit()).equals(normalizePolicyText(unitName));
    }

    private boolean isAviation(String unitName) {
        return normalizePolicyText(unitName).contains("항공서비스");
    }

    private boolean isPractical(String unitName) {
        String unit = normalizePolicyText(unitName);
        return unit.contains("실용음악") || unit.contains("연극영상") || unit.contains("문예창작")
            || unit.contains("산업디자인") || unit.contains("사회체육");
    }
}
