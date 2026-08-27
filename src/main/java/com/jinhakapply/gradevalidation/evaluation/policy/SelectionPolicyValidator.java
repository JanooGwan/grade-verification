package com.jinhakapply.gradevalidation.evaluation.policy;

import java.util.ArrayList;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.GroupDimension;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SelectionStage;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SelectionStageType;

public final class SelectionPolicyValidator {
    private SelectionPolicyValidator() {}

    public static List<String> validate(CourseSelectionPolicy policy) {
        List<String> errors = new ArrayList<>();
        if (policy == null) {
            errors.add("정책은 필수입니다.");
            return errors;
        }
        if (policy.schemaVersion() != CourseSelectionPolicy.CURRENT_SCHEMA_VERSION) {
            errors.add("지원하지 않는 정책 스키마 버전입니다: " + policy.schemaVersion());
        }
        if (!policy.filter().includeGeneralCourses()
            && !policy.filter().includeCareerCourses()
            && !policy.filter().includeProfessionalCourses()) {
            errors.add("일반·진로·전문교과 중 하나 이상을 포함해야 합니다.");
        }
        if (policy.stages().isEmpty()) {
            errors.add("선택 단계가 하나 이상 필요합니다.");
        }
        for (int index = 0; index < policy.stages().size(); index++) {
            SelectionStage stage = policy.stages().get(index);
            String prefix = "stages[" + index + "]: ";
            if (stage == null) {
                errors.add(prefix + "단계가 비어 있습니다.");
                continue;
            }
            if (stage.type() == null) errors.add(prefix + "type은 필수입니다.");
            if (stage.metric() == null) errors.add(prefix + "metric은 필수입니다.");
            if (stage.limit() < 1 || stage.limit() > 100) {
                errors.add(prefix + "limit은 1 이상 100 이하여야 합니다.");
            }
            if (stage.type() == SelectionStageType.TOP_GROUPS
                && (stage.groupBy() == null || stage.groupBy() == GroupDimension.NONE)) {
                errors.add(prefix + "TOP_GROUPS에는 groupBy가 필요합니다.");
            }
            if (stage.type() == SelectionStageType.TOP_COURSES
                && stage.groupBy() != null && stage.groupBy() != GroupDimension.NONE) {
                errors.add(prefix + "TOP_COURSES에는 groupBy를 사용할 수 없습니다.");
            }
        }
        return List.copyOf(errors);
    }
}
