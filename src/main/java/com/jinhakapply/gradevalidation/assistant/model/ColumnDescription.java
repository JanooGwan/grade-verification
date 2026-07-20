package com.jinhakapply.gradevalidation.assistant.model;

public record ColumnDescription(
    String tableName,
    String columnName,
    String dataType,
    boolean nullable,
    String comment
) {
}
