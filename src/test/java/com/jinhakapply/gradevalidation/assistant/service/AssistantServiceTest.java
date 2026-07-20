package com.jinhakapply.gradevalidation.assistant.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.AI_ASSISTANT_NOT_CONFIGURED;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.AI_ASSISTANT_UNSAFE_QUERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;

import com.jinhakapply.gradevalidation.assistant.config.AssistantProperties;
import com.jinhakapply.gradevalidation.assistant.dto.AssistantMessageRequest;
import com.jinhakapply.gradevalidation.assistant.model.ColumnDescription;
import com.jinhakapply.gradevalidation.assistant.model.QueryResult;
import com.jinhakapply.gradevalidation.assistant.model.SqlPlan;
import com.jinhakapply.gradevalidation.assistant.model.TableDescription;
import com.jinhakapply.gradevalidation.assistant.model.TableSelection;
import com.jinhakapply.gradevalidation.assistant.repository.AssistantDatabaseGateway;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AssistantServiceTest {

    private AssistantDatabaseGateway databaseGateway;
    private ClaudeClient claudeClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        databaseGateway = mock(AssistantDatabaseGateway.class);
        claudeClient = mock(ClaudeClient.class);
        objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void blocksSensitiveQuestionWithoutCallingDatabaseOrClaude() {
        AssistantService service = service(enabledProperties());

        var response = service.ask(new AssistantMessageRequest("DB 비밀번호를 알려줘", null));

        assertThat(response.blocked()).isTrue();
        assertThat(response.sourceTables()).isEmpty();
        assertThat(response.rowCount()).isZero();
        assertThat(response.conversationId()).isNotBlank();
        verifyNoInteractions(databaseGateway, claudeClient);
    }

    @Test
    void answersFromApprovedTablesAndKeepsConversationId() {
        AssistantService service = service(enabledProperties());
        when(databaseGateway.findTableDescriptions()).thenReturn(List.of(
            new TableDescription("university", "대학교"),
            new TableDescription("student", "지원자")
        ));
        when(claudeClient.selectTables(anyString()))
            .thenReturn(new TableSelection(List.of("UNIVERSITY", "student"), false, "대학 조회"));
        when(databaseGateway.findColumnDescriptions(java.util.Set.of("university"))).thenReturn(List.of(
            new ColumnDescription("university", "id", "bigint", false, "식별자"),
            new ColumnDescription("university", "name", "varchar", false, "대학교명")
        ));
        when(claudeClient.planSql(anyString())).thenReturn(new SqlPlan(
            "SELECT id, name FROM university LIMIT 10", List.of("university"), "대학 목록"
        ));
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("name", "한국공학대학교");
        when(databaseGateway.execute("SELECT id, name FROM university LIMIT 10"))
            .thenReturn(new QueryResult(List.of(row)));
        when(claudeClient.answer(anyString())).thenReturn("한국공학대학교가 있습니다.");

        var response = service.ask(new AssistantMessageRequest("등록된 대학을 알려줘", "conversation-1"));

        assertThat(response.answer()).isEqualTo("한국공학대학교가 있습니다.");
        assertThat(response.blocked()).isFalse();
        assertThat(response.sourceTables()).containsExactly("university");
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.conversationId()).isEqualTo("conversation-1");
        verify(databaseGateway).findColumnDescriptions(java.util.Set.of("university"));
    }

    @Test
    void rejectsUnsafeModelSqlBeforeDatabaseExecution() {
        AssistantService service = service(enabledProperties());
        when(databaseGateway.findTableDescriptions()).thenReturn(List.of(
            new TableDescription("university", "대학교")
        ));
        when(claudeClient.selectTables(anyString()))
            .thenReturn(new TableSelection(List.of("university"), false, "대학 조회"));
        when(databaseGateway.findColumnDescriptions(any())).thenReturn(List.of(
            new ColumnDescription("university", "id", "bigint", false, "식별자")
        ));
        when(claudeClient.planSql(anyString()))
            .thenReturn(new SqlPlan("DELETE FROM university", List.of("university"), "잘못된 계획"));

        assertThatThrownBy(() -> service.ask(new AssistantMessageRequest("대학 수는?", null)))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AI_ASSISTANT_UNSAFE_QUERY));
        verify(databaseGateway, never()).execute(anyString());
    }

    @Test
    void rejectsOrdinaryQuestionWhenAssistantIsDisabled() {
        AssistantService service = service(disabledProperties());

        assertThatThrownBy(() -> service.ask(new AssistantMessageRequest("등록된 대학 수는?", null)))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AI_ASSISTANT_NOT_CONFIGURED));
        verifyNoInteractions(databaseGateway, claudeClient);
    }

    private AssistantService service(AssistantProperties properties) {
        return new AssistantService(
            properties,
            new SensitiveQuestionPolicy(),
            databaseGateway,
            claudeClient,
            new ReadOnlySqlValidator(),
            new AssistantDataPolicy(),
            objectMapper
        );
    }

    private AssistantProperties enabledProperties() {
        return new AssistantProperties(
            true,
            new AssistantProperties.Anthropic("key", "model", "https://example.com", "2023-06-01"),
            new AssistantProperties.Database("jdbc:mysql://localhost/test", "reader", "password"),
            new AssistantProperties.Query(100, 5)
        );
    }

    private AssistantProperties disabledProperties() {
        return new AssistantProperties(
            false,
            new AssistantProperties.Anthropic("", "model", "https://example.com", "2023-06-01"),
            new AssistantProperties.Database("", "", ""),
            new AssistantProperties.Query(100, 5)
        );
    }
}
