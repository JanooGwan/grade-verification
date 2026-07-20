package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptCourse;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StudentPageResponse;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentCourseSummaryProjection;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
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
    private StudentRepository studentRepository;
    @Mock
    private StudentTranscriptCourseRepository courseRepository;
    @Mock
    private StudentTranscriptImportRepository importRepository;

    private TranscriptService transcriptService;

    @BeforeEach
    void setUp() {
        transcriptService = new TranscriptService(
            excelParser,
            studentRepository,
            courseRepository,
            importRepository
        );
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
        Student student = Student.create(2027, "A-001", "기존명", null, null, null);
        ReflectionTestUtils.setField(student, "id", 1L);

        when(excelParser.parse(file)).thenReturn(new TranscriptExcelParseResult(1, List.of(row), List.of()));
        when(studentRepository.findAllByAdmissionYearAndApplicantNumberIn(2027, java.util.Set.of("A-001")))
            .thenReturn(List.of(student));
        when(courseRepository.findAllByStudent_IdIn(List.of(1L))).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(studentRepository.findByAdmissionYearAndApplicantNumber(2027, "A-001"))
            .thenReturn(Optional.of(student));
        org.mockito.Mockito.lenient().when(courseRepository.findByStudent_IdAndSchoolYearAndSemesterAndSubjectCategoryAndCourseName(
            1L, 1, 1, SubjectCategory.MATH, "수학"
        )).thenReturn(Optional.empty());
        when(courseRepository.save(any(StudentTranscriptCourse.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(importRepository.save(any(StudentTranscriptImport.class))).thenAnswer(invocation -> {
            StudentTranscriptImport transcriptImport = invocation.getArgument(0);
            ReflectionTestUtils.setField(transcriptImport, "id", 10L);
            return transcriptImport;
        });

        TranscriptImportResponse response = transcriptService.importExcel(2027, file);

        assertThat(response.importId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(TranscriptImportStatus.COMPLETED);
        assertThat(response.updatedStudents()).isEqualTo(1);
        assertThat(response.createdCourses()).isEqualTo(1);
        assertThat(student.getName()).isEqualTo("학생");
        verify(courseRepository).save(any(StudentTranscriptCourse.class));
    }

    @Test
    void findsStudentPageWithCourseSummary() {
        Student student = Student.create(2027, "MJC27S001", "합성지원자001", "S001", "합성고", 2027);
        ReflectionTestUtils.setField(student, "id", 1L);
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "applicantNumber"));
        when(studentRepository.search(2027, "합성", pageable))
            .thenReturn(new PageImpl<>(List.of(student), pageable, 1));
        StudentCourseSummaryProjection summary = org.mockito.Mockito.mock(StudentCourseSummaryProjection.class);
        when(summary.getStudentId()).thenReturn(1L);
        when(summary.getCourseCount()).thenReturn(30L);
        when(summary.getAverageGrade()).thenReturn(3.456);
        when(courseRepository.summarizeByStudentIds(List.of(1L))).thenReturn(List.of(summary));

        StudentPageResponse response = transcriptService.findStudents(2027, " 합성 ", 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().courseCount()).isEqualTo(30);
        assertThat(response.content().getFirst().averageGrade()).isEqualByComparingTo("3.46");
    }
}
