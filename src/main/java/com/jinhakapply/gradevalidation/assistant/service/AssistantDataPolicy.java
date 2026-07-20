package com.jinhakapply.gradevalidation.assistant.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import com.jinhakapply.gradevalidation.assistant.model.ColumnDescription;
import com.jinhakapply.gradevalidation.assistant.model.QueryResult;
import com.jinhakapply.gradevalidation.assistant.model.TableDescription;
import org.springframework.stereotype.Component;

@Component
public class AssistantDataPolicy {

    private static final Set<String> APPROVED_TABLES = Set.of(
        "university",
        "admission_track",
        "recruitment_unit",
        "evaluation_rule",
        "evaluation_rule_grade_score",
        "evaluation_rule_achievement_grade",
        "evaluation_rule_achievement_score",
        "evaluation_rule_subject_priority",
        "evaluation_rule_extraction",
        "evaluation_rule_extraction_evidence"
    );
    private static final Set<String> BLOCKED_COLUMNS = Set.of(
        "reviewer",
        "review_note",
        "published_by",
        "publication_note",
        "retired_by",
        "retire_note"
    );

    public Set<String> approvedTables() {
        return APPROVED_TABLES;
    }

    public List<TableDescription> filterTables(List<TableDescription> tables) {
        return tables.stream()
            .filter(table -> APPROVED_TABLES.contains(normalize(table.name())))
            .toList();
    }

    public List<ColumnDescription> filterColumns(List<ColumnDescription> columns) {
        return columns.stream()
            .filter(column -> APPROVED_TABLES.contains(normalize(column.tableName())))
            .filter(column -> !BLOCKED_COLUMNS.contains(normalize(column.columnName())))
            .toList();
    }

    public QueryResult sanitize(QueryResult result) {
        List<Map<String, Object>> rows = result.rows().stream().map(row -> {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            row.forEach((column, value) -> {
                if (!BLOCKED_COLUMNS.contains(normalize(column))) {
                    sanitized.put(column, value);
                }
            });
            return Collections.unmodifiableMap(sanitized);
        }).toList();
        return new QueryResult(rows);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
