package com.jinhakapply.gradevalidation.assistant.service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ReadOnlySqlValidator {

    private static final Pattern TABLE_REFERENCE = Pattern.compile(
        "(?i)\\b(?:from|join)\\s+`?([a-zA-Z0-9_]+)`?"
    );
    private static final Pattern FORBIDDEN = Pattern.compile(
        "(?i)\\b(insert|update|delete|replace|merge|drop|alter|create|truncate|rename|grant|revoke|"
            + "call|execute|prepare|handler|load|into|outfile|dumpfile|lock|unlock|sleep|benchmark)\\b"
    );
    private static final Pattern SENSITIVE_IDENTIFIER = Pattern.compile(
        "(?i)\\b(password|passwd|api_?key|secret|access_?token|refresh_?token|credential|connection_?string|"
            + "reviewer|review_?note|published_?by|publication_?note|retired_?by|retire_?note)\\b"
    );
    private static final Pattern SELECT_CLAUSE = Pattern.compile("(?is)^select\\s+(?:distinct\\s+)?(.*?)\\s+from\\b");
    private static final Pattern WILDCARD_PROJECTION = Pattern.compile(
        "(?is)(?:^|,)\\s*(?:[a-zA-Z][a-zA-Z0-9_]*\\.)?\\*\\s*(?:,|$)"
    );
    private static final Pattern FROM_CLAUSE = Pattern.compile(
        "(?is)\\bfrom\\b(.*?)(?=\\bwhere\\b|\\bgroup\\s+by\\b|\\border\\s+by\\b|\\bhaving\\b|\\blimit\\b|$)"
    );
    private static final Pattern UNQUOTED_RESERVED_ALIAS_REFERENCE = Pattern.compile(
        "(?i)(?<!`)\\b(?:as|by|case|group|in|is|join|key|limit|on|order|references|select|table|where)\\s*\\."
    );
    private static final Pattern TABLE_ALIAS_DECLARATION = Pattern.compile(
        "(?i)\\b(?:from|join)\\s+`?[a-zA-Z0-9_]+`?\\s+(?:(as)\\s+)?(`?[a-zA-Z_][a-zA-Z0-9_]*`?)"
    );
    private static final Set<String> RESERVED_ALIASES = Set.of(
        "as", "by", "case", "group", "in", "is", "join", "key", "limit", "on", "order",
        "references", "select", "table", "where"
    );
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
        "information_schema", "mysql", "performance_schema", "sys"
    );

    public Set<String> validate(String sql, Set<String> allowedTables) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL이 비어 있습니다.");
        }
        String normalized = sql.strip();
        String syntaxOnly = withoutStringLiterals(normalized);
        String lower = syntaxOnly.toLowerCase(Locale.ROOT);
        if (!lower.matches("(?s)^select\\s+.*") || syntaxOnly.contains(";") || syntaxOnly.contains("--")
            || syntaxOnly.contains("/*") || syntaxOnly.contains("#") || FORBIDDEN.matcher(syntaxOnly).find()) {
            throw new IllegalArgumentException("SELECT 한 문장만 허용됩니다.");
        }
        if (lower.matches("(?s).*\\bfor\\s+update\\b.*")) {
            throw new IllegalArgumentException("잠금 조회는 허용되지 않습니다.");
        }
        if (SENSITIVE_IDENTIFIER.matcher(syntaxOnly).find()) {
            throw new IllegalArgumentException("민감정보 컬럼은 조회할 수 없습니다.");
        }
        if (UNQUOTED_RESERVED_ALIAS_REFERENCE.matcher(syntaxOnly).find()
            || hasReservedAliasDeclaration(syntaxOnly)) {
            throw new IllegalArgumentException(
                "MySQL reserved words cannot be used as unquoted table aliases. Use an alias starting with t_."
            );
        }
        Matcher selectClause = SELECT_CLAUSE.matcher(syntaxOnly);
        if (!selectClause.find() || WILDCARD_PROJECTION.matcher(selectClause.group(1)).find()) {
            throw new IllegalArgumentException("조회할 컬럼을 명시해 주세요.");
        }
        Matcher fromClause = FROM_CLAUSE.matcher(syntaxOnly);
        while (fromClause.find()) {
            if (fromClause.group(1).contains(",")) {
                throw new IllegalArgumentException("쉼표 조인은 허용되지 않습니다. 명시적 JOIN을 사용하세요.");
            }
        }
        for (String schema : SYSTEM_SCHEMAS) {
            if (lower.contains(schema + ".")) {
                throw new IllegalArgumentException("시스템 스키마는 조회할 수 없습니다.");
            }
        }

        Set<String> referenced = new HashSet<>();
        Matcher matcher = TABLE_REFERENCE.matcher(syntaxOnly);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!allowedTables.contains(table)) {
                throw new IllegalArgumentException("허용되지 않은 테이블입니다: " + table);
            }
            referenced.add(table);
        }
        if (referenced.isEmpty()) {
            throw new IllegalArgumentException("조회 대상 테이블을 확인할 수 없습니다.");
        }
        return Set.copyOf(referenced);
    }

    private boolean hasReservedAliasDeclaration(String sql) {
        Matcher matcher = TABLE_ALIAS_DECLARATION.matcher(sql);
        while (matcher.find()) {
            boolean explicitAlias = matcher.group(1) != null;
            String alias = matcher.group(2);
            String lowerAlias = alias.toLowerCase(Locale.ROOT);
            if (!explicitAlias && isClauseKeyword(sql, matcher.end(2), lowerAlias)) continue;
            if (!alias.startsWith("`") && RESERVED_ALIASES.contains(lowerAlias)) return true;
        }
        return false;
    }

    private boolean isClauseKeyword(String sql, int tokenEnd, String token) {
        if (Set.of("where", "join", "on", "limit").contains(token)) return true;
        if (!Set.of("group", "order").contains(token)) return false;
        return sql.substring(tokenEnd).matches("(?is)^\\s+by\\b.*");
    }

    private String withoutStringLiterals(String sql) {
        return sql.replaceAll("'(?:''|\\\\.|[^'\\\\])*'", "''");
    }
}
