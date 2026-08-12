package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptPreviewResponse;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class StoredTranscriptVerificationServiceTest {

    @Mock StudentTranscriptImportRepository importRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock TranscriptBatchVerificationService batchVerificationService;
    @Mock TranscriptValidationExcelWriter validationExcelWriter;
    @Mock SyuImportScoreExcelWriter syuImportScoreExcelWriter;
    @Mock BatchVerificationRunRepository batchVerificationRunRepository;
    @Mock ObjectMapper objectMapper;

    private StoredTranscriptVerificationService service;

    @BeforeEach
    void setUp() {
        service = new StoredTranscriptVerificationService(
            importRepository,
            jdbcTemplate,
            batchVerificationService,
            validationExcelWriter,
            syuImportScoreExcelWriter,
            batchVerificationRunRepository,
            objectMapper
        );
    }

    @Test
    void verifiesSyuSourceAsACommonCourseScenarioWithoutStoredApplications() {
        University university = University.create("SY", "삼육대학교");
        StudentTranscriptImport transcriptImport = StudentTranscriptImport.create(
            university, 2027, "syu-source.xlsx", TranscriptImportMode.VALID_ROWS_ONLY,
            "hash", 100, 100, 0, SyuSourceExcelStreamer.SOURCE_FORMAT
        );
        TranscriptPreviewResponse expected = org.mockito.Mockito.mock(TranscriptPreviewResponse.class);
        when(importRepository.findTopByUniversity_IdAndAdmissionYearAndStatusInOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2027),
            org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(Optional.of(transcriptImport));
        when(syuImportScoreExcelWriter.preview(transcriptImport)).thenReturn(expected);

        assertThat(service.verify(1L, 2027)).isSameAs(expected);

        verify(syuImportScoreExcelWriter).preview(transcriptImport);
        verifyNoInteractions(jdbcTemplate, batchVerificationService, validationExcelWriter);
    }

    @Test
    void exportsSyuSourceWithTheSameCommonCourseScenario() {
        University university = University.create("SY", "삼육대학교");
        StudentTranscriptImport transcriptImport = StudentTranscriptImport.create(
            university, 2027, "syu-source.xlsx", TranscriptImportMode.VALID_ROWS_ONLY,
            "hash", 100, 100, 0, SyuSourceExcelStreamer.SOURCE_FORMAT
        );
        byte[] expected = {1, 2, 3};
        when(importRepository.findTopByUniversity_IdAndAdmissionYearAndStatusInOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2027),
            org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(Optional.of(transcriptImport));
        when(syuImportScoreExcelWriter.write(transcriptImport)).thenReturn(expected);

        assertThat(service.export(1L, 2027)).isSameAs(expected);

        verify(syuImportScoreExcelWriter).write(transcriptImport);
        verifyNoInteractions(jdbcTemplate, batchVerificationService, validationExcelWriter);
    }

    @Test
    void refusesVerificationWhenNoCompletedDatabaseImportExists() {
        when(importRepository.findTopByUniversity_IdAndAdmissionYearAndStatusInOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq(4L),
            org.mockito.ArgumentMatchers.eq(2027),
            org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(4L, 2027))
            .isInstanceOfSatisfying(CustomException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.STORED_TRANSCRIPT_DATA_NOT_FOUND);
                assertThat(exception.getFullMessage()).contains("DB 저장 데이터를 먼저 업로드");
            });

        verifyNoInteractions(jdbcTemplate, batchVerificationService, validationExcelWriter);
    }
}
