package com.jinhakapply.gradevalidation.evaluation.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.CourseFilter;
import org.junit.jupiter.api.Test;

class SelectionPolicyValidatorTest {

    @Test
    void rejectsUnknownSchemaAndPolicyThatCannotSelectAnything() {
        CourseSelectionPolicy policy = new CourseSelectionPolicy(99,
            new CourseFilter(null, false, false, false), List.of());

        assertThat(SelectionPolicyValidator.validate(policy))
            .contains("지원하지 않는 정책 스키마 버전입니다: 99")
            .contains("일반·진로·전문교과 중 하나 이상을 포함해야 합니다.")
            .contains("선택 단계가 하나 이상 필요합니다.");
    }
}
