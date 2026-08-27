package com.jinhakapply.gradevalidation.assistant.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        "evaluation_rule_extraction_evidence",
        "ai_applicant_statistics",
        "ai_applicant_course_count_statistics",
        "ai_application_statistics",
        "ai_course_enrollment_statistics"
    );
    private static final Map<String, String> AGGREGATE_TABLE_DESCRIPTIONS = Map.of(
        "ai_applicant_statistics",
        "대학·모집연도별 전체 지원자 수를 제공하는 개인정보 없는 집계 뷰",
        "ai_applicant_course_count_statistics",
        "대학·모집연도·수강 과목 수별 지원자 수를 제공하는 개인정보 없는 집계 뷰",
        "ai_application_statistics",
        "대학·모집연도·전형·모집단위별 지원자 수를 제공하는 개인정보 없는 집계 뷰",
        "ai_course_enrollment_statistics",
        "대학·모집연도·교과·정규화 과목명별 이수 지원자 수와 평균 성적을 제공하는 개인정보 없는 집계 뷰"
    );
    private static final Map<String, String> AGGREGATE_COLUMN_DESCRIPTIONS = Map.ofEntries(
        Map.entry("university_code", "집계 대상 대학을 식별하는 영문 코드"),
        Map.entry("university_name", "집계 대상 대학의 공식 명칭"),
        Map.entry("admission_year", "지원자가 입학을 지원한 모집연도"),
        Map.entry("applicant_count", "해당 집계 조건에 포함되는 중복 제거 지원자 수"),
        Map.entry("course_count", "지원자 한 명에게 저장된 서로 다른 학년·학기·정규화 과목 성적 행 수"),
        Map.entry("admission_track_name", "지원자가 선택한 전형명"),
        Map.entry("recruitment_unit_code", "대학이 부여한 모집단위 코드"),
        Map.entry("recruitment_unit_name", "지원자가 선택한 학과·학부 등 모집단위명"),
        Map.entry("subject_category", "과목이 속한 교과군 코드"),
        Map.entry("course_name_normalized", "공백·구두점 차이를 제거해 동일 과목을 묶은 정규화 과목명"),
        Map.entry("course_record_count", "해당 집계 조건에 포함되는 전체 과목 성적 행 수"),
        Map.entry("average_credits", "해당 집계 조건에 포함되는 과목들의 평균 이수단위"),
        Map.entry("average_grade_value", "석차등급이 존재하는 과목들의 평균 석차등급")
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
            .map(table -> new TableDescription(
                table.name(), AGGREGATE_TABLE_DESCRIPTIONS.getOrDefault(normalize(table.name()), table.comment())
            ))
            .toList();
    }

    public List<ColumnDescription> filterColumns(List<ColumnDescription> columns) {
        return columns.stream()
            .filter(column -> APPROVED_TABLES.contains(normalize(column.tableName())))
            .filter(column -> !BLOCKED_COLUMNS.contains(normalize(column.columnName())))
            .map(column -> new ColumnDescription(
                column.tableName(), column.columnName(), column.dataType(), column.nullable(),
                columnComment(column)
            ))
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

    private String columnComment(ColumnDescription column) {
        if (!AGGREGATE_TABLE_DESCRIPTIONS.containsKey(normalize(column.tableName()))) {
            return column.comment();
        }
        return AGGREGATE_COLUMN_DESCRIPTIONS.getOrDefault(normalize(column.columnName()), column.comment());
    }
}
