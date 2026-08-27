package com.jinhakapply.gradevalidation.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;

import com.jinhakapply.gradevalidation.assistant.model.ColumnDescription;
import com.jinhakapply.gradevalidation.assistant.model.QueryResult;
import com.jinhakapply.gradevalidation.assistant.model.TableDescription;
import org.junit.jupiter.api.Test;

class AssistantDataPolicyTest {

    private final AssistantDataPolicy policy = new AssistantDataPolicy();

    @Test
    void excludesStudentRelationsAndReviewerFields() {
        assertThat(policy.filterTables(List.of(
            new TableDescription("university", "대학"),
            new TableDescription("student", "지원자"),
            new TableDescription("ai_applicant_course_count_statistics", "VIEW")
        ))).extracting(TableDescription::name)
            .containsExactly("university", "ai_applicant_course_count_statistics");

        assertThat(policy.filterTables(List.of(
            new TableDescription("ai_applicant_course_count_statistics", "VIEW")
        )).getFirst().comment()).contains("수강 과목 수별 지원자 수");

        assertThat(policy.filterColumns(List.of(
            new ColumnDescription("evaluation_rule", "name", "varchar", false, "규칙명"),
            new ColumnDescription("evaluation_rule", "reviewer", "varchar", true, "검수자"),
            new ColumnDescription(
                "ai_applicant_course_count_statistics", "applicant_count", "bigint", false, ""
            )
        ))).extracting(ColumnDescription::columnName).containsExactly("name", "applicant_count");

        assertThat(policy.filterColumns(List.of(
            new ColumnDescription(
                "ai_applicant_course_count_statistics", "applicant_count", "bigint", false, ""
            )
        )).getFirst().comment()).contains("지원자 수");

        assertThat(policy.approvedTables())
            .contains("ai_applicant_statistics", "ai_applicant_course_count_statistics")
            .doesNotContain("student", "student_transcript_course", "student_application");
    }

    @Test
    void removesBlockedFieldsBeforeProviderCallAndPreservesNulls() {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("name", null);
        row.put("reviewer", "operator");

        QueryResult sanitized = policy.sanitize(new QueryResult(List.of(row)));

        assertThat(sanitized.rows().getFirst()).containsOnlyKeys("name");
        assertThat(sanitized.rows().getFirst().get("name")).isNull();
    }
}
