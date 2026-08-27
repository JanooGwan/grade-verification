package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class StoredTranscriptVerificationServiceTest {

    @Mock StudentTranscriptImportRepository importRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock TranscriptBatchVerificationService batchVerificationService;
    @Mock TranscriptValidationExcelWriter validationExcelWriter;

    private StoredTranscriptVerificationService service;

    @BeforeEach
    void setUp() {
        service = new StoredTranscriptVerificationService(
            importRepository, jdbcTemplate, batchVerificationService, validationExcelWriter
        );
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
