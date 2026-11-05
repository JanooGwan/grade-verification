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
            DELETE verification
            FROM verification_run verification
            JOIN student ON student.id = verification.student_id
            WHERE student.university_id = ? AND student.admission_year = ?
            """, universityId, admissionYear), Integer::intValue);
        int deletedApplications = measured(importId, "delete_applications", () -> jdbcTemplate.update("""
            DELETE application
            FROM student_application application
            JOIN student ON student.id = application.student_id
            WHERE student.university_id = ? AND student.admission_year = ?
            """, universityId, admissionYear), Integer::intValue);
        int deletedCourses = measured(importId, "delete_courses", () -> jdbcTemplate.update("""
            DELETE course
            FROM student_transcript_course course
            JOIN student ON student.id = course.student_id
            WHERE student.university_id = ? AND student.admission_year = ?
            """, universityId, admissionYear), Integer::intValue);
        int deletedAttendance = replaceAttendance ? measured(importId, "delete_attendance", () -> jdbcTemplate.update("""
            DELETE attendance
            FROM student_attendance attendance
            JOIN student ON student.id = attendance.student_id
            WHERE student.university_id = ? AND student.admission_year = ?
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
