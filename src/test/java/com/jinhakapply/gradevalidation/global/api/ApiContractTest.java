package com.jinhakapply.gradevalidation.global.api;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.AI_ASSISTANT_NOT_CONFIGURED;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.jinhakapply.gradevalidation.admission.controller.AdmissionController;
import com.jinhakapply.gradevalidation.admission.dto.AdmissionTrackResponse;
import com.jinhakapply.gradevalidation.admission.service.AdmissionService;
import com.jinhakapply.gradevalidation.admission.service.ApplicationScoreService;
import com.jinhakapply.gradevalidation.assistant.controller.AssistantController;
import com.jinhakapply.gradevalidation.assistant.dto.AssistantMessageResponse;
import com.jinhakapply.gradevalidation.assistant.service.AssistantService;
import com.jinhakapply.gradevalidation.evaluation.controller.EvaluationController;
import com.jinhakapply.gradevalidation.evaluation.controller.RuleExtractionController;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.domain.RuleExtractionStatus;
import com.jinhakapply.gradevalidation.evaluation.dto.RuleExtractionResponse;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.evaluation.service.RuleExtractionService;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.operation.controller.OperationsController;
import com.jinhakapply.gradevalidation.operation.dto.OperationsDashboardResponse;
import com.jinhakapply.gradevalidation.operation.service.OperationalMetrics;
import com.jinhakapply.gradevalidation.operation.service.OperationsDashboardService;
import com.jinhakapply.gradevalidation.transcript.controller.TranscriptController;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StudentPageResponse;
import com.jinhakapply.gradevalidation.transcript.service.TranscriptService;
import com.jinhakapply.gradevalidation.university.controller.UniversityController;
import com.jinhakapply.gradevalidation.university.dto.UniversityResponse;
import com.jinhakapply.gradevalidation.university.service.UniversityService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest({
    UniversityController.class,
    AdmissionController.class,
    EvaluationController.class,
    RuleExtractionController.class,
    TranscriptController.class,
    AssistantController.class,
    OperationsController.class
})
class ApiContractTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UniversityService universityService;
    @MockitoBean AdmissionService admissionService;
    @MockitoBean ApplicationScoreService applicationScoreService;
    @MockitoBean EvaluationService evaluationService;
    @MockitoBean RuleExtractionService ruleExtractionService;
    @MockitoBean TranscriptService transcriptService;
    @MockitoBean AssistantService assistantService;
    @MockitoBean OperationsDashboardService operationsDashboardService;
    @MockitoBean OperationalMetrics operationalMetrics;

    @Test
    void createsUniversityAndReturnsResourceLocation() throws Exception {
        when(universityService.create(any())).thenReturn(
            new UniversityResponse(7L, "TUK", "한국공학대학교", true, null, null)
        );

        mockMvc.perform(post("/api/universities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"TUK","name":"한국공학대학교"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/universities/7"))
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.code").value("TUK"))
            .andExpect(jsonPath("$.name").value("한국공학대학교"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsInvalidUniversityRequestWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/universities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"invalid code!","name":" "}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
            .andExpect(jsonPath("$.fieldErrors.code").exists())
            .andExpect(jsonPath("$.fieldErrors.name").exists())
            .andExpect(jsonPath("$.timestamp").exists());

        verify(universityService, never()).create(any());
    }

    @Test
    void mapsDomainFailureToApiErrorContract() throws Exception {
        when(universityService.findById(99L)).thenThrow(CustomException.of(UNIVERSITY_NOT_FOUND, "99"));

        mockMvc.perform(get("/api/universities/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("UNIVERSITY_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("99")))
            .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void deletesUniversityWithoutResponseBody() throws Exception {
        mockMvc.perform(delete("/api/universities/7"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(universityService).delete(7L);
    }

    @Test
    void createsAdmissionTrackAndBindsItsRequest() throws Exception {
        when(admissionService.createTrack(any())).thenReturn(new AdmissionTrackResponse(
            11L, 7L, "한국공학대학교", 2027, "학생부교과", true, List.of(), null, null
        ));

        mockMvc.perform(post("/api/admissions/tracks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"universityId":7,"admissionYear":2027,"name":"학생부교과"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/admissions/tracks/11"))
            .andExpect(jsonPath("$.admissionYear").value(2027))
            .andExpect(jsonPath("$.recruitmentUnits").isArray());
    }

    @Test
    void bindsAdmissionTrackQueryParameters() throws Exception {
        when(admissionService.findTracks(7L, 2027)).thenReturn(List.of());

        mockMvc.perform(get("/api/admissions/tracks")
                .param("universityId", "7")
                .param("admissionYear", "2027"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        verify(admissionService).findTracks(7L, 2027);
    }

    @Test
    void rejectsOutOfRangeAdmissionYear() throws Exception {
        mockMvc.perform(get("/api/admissions/tracks")
                .param("universityId", "7")
                .param("admissionYear", "1999"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(admissionService, never()).findTracks(anyLong(), anyInt());
    }

    @Test
    void rejectsMissingRequiredQueryParameter() throws Exception {
        mockMvc.perform(get("/api/admissions/tracks").param("universityId", "7"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
            .andExpect(jsonPath("$.fieldErrors.admissionYear").exists());

        verify(admissionService, never()).findTracks(anyLong(), anyInt());
    }

    @Test
    void rejectsInvalidApplicationScoreRequestBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/admissions/students/3/applications/5/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"schoolViolenceAction":10,"essayScore":801}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
            .andExpect(jsonPath("$.fieldErrors.educationBackground").exists())
            .andExpect(jsonPath("$.fieldErrors.schoolViolenceAction").exists())
            .andExpect(jsonPath("$.fieldErrors.essayScore").exists());

        verify(applicationScoreService, never()).calculate(anyLong(), anyLong(), any());
    }

    @Test
    void bindsEvaluationStatusEnum() throws Exception {
        when(evaluationService.findAdminRules(EvaluationRuleStatus.PUBLISHED)).thenReturn(List.of());

        mockMvc.perform(get("/api/evaluations/rules/admin").param("status", "PUBLISHED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        verify(evaluationService).findAdminRules(EvaluationRuleStatus.PUBLISHED);
    }

    @Test
    void rejectsInvalidEvaluationStatus() throws Exception {
        mockMvc.perform(get("/api/evaluations/rules/admin").param("status", "INVALID"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(evaluationService, never()).findAdminRules(any());
    }

    @Test
    void uploadsRulePdfAsMultipartAndReturnsExtractionLocation() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "guide.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7".getBytes()
        );
        when(ruleExtractionService.extract(anyLong(), anyInt(), any())).thenReturn(extractionResponse());

        mockMvc.perform(multipart("/api/evaluations/rule-extractions/pdf")
                .file(file)
                .param("universityId", "7")
                .param("admissionYear", "2027"))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                HttpHeaders.LOCATION, "/api/evaluations/rule-extractions/31"
            ))
            .andExpect(jsonPath("$.originalFileName").value("guide.pdf"));

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(ruleExtractionService).extract(org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(2027), fileCaptor.capture());
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("guide.pdf");
        assertThat(fileCaptor.getValue().getSize()).isPositive();
    }

    @Test
    void rejectsInvalidRuleExtractionIdentifier() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "guide.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7".getBytes()
        );

        mockMvc.perform(multipart("/api/evaluations/rule-extractions/pdf")
                .file(file)
                .param("universityId", "0")
                .param("admissionYear", "2027"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(ruleExtractionService, never()).extract(anyLong(), anyInt(), any());
    }

    @Test
    void importsTranscriptExcelAndReturnsImportLocation() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "transcript.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[]{1, 2, 3}
        );
        when(transcriptService.importExcel(anyInt(), any(), any())).thenReturn(new TranscriptImportResponse(
            41L, TranscriptImportStatus.COMPLETED, 1, 1, 0, 1, 0, 1, 0, List.of()
        ));

        mockMvc.perform(multipart("/api/transcripts/imports/excel")
                .file(file)
                .param("admissionYear", "2027"))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/transcripts/imports/41"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.importedRows").value(1));
    }

    @Test
    void downloadsTranscriptTemplateWithAttachmentHeaders() throws Exception {
        byte[] workbook = new byte[]{1, 2, 3, 4};
        when(transcriptService.createExcelTemplate()).thenReturn(workbook);

        mockMvc.perform(get("/api/transcripts/imports/template"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student-transcript-template.xlsx"
            ))
            .andExpect(content().contentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .andExpect(content().bytes(workbook));
    }

    @Test
    void appliesStudentPagingDefaults() throws Exception {
        when(transcriptService.findStudents(2027, null, 0, 20)).thenReturn(
            new StudentPageResponse(List.of(), 0, 20, 0, 0, true, true)
        );

        mockMvc.perform(get("/api/transcripts/students").param("admissionYear", "2027"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.content").isArray());

        verify(transcriptService).findStudents(2027, null, 0, 20);
    }

    @Test
    void rejectsOversizedStudentPage() throws Exception {
        mockMvc.perform(get("/api/transcripts/students")
                .param("admissionYear", "2027")
                .param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(transcriptService, never()).findStudents(anyInt(), any(), anyInt(), anyInt());
    }

    @Test
    void returnsAssistantAnswerAndSourceMetadata() throws Exception {
        when(assistantService.ask(any())).thenReturn(new AssistantMessageResponse(
            "조회 결과입니다.", false, List.of("university", "admission_track"), 2, "conversation-1"
        ));

        mockMvc.perform(post("/api/assistant/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question":"한국공학대 전형을 알려줘","conversationId":"conversation-1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("조회 결과입니다."))
            .andExpect(jsonPath("$.blocked").value(false))
            .andExpect(jsonPath("$.sourceTables[0]").value("university"))
            .andExpect(jsonPath("$.rowCount").value(2))
            .andExpect(jsonPath("$.conversationId").value("conversation-1"));
    }

    @Test
    void rejectsInvalidAssistantRequestBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/assistant/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question":" ","conversationId":"invalid id!"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
            .andExpect(jsonPath("$.fieldErrors.question").exists())
            .andExpect(jsonPath("$.fieldErrors.conversationId").exists());

        verify(assistantService, never()).ask(any());
    }

    @Test
    void rejectsMalformedJsonAsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/assistant/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(assistantService, never()).ask(any());
    }

    @Test
    void mapsAssistantConfigurationFailureToServiceUnavailable() throws Exception {
        when(assistantService.ask(any())).thenThrow(CustomException.of(AI_ASSISTANT_NOT_CONFIGURED));

        mockMvc.perform(post("/api/assistant/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question":"등록된 대학 수를 알려줘","conversationId":"conversation-1"}
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI_ASSISTANT_NOT_CONFIGURED"));
    }

    @Test
    void returnsOperationsDashboardContract() throws Exception {
        OperationalMetrics.Snapshot snapshot = new OperationalMetrics.Snapshot(
            LocalDateTime.of(2026, 7, 20, 9, 0), 12, 1, 15, 80, List.of()
        );
        when(operationsDashboardService.getDashboard()).thenReturn(new OperationsDashboardResponse(
            3, 120, 960, 5, 80, 22, 4,
            new OperationsDashboardResponse.RuleCounts(1, 2, 3, 4), snapshot
        ));

        mockMvc.perform(get("/api/operations/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.universities").value(3))
            .andExpect(jsonPath("$.students").value(120))
            .andExpect(jsonPath("$.rules.published").value(3))
            .andExpect(jsonPath("$.http.totalRequests").value(12))
            .andExpect(jsonPath("$.http.errorRequests").value(1));
    }

    private RuleExtractionResponse extractionResponse() {
        return new RuleExtractionResponse(
            31L, 7L, "한국공학대학교", 2027, "guide.pdf", "a".repeat(64),
            10, 10, RuleExtractionStatus.EXTRACTED, null, new BigDecimal("0.95"),
            null, List.of(), List.of(), List.of(), null
        );
    }
}
