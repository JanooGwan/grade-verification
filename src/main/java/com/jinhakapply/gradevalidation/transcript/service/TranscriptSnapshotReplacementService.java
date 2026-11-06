package com.jinhakapply.gradevalidation.transcript.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TranscriptSnapshotReplacementService {

    private final StudentRepository studentRepository;
    private final JdbcTemplate jdbcTemplate;

    SnapshotScope clear(Long universityId, int admissionYear, boolean replaceAttendance) {
        return clear(universityId, admissionYear, replaceAttendance, null);
    }

    SnapshotScope clear(Long universityId, int admissionYear, boolean replaceAttendance, Long importId) {
        List<Student> existingStudents = measured(importId, "load_existing_students", () -> studentRepository.findAllByUniversity_IdAndAdmissionYear(
            universityId, admissionYear
        ), List::size);
        int deletedVerifications = measured(importId, "delete_verifications", () -> jdbcTemplate.update("""
            DELETE FROM verification_run
            WHERE student_id IN (SELECT id FROM student WHERE university_id = ? AND admission_year = ?)
            """, universityId, admissionYear), Integer::intValue);
        int deletedApplications = measured(importId, "delete_applications", () -> jdbcTemplate.update("""
            DELETE FROM student_application
            WHERE student_id IN (SELECT id FROM student WHERE university_id = ? AND admission_year = ?)
            """, universityId, admissionYear), Integer::intValue);
        int deletedCourses = measured(importId, "delete_courses", () -> jdbcTemplate.update("""
            DELETE FROM student_transcript_course
            WHERE student_id IN (SELECT id FROM student WHERE university_id = ? AND admission_year = ?)
            """, universityId, admissionYear), Integer::intValue);
        int deletedAttendance = replaceAttendance ? measured(importId, "delete_attendance", () -> jdbcTemplate.update("""
            DELETE FROM student_attendance
            WHERE student_id IN (SELECT id FROM student WHERE university_id = ? AND admission_year = ?)
            """, universityId, admissionYear), Integer::intValue) : 0;
        return new SnapshotScope(
            existingStudents,
            deletedApplications,
            deletedCourses,
            deletedAttendance,
            deletedVerifications
        );
    }

    private <T> T measured(Long importId, String operation, java.util.function.Supplier<T> action,
                           java.util.function.ToIntFunction<T> count) {
        return importId == null ? action.get() : ImportTimingLog.measure(importId, operation, action, count);
    }

    int deleteMissingStudents(Collection<Student> previousStudents, Set<String> currentApplicantNumbers) {
        List<Student> missing = previousStudents.stream()
            .filter(student -> !currentApplicantNumbers.contains(student.getApplicantNumber()))
            .toList();
        if (!missing.isEmpty()) studentRepository.deleteAllInBatch(missing);
        return missing.size();
    }

    record SnapshotScope(
        List<Student> existingStudents,
        int deletedApplications,
        int deletedCourses,
        int deletedAttendance,
        int deletedVerifications
    ) {}
}
