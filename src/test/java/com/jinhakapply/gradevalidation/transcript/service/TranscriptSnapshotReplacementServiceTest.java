package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class TranscriptSnapshotReplacementServiceTest {

    @Test
    void clearsTheUniversityYearSnapshotAndDeletesApplicantsMissingFromTheNewFile() {
        StudentRepository studentRepository = org.mockito.Mockito.mock(StudentRepository.class);
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        Student retained = Student.create(2027, "A-001", "유지", null, null, 2027);
        Student removed = Student.create(2027, "A-002", "삭제", null, null, 2027);
        when(studentRepository.findAllByUniversity_IdAndAdmissionYear(4L, 2027))
            .thenReturn(List.of(retained, removed));
        when(jdbcTemplate.update(anyString(), eq(4L), eq(2027))).thenReturn(3, 5, 7);
        TranscriptSnapshotReplacementService service = new TranscriptSnapshotReplacementService(
            studentRepository, jdbcTemplate
        );

        TranscriptSnapshotReplacementService.SnapshotScope scope = service.clear(4L, 2027, false);
        int deletedStudents = service.deleteMissingStudents(scope.existingStudents(), Set.of("A-001"));

        assertThat(scope.deletedVerifications()).isEqualTo(3);
        assertThat(scope.deletedApplications()).isEqualTo(5);
        assertThat(scope.deletedCourses()).isEqualTo(7);
        assertThat(deletedStudents).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Student>> deleted = ArgumentCaptor.forClass(List.class);
        verify(studentRepository).deleteAllInBatch(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(removed);
    }
}
