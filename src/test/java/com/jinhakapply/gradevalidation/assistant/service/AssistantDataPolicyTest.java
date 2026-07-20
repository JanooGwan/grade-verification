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
            new TableDescription("student", "지원자")
        ))).extracting(TableDescription::name).containsExactly("university");

        assertThat(policy.filterColumns(List.of(
            new ColumnDescription("evaluation_rule", "name", "varchar", false, "규칙명"),
            new ColumnDescription("evaluation_rule", "reviewer", "varchar", true, "검수자")
        ))).extracting(ColumnDescription::columnName).containsExactly("name");
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
