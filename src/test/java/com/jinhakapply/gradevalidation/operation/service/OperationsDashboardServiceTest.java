package com.jinhakapply.gradevalidation.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.jinhakapply.gradevalidation.admission.repository.StudentApplicationRepository;
import com.jinhakapply.gradevalidation.admission.repository.VerificationRunRepository;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleExtractionRepository;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.operation.repository.UniversityDataStatusProjection;
import com.jinhakapply.gradevalidation.operation.repository.UniversityDataStatusRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationsDashboardServiceTest {

    @Mock UniversityRepository universityRepository;
    @Mock StudentRepository studentRepository;
    @Mock StudentTranscriptCourseRepository courseRepository;
    @Mock StudentTranscriptImportRepository importRepository;
    @Mock StudentApplicationRepository applicationRepository;
    @Mock VerificationRunRepository verificationRunRepository;
    @Mock EvaluationRuleExtractionRepository extractionRepository;
    @Mock EvaluationRuleRepository ruleRepository;
    @Mock UniversityDataStatusRepository dataStatusRepository;
    @Mock OperationalMetrics metrics;
    @InjectMocks OperationsDashboardService service;

    @Test
    void combinesRepositoryCountsAndHttpMetrics() {
        when(universityRepository.count()).thenReturn(2L);
        when(studentRepository.count()).thenReturn(30L);
        when(courseRepository.count()).thenReturn(900L);
        when(importRepository.count()).thenReturn(1L);
        when(applicationRepository.count()).thenReturn(12L);
        when(verificationRunRepository.count()).thenReturn(8L);
        when(extractionRepository.count()).thenReturn(4L);
        when(ruleRepository.countByStatus(EvaluationRuleStatus.DRAFT)).thenReturn(3L);
        when(ruleRepository.countByStatus(EvaluationRuleStatus.VERIFIED)).thenReturn(2L);
        when(ruleRepository.countByStatus(EvaluationRuleStatus.PUBLISHED)).thenReturn(5L);
        when(ruleRepository.countByStatus(EvaluationRuleStatus.RETIRED)).thenReturn(1L);
        LocalDateTime importedAt = LocalDateTime.of(2026, 8, 8, 14, 30);
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 8, 8, 15, 10);
        when(dataStatusRepository.findAll()).thenReturn(List.of(new UniversityDataStatusProjection(
            1L, "HS", "한신대학교", true, 2027, 30, 900, 12,
            "COMPLETED", "학생성적.xlsx", importedAt, 8, verifiedAt
        )));
        OperationalMetrics.Snapshot http = new OperationalMetrics.Snapshot(
            LocalDateTime.now(), 20, 2, 15, 70, List.of()
        );
        when(metrics.snapshot()).thenReturn(http);

        var response = service.getDashboard();

        assertThat(response.universities()).isEqualTo(2);
        assertThat(response.students()).isEqualTo(30);
        assertThat(response.transcriptCourses()).isEqualTo(900);
        assertThat(response.studentApplications()).isEqualTo(12);
        assertThat(response.rules()).satisfies(counts -> {
            assertThat(counts.draft()).isEqualTo(3);
            assertThat(counts.verified()).isEqualTo(2);
            assertThat(counts.published()).isEqualTo(5);
            assertThat(counts.retired()).isEqualTo(1);
        });
        assertThat(response.universityDataStatuses()).singleElement().satisfies(status -> {
            assertThat(status.universityName()).isEqualTo("한신대학교");
            assertThat(status.admissionYear()).isEqualTo(2027);
            assertThat(status.studentDataPresent()).isTrue();
            assertThat(status.latestImportAt()).isEqualTo(importedAt);
            assertThat(status.verificationDataPresent()).isTrue();
            assertThat(status.latestVerificationAt()).isEqualTo(verifiedAt);
        });
        assertThat(response.http()).isSameAs(http);
    }
}
