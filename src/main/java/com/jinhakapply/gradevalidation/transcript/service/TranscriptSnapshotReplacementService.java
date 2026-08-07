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
        List<Student> existingStudents = studentRepository.findAllByUniversity_IdAndAdmissionYear(
            universityId, admissionYear
        );
        int deletedVerifications = jdbcTemplate.update("""
            DELETE verification
            FROM verification_run verification
            JOIN student ON student.id = verification.student_id
            WHERE student.university_id = ? AND student.admission_year = ?
            """, universityId, admissionYear);
        int deletedApplications = jdbcTemplate.update("""
            DELETE application
            FROM student_application application
            JOIN student ON student.id = application.student_id
            WHERE student.university_id = ? AND student.admission_year = ?
            """, universityId, admissionYear);
        int deletedCourses = jdbcTemplate.update("""
            DELETE course
            FROM student_transcript_course course
            JOIN student ON student.id = course.student_id
            WHERE student.university_id = ? AND student.admission_year = ?
            """, universityId, admissionYear);
        int deletedAttendance = replaceAttendance ? jdbcTemplate.update("""
            DELETE attendance
            FROM student_attendance attendance
            JOIN student ON student.id = attendance.student_id
            WHERE student.university_id = ? AND student.admission_year = ?
            """, universityId, admissionYear) : 0;
        return new SnapshotScope(
            existingStudents,
            deletedApplications,
            deletedCourses,
            deletedAttendance,
            deletedVerifications
        );
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
