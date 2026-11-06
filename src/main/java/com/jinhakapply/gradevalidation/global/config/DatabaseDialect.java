package com.jinhakapply.gradevalidation.global.config;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Database-specific SQL is kept at the few places that need it. */
public final class DatabaseDialect {
    private DatabaseDialect() {}

    public static boolean isPostgresql(JdbcTemplate jdbc) {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
            "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
    }

    public static String upsert(JdbcTemplate jdbc, String mysql, String conflictColumns) {
        if (!isPostgresql(jdbc)) return mysql;
        return mysql.replace("ON DUPLICATE KEY UPDATE", "ON CONFLICT (" + conflictColumns + ") DO UPDATE SET")
            .replaceAll("VALUES\\((\\w+)\\)", "EXCLUDED.$1");
    }
}
