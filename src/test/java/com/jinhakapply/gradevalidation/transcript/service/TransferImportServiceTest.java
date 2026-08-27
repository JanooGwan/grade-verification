package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jinhakapply.gradevalidation.admission.domain.AdmissionTrack;
import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;
import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;
import com.jinhakapply.gradevalidation.admission.repository.AdmissionTrackRepository;
import com.jinhakapply.gradevalidation.admission.repository.RecruitmentUnitRepository;
import com.jinhakapply.gradevalidation.admission.repository.StudentApplicationRepository;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.test.util.ReflectionTestUtils;

class TransferImportServiceTest {

    @Test
    void classifiesCreatedUpdatedUnchangedAndUnknownBatchResultsSeparately() {
        TransferImportService.CourseResult result = TransferImportService.classifyBatchResults(new int[][] {
            {1, 2, 0, Statement.SUCCESS_NO_INFO, Statement.EXECUTE_FAILED}
        });

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.unknown()).isEqualTo(2);
    }

    @Test
    void keepsGraduationStatusInferredFromTransferWhenSchoolInfoHasNoGraduationYear() {
        Student student = Student.create(2027, "A-001", "미등록", null, null, 2026);
        ApplicantSchoolInfoRow schoolInfo = new ApplicantSchoolInfoRow(
            2, 2027, "A-001", null, "S-001", "직업고등학교", "전문학과",
            "실업고", "특성화고", "전문계고교",
            EducationBackground.DOMESTIC_HIGH_SCHOOL, HighSchoolType.SPECIALIZED
        );

        TransferImportService.applySchoolInfo(student, schoolInfo);

        assertThat(student.getGraduationStatus()).isEqualTo(GraduationStatus.GRADUATE);
        assertThat(student.getHighSchoolType()).isEqualTo(HighSchoolType.SPECIALIZED);
        assertThat(student.getApplicantHighSchoolCategoryCode()).isEqualTo("전문계고교");
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacesCoursesForApplicantsRemovedEntirelyFromTheReuploadedFile() {
        StudentTranscriptCourseRepository courseRepository = mock(StudentTranscriptCourseRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TransferImportService service = new TransferImportService(
            null, null, null, null, null, null, courseRepository, null, jdbcTemplate, null
        );
        Student first = mock(Student.class);
        when(first.getId()).thenReturn(11L);
        List<TranscriptExcelRow> rows = List.of(new TranscriptExcelRow(
            2, "A-001", "미등록", null, null, null,
            1, 1, SubjectCategory.SOCIAL, "사회문화", 2, GradeScale.NINE_LEVEL,
            null, null, null, null, 100, 1, null, null,
            new BigDecimal("4"), false, false
        ));
        University university = University.create("HS", "한신대학교");
        StudentTranscriptImport currentImport = StudentTranscriptImport.create(
            university, 2027, "reupload.xlsx", TranscriptImportMode.ALL_OR_NOTHING,
            "current", 1, 1, 0, "HANSHIN_MULTI_SHEET_V1"
        );
        ReflectionTestUtils.setField(currentImport, "id", 31L);
        when(jdbcTemplate.batchUpdate(
            anyString(), same(rows), anyInt(), any(ParameterizedPreparedStatementSetter.class)
        )).thenReturn(new int[][] {{1}});

        TransferImportService.CourseResult result = service.replaceCourses(
            rows, Map.of("A-001", first), "reupload.xlsx", currentImport, 7
        );

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(7);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletesApplicationsMissingFromTheReuploadedUniversitySnapshot() {
        AdmissionTrackRepository trackRepository = mock(AdmissionTrackRepository.class);
        RecruitmentUnitRepository unitRepository = mock(RecruitmentUnitRepository.class);
        StudentApplicationRepository applicationRepository = mock(StudentApplicationRepository.class);
        TransferImportService service = new TransferImportService(
            null, null, trackRepository, unitRepository, applicationRepository,
            null, null, null, null, null
        );
        University university = University.create("HS", "한신대학교");
        ReflectionTestUtils.setField(university, "id", 1L);
        AdmissionTrack track = AdmissionTrack.create(university, 2027, "참인재");
        ReflectionTestUtils.setField(track, "id", 101L);
        RecruitmentUnit desiredUnit = RecruitmentUnit.create(track, "21", "한국어문학");
        ReflectionTestUtils.setField(desiredUnit, "id", 201L);
        RecruitmentUnit staleUnit = RecruitmentUnit.create(track, "22", "문예창작");
        ReflectionTestUtils.setField(staleUnit, "id", 202L);
        Student student = Student.create(university, 2027, "A-001", "학생", null, null, 2027);
        ReflectionTestUtils.setField(student, "id", 11L);
        when(trackRepository.findAllByUniversityIdAndAdmissionYearOrderByNameAsc(1L, 2027))
            .thenReturn(List.of(track));
        when(unitRepository.findAllByAdmissionTrackIdInOrderByNameAsc(List.of(101L)))
            .thenReturn(List.of(desiredUnit, staleUnit));
        StudentTranscriptImport currentImport = StudentTranscriptImport.create(
            university, 2027, "reupload.xlsx", TranscriptImportMode.ALL_OR_NOTHING,
            "current", 1, 1, 0, "HANSHIN_MULTI_SHEET_V1"
        );
        ReflectionTestUtils.setField(currentImport, "id", 31L);

        TransferImportService.CatalogResult result = service.importApplications(
            university,
            2027,
            List.of(new TransferApplicationRow(
                2, 2027, "A-001", "06", "참인재", "21", "한국어문학", 2027
            )),
            Map.of("A-001", student),
            currentImport,
            2
        );

        ArgumentCaptor<Iterable<StudentApplication>> created = ArgumentCaptor.forClass(Iterable.class);
        verify(applicationRepository).saveAll(created.capture());
        assertThat(created.getValue()).singleElement().satisfies(application -> {
            assertThat(application.getStudent()).isSameAs(student);
            assertThat(application.getRecruitmentUnit()).isSameAs(desiredUnit);
        });
        assertThat(result.createdApplications()).isEqualTo(1);
        assertThat(result.deletedApplications()).isEqualTo(2);
    }
}
