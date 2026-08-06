package com.jinhakapply.gradevalidation.transcript.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.StudentAttendance;
import com.jinhakapply.gradevalidation.transcript.domain.StudentSchoolViolenceAction;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptCourse;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.LegacyAchievement;
import com.jinhakapply.gradevalidation.transcript.domain.StudentGedSubjectScore;
import com.jinhakapply.gradevalidation.transcript.domain.StudentLegacyGradeSummary;
import com.jinhakapply.gradevalidation.transcript.domain.GedSubjectType;
import com.jinhakapply.gradevalidation.transcript.domain.LegacySummaryType;

public record StudentTranscriptResponse(
    Long studentId,
    Long universityId,
    String universityName,
    int admissionYear,
    String applicantNumber,
    String name,
    String highSchoolCode,
    String highSchoolName,
    Integer graduationYear,
    EducationBackground educationBackground,
    HighSchoolType highSchoolType,
    GraduationStatus graduationStatus,
    BigDecimal gedAverageScore,
    List<GedSubjectScoreResponse> gedSubjectScores,
    List<LegacyGradeSummaryResponse> legacyGradeSummaries,
    List<AttendanceResponse> attendance,
    List<SchoolViolenceActionResponse> schoolViolenceActions,
    List<CourseResponse> courses,
    List<String> dataQualityWarnings
) {
    public static StudentTranscriptResponse of(
        Student student,
        List<StudentGedSubjectScore> gedSubjectScores,
        List<StudentLegacyGradeSummary> legacyGradeSummaries,
        List<StudentAttendance> attendance,
        List<StudentSchoolViolenceAction> schoolViolenceActions,
        List<StudentTranscriptCourse> courses
    ) {
        return new StudentTranscriptResponse(
            student.getId(),
            student.getUniversity().getId(),
            student.getUniversity().getName(),
            student.getAdmissionYear(),
            student.getApplicantNumber(),
            student.getName(),
            student.getHighSchoolCode(),
            student.getHighSchoolName(),
            student.getGraduationYear(),
            student.getEducationBackground(),
            student.getHighSchoolType(),
            student.getGraduationStatus(),
            student.getGedAverageScore(),
            gedSubjectScores.stream().map(GedSubjectScoreResponse::from).toList(),
            legacyGradeSummaries.stream().map(LegacyGradeSummaryResponse::from).toList(),
            attendance.stream().map(AttendanceResponse::from).toList(),
            schoolViolenceActions.stream().map(SchoolViolenceActionResponse::from).toList(),
            courses.stream().map(CourseResponse::from).toList(),
            qualityWarnings(courses)
        );
    }

    public record GedSubjectScoreResponse(Long id, GedSubjectType subjectType, String subjectName, BigDecimal score) {
        static GedSubjectScoreResponse from(StudentGedSubjectScore value) {
            return new GedSubjectScoreResponse(value.getId(), value.getSubjectType(), value.getSubjectName(), value.getScore());
        }
    }

    public record LegacyGradeSummaryResponse(Long id, LegacySummaryType summaryType, int schoolYear,
        Integer semester, int rankPosition, Integer tiedRankCount, int cohortSize, BigDecimal credits) {
        static LegacyGradeSummaryResponse from(StudentLegacyGradeSummary value) {
            return new LegacyGradeSummaryResponse(value.getId(), value.getSummaryType(), value.getSchoolYear(),
                value.getSemester(), value.getRankPosition(), value.getTiedRankCount(), value.getCohortSize(),
                value.getCredits());
        }
    }

    public record AttendanceResponse(
        int schoolYear,
        int unexcusedAbsenceDays,
        int unexcusedTardyCount,
        int unexcusedEarlyLeaveCount,
        int unexcusedClassAbsenceCount
    ) {
        public static AttendanceResponse from(StudentAttendance attendance) {
            return new AttendanceResponse(
                attendance.getSchoolYear(), attendance.getUnexcusedAbsenceDays(),
                attendance.getUnexcusedTardyCount(), attendance.getUnexcusedEarlyLeaveCount(),
                attendance.getUnexcusedClassAbsenceCount()
            );
        }
    }

    public record SchoolViolenceActionResponse(
        Long id,
        Integer schoolYear,
        int actionNumber,
        LocalDate actionDate,
        boolean active,
        String note
    ) {
        public static SchoolViolenceActionResponse from(StudentSchoolViolenceAction action) {
            return new SchoolViolenceActionResponse(
                action.getId(), action.getSchoolYear(), action.getActionNumber(), action.getActionDate(),
                action.isActive(), action.getNote()
            );
        }
    }

    private static List<String> qualityWarnings(List<StudentTranscriptCourse> courses) {
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>();
        if (courses.isEmpty()) warnings.add("등록된 학생부 과목이 없습니다.");
        for (int year = 1; year <= 3; year++) {
            for (int semester = 1; semester <= (year == 3 ? 1 : 2); semester++) {
                int targetYear = year;
                int targetSemester = semester;
                if (courses.stream().noneMatch(course -> course.getSchoolYear() == targetYear
                    && course.getSemester() == targetSemester)) {
                    warnings.add("%d학년 %d학기 성적이 없습니다.".formatted(year, semester));
                }
            }
        }
        long missingZScore = courses.stream().filter(course -> course.isCareerSubject()
            && course.getGrade() == null
            && (course.getRawScore() == null || course.getMeanScore() == null || course.getStandardDeviation() == null))
            .count();
        if (missingZScore > 0) warnings.add("Z점수 환산 정보가 부족한 진로선택 과목이 %d개 있습니다.".formatted(missingZScore));
        return List.copyOf(warnings);
    }

    public record CourseResponse(
        Long id,
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName,
        Integer grade,
        GradeScale gradeScale,
        AchievementLevel achievement,
        BigDecimal rawScore,
        BigDecimal meanScore,
        BigDecimal standardDeviation,
        Integer studentCount,
        Integer rankPosition,
        Integer tiedRankCount,
        LegacyAchievement legacyAchievement,
        BigDecimal credits,
        boolean careerSubject,
        boolean professionalCourse
    ) {
        public static CourseResponse from(StudentTranscriptCourse course) {
            return new CourseResponse(
                course.getId(),
                course.getSchoolYear(),
                course.getSemester(),
                course.getSubjectCategory(),
                course.getCourseName(),
                course.getGrade(),
                course.getGradeScale(),
                course.getAchievement(),
                course.getRawScore(),
                course.getMeanScore(),
                course.getStandardDeviation(),
                course.getStudentCount(),
                course.getRankPosition(),
                course.getTiedRankCount(),
                course.getLegacyAchievement(),
                course.getCredits(),
                course.isCareerSubject(),
                course.isProfessionalCourse()
            );
        }
    }
}
