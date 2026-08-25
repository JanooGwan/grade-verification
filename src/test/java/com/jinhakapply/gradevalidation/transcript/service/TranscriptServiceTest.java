package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.jinhakapply.gradevalidation.admission.repository.VerificationRunRepository;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptCourse;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;
import com.jinhakapply.gradevalidation.transcript.dto.UpsertTranscriptCourseRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentCommonDataRequest;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import com.jinhakapply.gradevalidation.transcript.dto.StudentPageResponse;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentCourseSummaryProjection;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentAttendanceRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentSchoolViolenceActionRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentGedSubjectScoreRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentLegacyGradeSummaryRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TranscriptServiceTest {

    @Mock
    private TranscriptExcelParser excelParser;
    @Mock
    private TransferExcelParser transferExcelParser;
    @Mock
    private ApplicantSchoolInfoExcelParser applicantSchoolInfoExcelParser;
    @Mock
    private TransferImportService transferImportService;
    @Mock
    private TranscriptImportResultExcelWriter importResultExcelWriter;
    @Mock
    private SyuImportScoreExcelWriter syuImportScoreExcelWriter;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentTranscriptCourseRepository courseRepository;
    @Mock
    private StudentTranscriptImportRepository importRepository;
    @Mock
    private StudentAttendanceRepository attendanceRepository;
    @Mock
    private StudentSchoolViolenceActionRepository schoolViolenceRepository;
    @Mock
    private StudentGedSubjectScoreRepository gedSubjectScoreRepository;
    @Mock
    private StudentLegacyGradeSummaryRepository legacyGradeSummaryRepository;
    @Mock
    private UniversityRepository universityRepository;
    @Mock
    private TranscriptSnapshotReplacementService snapshotReplacementService;
    @Mock
    private VerificationRunRepository verificationRunRepository;

    private TranscriptService transcriptService;
    private University university;

    @BeforeEach
    void setUp() {
        university = University.create("TEST", "테스트대학교");
        ReflectionTestUtils.setField(university, "id", 1L);
        org.mockito.Mockito.lenient().when(universityRepository.findById(1L))
            .thenReturn(Optional.of(university));
        org.mockito.Mockito.lenient().when(snapshotReplacementService.clear(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.eq(false)
        )).thenReturn(new TranscriptSnapshotReplacementService.SnapshotScope(
            List.of(), 0, 0, 0, 0
        ));
        transcriptService = new TranscriptService(
            excelParser,
            transferExcelParser,
            applicantSchoolInfoExcelParser,
            transferImportService,
            importResultExcelWriter,
            syuImportScoreExcelWriter,
            studentRepository,
            courseRepository,
            importRepository,
            attendanceRepository,
            schoolViolenceRepository,
            gedSubjectScoreRepository,
            legacyGradeSummaryRepository,
            universityRepository,
            snapshotReplacementService,
            verificationRunRepository
        );
    }

    @Test
    void reportsWhetherEachImportHasSavedVerificationResults() {
        StudentTranscriptImport savedImport = StudentTranscriptImport.create(
            university, 2026, "saved.xlsx", TranscriptImportMode.ALL_OR_NOTHING,
            "saved", 10, 10, 0, "STANDARD_TRANSCRIPT_V1"
        );
        StudentTranscriptImport unsavedImport = StudentTranscriptImport.create(
            university, 2026, "unsaved.xlsx", TranscriptImportMode.ALL_OR_NOTHING,
            "unsaved", 10, 10, 0, "STANDARD_TRANSCRIPT_V1"
        );
        ReflectionTestUtils.setField(savedImport, "id", 21L);
        ReflectionTestUtils.setField(unsavedImport, "id", 22L);
        when(importRepository.findTop50ByUniversity_IdOrderByCreatedAtDesc(1L))
            .thenReturn(List.of(unsavedImport, savedImport));
        when(verificationRunRepository.findSourceImportIdsWithResults(List.of(22L, 21L)))
            .thenReturn(Set.of(21L));

        var result = transcriptService.findImports(1L);

        assertThat(result).extracting(
            response -> response.importId() + ":" + response.hasSavedVerificationResults()
        ).containsExactly("22:false", "21:true");
    }

    @Test
    void storesUniversityCommonStudentDataSeparatelyFromScoreRuns() {
        Student student = Student.create(university, 2027, "A-001", "학생", "S001", "고등학교", 2027);
        ReflectionTestUtils.setField(student, "id", 1L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(attendanceRepository.findAllByStudent_IdOrderBySchoolYearAsc(1L)).thenReturn(List.of());
        when(schoolViolenceRepository.findAllByStudent_IdOrderBySchoolYearAscActionNumberAsc(1L))
            .thenReturn(List.of());
        when(courseRepository.findAllByStudent_IdOrderBySchoolYearAscSemesterAscCourseNameAsc(1L))
            .thenReturn(List.of());
        var request = new UpdateStudentCommonDataRequest(
            EducationBackground.GED, GraduationStatus.GRADUATE, new BigDecimal("94.50"),
            List.of(new UpdateStudentCommonDataRequest.Attendance(1, 2, 3, 1, 0)),
            List.of(new UpdateStudentCommonDataRequest.SchoolViolenceAction(2, 4, null, true, "확정"))
        );

        transcriptService.updateStudentCommonData(1L, request);

        assertThat(student.getEducationBackground()).isEqualTo(EducationBackground.GED);
        assertThat(student.getGraduationStatus()).isEqualTo(GraduationStatus.GRADUATE);
        assertThat(student.getGedAverageScore()).isEqualByComparingTo("94.50");
        verify(attendanceRepository).saveAll(any());
        verify(schoolViolenceRepository).saveAll(any());
    }

    @Test
    void rejectsDuplicateAttendanceSchoolYearsBeforeReplacingCommonData() {
        Student student = Student.create(2027, "A-001", "학생", null, null, 2027);
        ReflectionTestUtils.setField(student, "id", 1L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        var request = new UpdateStudentCommonDataRequest(
            EducationBackground.DOMESTIC_HIGH_SCHOOL, GraduationStatus.EXPECTED_GRADUATE, null,
            List.of(
                new UpdateStudentCommonDataRequest.Attendance(1, 0, 0, 0, 0),
                new UpdateStudentCommonDataRequest.Attendance(1, 1, 0, 0, 0)
            ),
            List.of()
        );

        assertThatThrownBy(() -> transcriptService.updateStudentCommonData(1L, request))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_STUDENT_COMMON_DATA));
        verifyNoInteractions(attendanceRepository, schoolViolenceRepository);
    }

    @Test
    void requiresAverageScoreForGedApplicant() {
        Student student = Student.create(2027, "A-001", "학생", null, null, null);
        ReflectionTestUtils.setField(student, "id", 1L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        var request = new UpdateStudentCommonDataRequest(
            EducationBackground.GED, GraduationStatus.GRADUATE, null, List.of(), List.of()
        );

        assertThatThrownBy(() -> transcriptService.updateStudentCommonData(1L, request))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_STUDENT_COMMON_DATA));
        verifyNoInteractions(attendanceRepository, schoolViolenceRepository);
    }

    @Test
    void rejectsGedAverageScoreForHighSchoolApplicant() {
        Student student = Student.create(2027, "A-001", "학생", null, null, 2026);
        ReflectionTestUtils.setField(student, "id", 1L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        var request = new UpdateStudentCommonDataRequest(
            EducationBackground.FOREIGN_HIGH_SCHOOL, GraduationStatus.GRADUATE, new BigDecimal("90.0"),
            List.of(), List.of()
        );

        assertThatThrownBy(() -> transcriptService.updateStudentCommonData(1L, request))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_STUDENT_COMMON_DATA));
        verifyNoInteractions(attendanceRepository, schoolViolenceRepository);
    }

    @Test
    void updatesExistingStudentAndCreatesCourse() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "transcript.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[] {1}
        );
        TranscriptExcelRow row = new TranscriptExcelRow(
            2,
            "A-001",
            "학생",
            "S001",
            "고등학교",
            2027,
            1,
            1,
            SubjectCategory.MATH,
            "수학",
            2,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("3"),
            false,
            false
        );
        Student student = Student.create(university, 2027, "A-001", "기존명", null, null, null);
        ReflectionTestUtils.setField(student, "id", 1L);

        when(excelParser.parse(file)).thenReturn(new TranscriptExcelParseResult(1, List.of(row), List.of()));
        when(studentRepository.findAllByUniversity_IdAndAdmissionYearAndApplicantNumberIn(
            1L, 2027, java.util.Set.of("A-001")
        ))
            .thenReturn(List.of(student));
        org.mockito.Mockito.lenient().when(studentRepository.findByUniversity_IdAndAdmissionYearAndApplicantNumber(
            1L, 2027, "A-001"
        ))
            .thenReturn(Optional.of(student));
        org.mockito.Mockito.lenient().when(courseRepository.findByStudent_IdAndSchoolYearAndSemesterAndCourseNameNormalized(
            1L, 1, 1, "수학"
        )).thenReturn(Optional.empty());
        when(courseRepository.save(any(StudentTranscriptCourse.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(importRepository.save(any(StudentTranscriptImport.class))).thenAnswer(invocation -> {
            StudentTranscriptImport transcriptImport = invocation.getArgument(0);
            ReflectionTestUtils.setField(transcriptImport, "id", 10L);
            return transcriptImport;
        });
        StudentTranscriptImport previousImport = StudentTranscriptImport.create(
            university, 2027, "transcript.xlsx", TranscriptImportMode.VALID_ROWS_ONLY,
            "previous", 2, 2, 0, "STANDARD_TRANSCRIPT_V1"
        );
        ReflectionTestUtils.setField(previousImport, "id", 9L);
        when(importRepository.findTopByUniversity_IdAndAdmissionYearAndStatusInOrderByCreatedAtDesc(
            1L, 2027,
            List.of(TranscriptImportStatus.COMPLETED, TranscriptImportStatus.COMPLETED_WITH_ERRORS)
        )).thenReturn(Optional.of(previousImport));
        when(snapshotReplacementService.clear(1L, 2027, false)).thenReturn(
            new TranscriptSnapshotReplacementService.SnapshotScope(List.of(student), 0, 2, 0, 0)
        );

        TranscriptImportResponse response = transcriptService.importExcel(2027, TranscriptImportMode.VALID_ROWS_ONLY, 1L, file);

        assertThat(response.importId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(TranscriptImportStatus.COMPLETED);
        assertThat(response.updatedStudents()).isEqualTo(1);
        assertThat(response.createdCourses()).isEqualTo(1);
        assertThat(response.deletedCourses()).isEqualTo(2);
        assertThat(student.getName()).isEqualTo("학생");
        verify(courseRepository).save(any(StudentTranscriptCourse.class));
    }

    @Test
    void findsStudentPageWithCourseSummary() {
        Student student = Student.create(university, 2027, "MJC27S001", "합성지원자001", "S001", "합성고", 2027);
        ReflectionTestUtils.setField(student, "id", 1L);
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "applicantNumber"));
        when(studentRepository.search(1L, 2027, "합성", pageable))
            .thenReturn(new PageImpl<>(List.of(student), pageable, 1));
        StudentCourseSummaryProjection summary = org.mockito.Mockito.mock(StudentCourseSummaryProjection.class);
        when(summary.getStudentId()).thenReturn(1L);
        when(summary.getCourseCount()).thenReturn(30L);
        when(summary.getAverageGrade()).thenReturn(3.456);
        when(courseRepository.summarizeByStudentIds(List.of(1L))).thenReturn(List.of(summary));

        StudentPageResponse response = transcriptService.findStudents(1L, 2027, " 합성 ", 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().courseCount()).isEqualTo(30);
        assertThat(response.content().getFirst().averageGrade()).isEqualByComparingTo("3.46");
    }

    @Test
    void rejectsUnsupportedFileExtensionBeforeParsing() {
        MockMultipartFile file = new MockMultipartFile("file", "transcript.csv", "text/csv", new byte[] {1});

        assertThatThrownBy(() -> transcriptService.importExcel(2027, file))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_TRANSCRIPT_FILE));
        verifyNoInteractions(excelParser);
    }

    @Test
    void rejectsEmptyTranscriptFileBeforeParsing() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "transcript.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]
        );

        assertThatThrownBy(() -> transcriptService.importExcel(2027, file))
            .isInstanceOf(CustomException.class);
        verifyNoInteractions(excelParser);
    }

    @Test
    void exportsSyuSourceImportWithTheSelectedAdmissionYearScoreRule() {
        StudentTranscriptImport transcriptImport = StudentTranscriptImport.create(
            university, 2027, "syu-2026-source.xlsx", TranscriptImportMode.VALID_ROWS_ONLY,
            "hash", 10, 10, 0, SyuSourceExcelStreamer.SOURCE_FORMAT
        );
        ReflectionTestUtils.setField(transcriptImport, "id", 20L);
        when(importRepository.findById(20L)).thenReturn(Optional.of(transcriptImport));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        transcriptService.writeImportResult(20L, output);

        verify(syuImportScoreExcelWriter).write(transcriptImport, output);
        verifyNoInteractions(importResultExcelWriter);
    }

    @Test
    void allOrNothingModeRejectsParsedRowsWhenAnyErrorExists() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "transcript.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[] {1}
        );
        TranscriptExcelRow row = row("A-001");
        when(excelParser.parse(file)).thenReturn(new TranscriptExcelParseResult(
            2, List.of(row), List.of(new TranscriptImportRowError(3, "등급 오류"))
        ));

        assertThatThrownBy(() -> transcriptService.importExcel(2027, TranscriptImportMode.ALL_OR_NOTHING, 1L, file))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_TRANSCRIPT_FILE));
        verifyNoInteractions(studentRepository, courseRepository, importRepository);
    }

    @Test
    void rejectsManualCourseWithoutGradeOrAchievement() {
        Student student = Student.create(2027, "A-001", "학생", null, null, 2027);
        ReflectionTestUtils.setField(student, "id", 1L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        UpsertTranscriptCourseRequest request = new UpsertTranscriptCourseRequest(
            1, 1, SubjectCategory.MATH, "수학", null, null,
            null, null, null, null, new BigDecimal("3"), false, false
        );

        assertThatThrownBy(() -> transcriptService.createCourse(1L, request))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_TRANSCRIPT_FILE));
    }

    private TranscriptExcelRow row(String applicantNumber) {
        return new TranscriptExcelRow(
            2, applicantNumber, "학생", "S001", "고등학교", 2027,
            1, 1, SubjectCategory.MATH, "수학", 2, null,
            null, null, null, null, new BigDecimal("3"), false, false
        );
    }
}
