package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.STORED_TRANSCRIPT_DATA_NOT_FOUND;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.LegacyAchievement;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptPreviewResponse;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoredTranscriptVerificationService {

    private static final List<TranscriptImportStatus> COMPLETED_STATUSES = List.of(
        TranscriptImportStatus.COMPLETED,
        TranscriptImportStatus.COMPLETED_WITH_ERRORS
    );

    private final StudentTranscriptImportRepository importRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TranscriptBatchVerificationService batchVerificationService;
    private final TranscriptValidationExcelWriter validationExcelWriter;

    @Transactional(readOnly = true)
    public TranscriptPreviewResponse verify(Long universityId, int admissionYear) {
        StoredVerification stored = verifyStored(universityId, admissionYear);
        return response(stored);
    }

    @Transactional(readOnly = true)
    public byte[] export(Long universityId, int admissionYear) {
        StoredVerification stored = verifyStored(universityId, admissionYear);
        StudentTranscriptImport transcriptImport = stored.transcriptImport();
        return validationExcelWriter.write(
            transcriptImport.getOriginalFileName(),
            transcriptImport.getSourceFormat(),
            stored.applications().size(),
            transcriptImport.getTotalRows(),
            List.of(),
            stored.courses(),
            List.of(),
            warnings(transcriptImport),
            stored.verification()
        );
    }

    private StoredVerification verifyStored(Long universityId, int admissionYear) {
        importRepository.findTopByUniversity_IdAndAdmissionYearOrderByCreatedAtDesc(universityId, admissionYear)
            .filter(latest -> latest.getStatus() == TranscriptImportStatus.QUEUED
                || latest.getStatus() == TranscriptImportStatus.PROCESSING)
            .ifPresent(latest -> {
                throw CustomException.of(
                    INVALID_TRANSCRIPT_FILE,
                    "최신 DB 저장 작업 #%d이 아직 처리 중입니다. 완료 후 성적검증을 실행해 주세요."
                        .formatted(latest.getId())
                );
            });
        StudentTranscriptImport transcriptImport = importRepository
            .findTopByUniversity_IdAndAdmissionYearAndStatusInOrderByCreatedAtDesc(
                universityId, admissionYear, COMPLETED_STATUSES
            )
            .orElseThrow(() -> CustomException.of(
                STORED_TRANSCRIPT_DATA_NOT_FOUND,
                "선택한 대학교·모집연도의 DB 저장 데이터를 먼저 업로드해 주세요."
            ));
        List<TransferApplicationRow> applications = loadApplications(universityId, admissionYear);
        List<TranscriptExcelRow> courses = loadCourses(universityId, admissionYear);
        if (applications.isEmpty() || courses.isEmpty()) {
            throw CustomException.of(
                STORED_TRANSCRIPT_DATA_NOT_FOUND,
                "최신 DB 저장본에 지원정보 또는 과목 성적이 없습니다. 완전한 파일을 다시 업로드해 주세요."
            );
        }
        TranscriptBatchVerificationResult verification = batchVerificationService.verify(
            universityId,
            admissionYear,
            applications,
            courses,
            loadSchoolInfo(universityId, admissionYear)
        );
        requireMatchedVerificationRule(verification);
        return new StoredVerification(transcriptImport, applications, courses, verification);
    }

    private List<TransferApplicationRow> loadApplications(Long universityId, int admissionYear) {
        return jdbcTemplate.query("""
            SELECT application.id,
                   student.admission_year,
                   student.applicant_number,
                   student.graduation_year,
                   track.name AS admission_track_name,
                   unit.code AS recruitment_unit_code,
                   unit.name AS recruitment_unit_name
            FROM student_application application
            JOIN student ON student.id = application.student_id
            JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
            JOIN admission_track track ON track.id = unit.admission_track_id
            WHERE student.university_id = ?
              AND student.admission_year = ?
              AND track.university_id = ?
              AND track.admission_year = ?
            ORDER BY application.id
            """, (resultSet, rowNumber) -> new TransferApplicationRow(
                rowNumber + 1,
                resultSet.getInt("admission_year"),
                resultSet.getString("applicant_number"),
                null,
                resultSet.getString("admission_track_name"),
                resultSet.getString("recruitment_unit_code"),
                resultSet.getString("recruitment_unit_name"),
                nullableInteger(resultSet, "graduation_year")
            ), universityId, admissionYear, universityId, admissionYear);
    }

    private List<TranscriptExcelRow> loadCourses(Long universityId, int admissionYear) {
        return jdbcTemplate.query("""
            SELECT course.source_row_number,
                   student.applicant_number,
                   student.name AS student_name,
                   student.high_school_code,
                   student.high_school_name,
                   student.graduation_year,
                   course.school_year,
                   course.semester,
                   course.subject_category,
                   course.course_name,
                   course.grade_value,
                   course.grade_scale,
                   course.achievement,
                   course.raw_score,
                   course.mean_score,
                   course.standard_deviation,
                   course.student_count,
                   course.rank_position,
                   course.tied_rank_count,
                   course.legacy_achievement,
                   course.credits,
                   course.career_subject,
                   course.professional_course
            FROM student_transcript_course course
            JOIN student ON student.id = course.student_id
            WHERE student.university_id = ?
              AND student.admission_year = ?
            ORDER BY course.source_row_number, course.id
            """, (resultSet, ignored) -> new TranscriptExcelRow(
                resultSet.getInt("source_row_number"),
                resultSet.getString("applicant_number"),
                resultSet.getString("student_name"),
                resultSet.getString("high_school_code"),
                resultSet.getString("high_school_name"),
                nullableInteger(resultSet, "graduation_year"),
                resultSet.getInt("school_year"),
                resultSet.getInt("semester"),
                SubjectCategory.valueOf(resultSet.getString("subject_category")),
                resultSet.getString("course_name"),
                nullableInteger(resultSet, "grade_value"),
                GradeScale.valueOf(resultSet.getString("grade_scale")),
                nullableEnum(resultSet.getString("achievement"), AchievementLevel.class),
                resultSet.getBigDecimal("raw_score"),
                resultSet.getBigDecimal("mean_score"),
                resultSet.getBigDecimal("standard_deviation"),
                nullableInteger(resultSet, "student_count"),
                nullableInteger(resultSet, "rank_position"),
                nullableInteger(resultSet, "tied_rank_count"),
                nullableEnum(resultSet.getString("legacy_achievement"), LegacyAchievement.class),
                resultSet.getBigDecimal("credits"),
                resultSet.getBoolean("career_subject"),
                resultSet.getBoolean("professional_course")
            ), universityId, admissionYear);
    }

    private Map<String, ApplicantSchoolInfoRow> loadSchoolInfo(Long universityId, int admissionYear) {
        Map<String, ApplicantSchoolInfoRow> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
            SELECT DISTINCT student.applicant_number,
                            student.graduation_year,
                            student.high_school_code,
                            student.high_school_name,
                            student.education_background,
                            student.high_school_type,
                            student.applicant_high_school_category_code
            FROM student
            JOIN student_application application ON application.student_id = student.id
            WHERE student.university_id = ?
              AND student.admission_year = ?
            ORDER BY student.applicant_number
            """, resultSet -> {
                EducationBackground educationBackground = EducationBackground.valueOf(
                    resultSet.getString("education_background")
                );
                HighSchoolType highSchoolType = HighSchoolType.valueOf(resultSet.getString("high_school_type"));
                String categoryCode = resultSet.getString("applicant_high_school_category_code");
                if (educationBackground == EducationBackground.DOMESTIC_HIGH_SCHOOL
                    && highSchoolType == HighSchoolType.GENERAL
                    && (categoryCode == null || categoryCode.isBlank())) {
                    return;
                }
                String applicantNumber = resultSet.getString("applicant_number");
                result.put(applicantNumber, new ApplicantSchoolInfoRow(
                    0,
                    admissionYear,
                    applicantNumber,
                    nullableInteger(resultSet, "graduation_year"),
                    resultSet.getString("high_school_code"),
                    resultSet.getString("high_school_name"),
                    null,
                    null,
                    null,
                    categoryCode,
                    educationBackground,
                    highSchoolType
                ));
            }, universityId, admissionYear);
        return Map.copyOf(result);
    }

    private TranscriptPreviewResponse response(StoredVerification stored) {
        StudentTranscriptImport transcriptImport = stored.transcriptImport();
        TranscriptBatchVerificationResult verification = stored.verification();
        return new TranscriptPreviewResponse(
            transcriptImport.getOriginalFileName(),
            transcriptImport.getFileSha256(),
            transcriptImport.getSourceFormat(),
            stored.applications().size(),
            transcriptImport.getTotalRows(),
            stored.courses().size(),
            transcriptImport.getFailedRows(),
            0,
            stored.courses().stream().limit(50).map(row -> new TranscriptPreviewResponse.PreviewRow(
                row.rowNumber(), row.applicantNumber(), row.studentName(), row.schoolYear(), row.semester(),
                row.subjectCategory(), row.courseName(), row.grade(), row.achievement(), row.credits()
            )).toList(),
            new TranscriptPreviewResponse.VerificationSummary(
                stored.applications().size(),
                verification.successes().size(),
                verification.failures().size(),
                verification.successes().stream().limit(20).map(success ->
                    new TranscriptPreviewResponse.VerificationResultRow(
                        success.application().rowNumber(),
                        success.application().applicantNumber(),
                        success.studentName(),
                        success.application().admissionTrackName(),
                        success.application().recruitmentUnitName(),
                        success.verification().finalScore(),
                        success.verification().averageGrade(),
                        success.verification().includedCourseCount()
                    )
                ).toList()
            ),
            List.of(),
            warnings(transcriptImport)
        );
    }

    private List<String> warnings(StudentTranscriptImport transcriptImport) {
        return List.of(
            "DB 가져오기 #%d에 저장된 데이터만 사용해 검증했습니다.".formatted(transcriptImport.getId())
        );
    }

    private void requireMatchedVerificationRule(TranscriptBatchVerificationResult verification) {
        if (!verification.failures().isEmpty()
            && verification.successes().isEmpty()
            && verification.failures().stream().allMatch(failure -> "RULE_NOT_FOUND".equals(failure.code()))) {
            throw CustomException.of(
                INVALID_TRANSCRIPT_FILE,
                "선택한 대학교·모집연도에 DB 지원정보와 맞는 게시 규칙이 없습니다."
            );
        }
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static <T extends Enum<T>> T nullableEnum(String value, Class<T> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private record StoredVerification(
        StudentTranscriptImport transcriptImport,
        List<TransferApplicationRow> applications,
        List<TranscriptExcelRow> courses,
        TranscriptBatchVerificationResult verification
    ) {}
}
