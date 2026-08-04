package com.jinhakapply.gradevalidation.evaluation.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.CourseFilter;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.GroupDimension;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SelectionMetric;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SelectionStage;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SelectionStageType;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SortDirection;
import org.junit.jupiter.api.Test;

class SelectionPolicyConverterTest {
    private final SelectionPolicyConverter converter = new SelectionPolicyConverter();

    @Test
    void roundTripsVersionedPolicyJson() {
        CourseSelectionPolicy policy = new CourseSelectionPolicy(1,
            new CourseFilter(Set.of(SubjectCategory.KOREAN, SubjectCategory.MATH), true, false, false),
            List.of(new SelectionStage(SelectionStageType.TOP_COURSES, GroupDimension.SUBJECT,
                GroupDimension.NONE, SelectionMetric.CONVERTED_SCORE, SortDirection.DESC, 3)));

        String json = converter.convertToDatabaseColumn(policy);

        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(policy);
        assertThat(json).contains("\"schemaVersion\":1", "\"TOP_COURSES\"");
    }
}
