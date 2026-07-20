package com.jinhakapply.gradevalidation.assistant.repository;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.AI_ASSISTANT_DATABASE_ERROR;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.assistant.config.AssistantProperties;
import com.jinhakapply.gradevalidation.assistant.model.ColumnDescription;
import com.jinhakapply.gradevalidation.assistant.model.QueryResult;
import com.jinhakapply.gradevalidation.assistant.model.TableDescription;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AssistantDatabaseGateway {

    private final AssistantProperties properties;

    public List<TableDescription> findTableDescriptions() {
        String sql = """
            SELECT TABLE_NAME, COALESCE(NULLIF(TABLE_COMMENT, ''), '설명 미등록')
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_TYPE = 'BASE TABLE'
              AND TABLE_NAME <> 'flyway_schema_history'
            ORDER BY TABLE_NAME
            """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<TableDescription> descriptions = new ArrayList<>();
            while (resultSet.next()) {
                descriptions.add(new TableDescription(resultSet.getString(1), resultSet.getString(2)));
            }
            return descriptions;
        } catch (SQLException exception) {
            throw databaseError(exception);
        }
    }

    public List<ColumnDescription> findColumnDescriptions(Collection<String> tableNames) {
        if (tableNames.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", tableNames.stream().map(ignored -> "?").toList());
        String sql = """
            SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COALESCE(COLUMN_COMMENT, '')
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (%s)
            ORDER BY TABLE_NAME, ORDINAL_POSITION
            """.formatted(placeholders);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String tableName : tableNames) {
                statement.setString(index++, tableName);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ColumnDescription> descriptions = new ArrayList<>();
                while (resultSet.next()) {
                    descriptions.add(new ColumnDescription(
                        resultSet.getString(1), resultSet.getString(2), resultSet.getString(3),
                        "YES".equals(resultSet.getString(4)), resultSet.getString(5)
                    ));
                }
                return descriptions;
            }
        } catch (SQLException exception) {
            throw databaseError(exception);
        }
    }

    public QueryResult execute(String sql) {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            int timeoutSeconds = Math.min(5, Math.max(1, properties.query().timeoutSeconds()));
            int maxRows = Math.min(100, Math.max(1, properties.query().maxRows()));
            statement.setQueryTimeout(timeoutSeconds);
            statement.setMaxRows(maxRows);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        row.put(metadata.getColumnLabel(column), serializableValue(resultSet.getObject(column)));
                    }
                    rows.add(row);
                }
                return new QueryResult(List.copyOf(rows));
            }
        } catch (SQLException exception) {
            throw databaseError(exception);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
            properties.database().url(), properties.database().username(), properties.database().password()
        );
        connection.setReadOnly(true);
        return connection;
    }

    private Object serializableValue(Object value) {
        if (value instanceof byte[]) {
            return "[binary data omitted]";
        }
        if (value instanceof TemporalAccessor) {
            return value.toString();
        }
        return value;
    }

    private CustomException databaseError(SQLException exception) {
        return CustomException.of(AI_ASSISTANT_DATABASE_ERROR, "SQL state=" + exception.getSQLState());
    }
}
