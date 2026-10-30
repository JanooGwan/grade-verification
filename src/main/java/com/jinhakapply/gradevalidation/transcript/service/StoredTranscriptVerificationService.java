package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.STORED_TRANSCRIPT_DATA_NOT_FOUND;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository;
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
import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationPersistenceResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationProgress;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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
    private final SyuImportScoreExcelWriter syuImportScoreExcelWriter;
    private final SyuScenarioVerificationPersistenceService syuPersistenceService;
    private final BatchVerificationRunRepository batchVerificationRunRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TranscriptPreviewResponse verify(Long universityId, int admissionYear) {
        StudentTranscriptImport transcriptImport = loadLatestCompletedImport(universityId, admissionYear);
        if (isSyuSource(transcriptImport)) {
            return syuImportScoreExcelWriter.preview(transcriptImport);
        }
        StoredVerification stored = verifyStored(universityId, admissionYear);
        return response(stored);
    }

    @Transactional(readOnly = true)
    public byte[] export(Long universityId, int admissionYear) {
        StudentTranscriptImport latestImport = loadLatestCompletedImport(universityId, admissionYear);
        if (isSyuSource(latestImport)) {
            return syuImportScoreExcelWriter.write(latestImport);
        }
        StoredVerification stored = verifyStored(universityId, admissionYear);
        StudentTranscriptImport transcriptImport = stored.transcriptImport();
        return validationExcelWriter.write(
            transcriptImport.getOriginalFileName(),
            transcriptImport.getSourceFormat(),
            transcriptImport.getUniversity().getName(),
            stored.applications().size(),
            transcriptImport.getTotalRows(),
            List.of(),
            stored.courses(),
            List.of(),
            warnings(transcriptImport),
            stored.verification()
        );
    }

    @Transactional(readOnly = true)
    public void writeExport(Long universityId, int admissionYear, OutputStream output) throws IOException {
        StudentTranscriptImport latestImport = loadLatestCompletedImport(universityId, admissionYear);
        if (isSyuSource(latestImport)) {
            output.write(syuImportScoreExcelWriter.write(latestImport));
            return;
        }
        StoredVerification stored = verifyStored(universityId, admissionYear);
        StudentTranscriptImport transcriptImport = stored.transcriptImport();
        validationExcelWriter.write(
            transcriptImport.getOriginalFileName(),
            transcriptImport.getSourceFormat(),
            transcriptImport.getUniversity().getName(),
            stored.applications().size(),
            transcriptImport.getTotalRows(),
            List.of(),
            stored.courses(),
            List.of(),
            warnings(transcriptImport),
            stored.verification(),
            output
        );
    }

    @Transactional
    public StoredVerificationPersistenceResponse persist(Long universityId, int admissionYear) {
        return persist(universityId, admissionYear, ignored -> { });
    }

    @Transactional
    public StoredVerificationPersistenceResponse persist(Long universityId, int admissionYear,
        Consumer<StoredVerificationProgress> progress) {
        return persist(universityId, admissionYear, progress, new OperationTimings());
    }

    @Transactional
    public StoredVerificationPersistenceResponse persist(Long universityId, int admissionYear,
        Consumer<StoredVerificationProgress> progress, OperationTimings timings) {
        try (var ignored = timings.start("service_body")) {
            return persistMeasured(universityId, admissionYear, progress, timings);
        }
    }

    private StoredVerificationPersistenceResponse persistMeasured(Long universityId, int admissionYear,
        Consumer<StoredVerificationProgress> progress, OperationTimings timings) {
        progress.accept(new StoredVerificationProgress("LOADING_INPUTS", 0, 0, 0));
        StudentTranscriptImport latestImport = timings.measure("input_load", () -> loadLatestCompletedImport(universityId, admissionYear));
        if (isSyuSource(latestImport)) {
            progress.accept(new StoredVerificationProgress("VERIFYING_SCENARIOS", 0, 0, 0));
            return timings.measure("syu_scenario_inclusive", () -> syuPersistenceService.persist(
                latestImport,
                syuImportScoreExcelWriter.loadScenarioRules(universityId, admissionYear)
            ));
        }
        StoredInput stored = timings.measure("input_load", () -> loadStoredInput(universityId, admissionYear));
        Map<String, MjcArchiveApplicant> archiveInputs = timings.measure("input_load", () -> loadArchiveInputs(stored.transcriptImport()));
        Long sourceImportId = stored.transcriptImport().getId();
        int totalApplications = stored.applications().size();
        progress.accept(new StoredVerificationProgress("PREPARING_SAVE", totalApplications, 0, 0));
        int replacedResults = timings.measure("previous_result_delete", () -> batchVerificationRunRepository.deleteAllBySourceImportId(sourceImportId));
        var buffer = new StoredVerificationResultBuffer(batchVerificationRunRepository, sourceImportId,
            saved -> progress.accept(new StoredVerificationProgress("VERIFYING_AND_SAVING", totalApplications, saved, 0)));
        progress.accept(new StoredVerificationProgress("VERIFYING_AND_SAVING", totalApplications, 0, 0));
        TranscriptBatchVerificationResult verification = timings.measure("evaluation_loop_inclusive", () -> batchVerificationService.verifyForPersistence(
            universityId,
            admissionYear,
            stored.applications(),
            stored.courses(),
            stored.schoolInfoByApplicant(),
            archiveInputs,
            (application, result) -> {
                String json = timings.measure("result_json", () -> objectMapper.writeValueAsString(result));
                try (var ignored = timings.start("result_buffer_and_db_write")) {
                    buffer.add(application.applicationId(), result, json);
                }
            }
        ));
        requireMatchedVerificationRule(verification);
        try (var ignored = timings.start("final_db_flush")) { buffer.flush(); }
        progress.accept(new StoredVerificationProgress("FINALIZING", totalApplications,
            buffer.savedResults(), verification.failures().size()));
        try (var ignored = timings.start("finalize")) {
            if (MjcSourceArchiveExtractor.isCurrentSource(stored.transcriptImport().getSourceFormat())) {
                jdbcTemplate.update("UPDATE student_mjc_archive_applicant SET verification_code=NULL, verification_reason=NULL WHERE import_id=?",
                    sourceImportId);
                jdbcTemplate.batchUpdate("""
                        UPDATE student_mjc_archive_applicant SET verification_code=?, verification_reason=?
                        WHERE import_id=? AND applicant_number=?
                        """, verification.failures(), 200, (statement, failure) -> {
                            statement.setString(1, failure.code());
                            statement.setString(2, failure.reason());
                            statement.setLong(3, sourceImportId);
                            statement.setString(4, failure.application().applicantNumber());
                        });
            }
        }
        progress.accept(new StoredVerificationProgress("COMMITTING", totalApplications,
            buffer.savedResults(), verification.failures().size()));
        return new StoredVerificationPersistenceResponse(
            sourceImportId,
            stored.applications().size(),
            buffer.savedResults(),
            verification.failures().size(),
            replacedResults,
            LocalDateTime.now()
        );
    }

    private StoredVerification verifyStored(Long universityId, int admissionYear) {
        StoredInput stored = loadStoredInput(universityId, admissionYear);
        TranscriptBatchVerificationResult verification = batchVerificationService.verify(
            universityId,
            admissionYear,
            stored.applications(),
            stored.courses(),
            stored.schoolInfoByApplicant(),
            loadArchiveInputs(stored.transcriptImport()),
            (application, result) -> { }
        );
        requireMatchedVerificationRule(verification);
        return new StoredVerification(
            stored.transcriptImport(), stored.applications(), stored.courses(), verification
        );
    }

    private StoredInput loadStoredInput(Long universityId, int admissionYear) {
        StudentTranscriptImport transcriptImport = loadLatestCompletedImport(universityId, admissionYear);
        List<TransferApplicationRow> applications = loadApplications(universityId, admissionYear);
        List<TranscriptExcelRow> courses = loadCourses(universityId, admissionYear);
        if (applications.isEmpty() || (courses.isEmpty()
            && !MjcSourceArchiveExtractor.isCurrentSource(transcriptImport.getSourceFormat()))) {
            throw CustomException.of(
                STORED_TRANSCRIPT_DATA_NOT_FOUND,
                "최신 DB 저장본에 지원정보 또는 과목 성적이 없습니다. 완전한 파일을 다시 업로드해 주세요."
            );
        }
        return new StoredInput(
            transcriptImport,
            applications,
            courses,
            loadSchoolInfo(universityId, admissionYear)
        );
    }

    private StudentTranscriptImport loadLatestCompletedImport(Long universityId, int admissionYear) {
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
        return importRepository
            .findTopByUniversity_IdAndAdmissionYearAndStatusInOrderByCreatedAtDesc(
                universityId, admissionYear, COMPLETED_STATUSES
            )
            .orElseThrow(() -> CustomException.of(
                STORED_TRANSCRIPT_DATA_NOT_FOUND,
                "선택한 대학교·모집연도의 DB 저장 데이터를 먼저 업로드해 주세요."
            ));
    }

    private boolean isSyuSource(StudentTranscriptImport transcriptImport) {
        return SyuSourceExcelStreamer.SOURCE_FORMAT.equals(transcriptImport.getSourceFormat());
    }

    private Map<String, MjcArchiveApplicant> loadArchiveInputs(StudentTranscriptImport transcriptImport) {
        if (!MjcSourceArchiveExtractor.isCurrentSource(transcriptImport.getSourceFormat())) return Map.of();
        Map<String, MjcArchiveApplicant> result = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT applicant_number, source_metadata FROM student_mjc_archive_applicant WHERE import_id=?",
            (org.springframework.jdbc.core.RowCallbackHandler) row -> result.put(row.getString("applicant_number"),
                objectMapper.readValue(row.getString("source_metadata"), MjcArchiveApplicant.class)), transcriptImport.getId());
        if (result.isEmpty()) throw CustomException.of(INVALID_TRANSCRIPT_FILE, "원자료 연결 정보가 없습니다. 원자료를 다시 업로드해 주세요.");
        return result;
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
                resultSet.getLong("id"),
                rowNumber + 1,
                resultSet.getInt("admission_year"),
                resultSet.getString("applicant_number"),
                null,
                resultSet.getString("admission_track_name"),
                resultSet.getString("recruitment_unit_code"),
                resultSet.getString("recruitment_unit_name"),
                nullableInteger(resultSet, "graduation_year"),
                null
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
                   course.professional_course,
                   course.vocational_training_semester, course.source_grade_text
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
                resultSet.getBoolean("professional_course"),
                resultSet.getBoolean("vocational_training_semester"), resultSet.getString("source_grade_text")
            ), universityId, admissionYear);
    }

    private Map<String, ApplicantSchoolInfoRow> loadSchoolInfo(Long universityId, int admissionYear) {
        Map<String, ApplicantSchoolInfoRow> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
            SELECT DISTINCT student.applicant_number,
                            student.graduation_year,
                            student.graduation_date,
                            student.graduation_status,
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
                    highSchoolType,
                    nullableEnum(resultSet.getString("graduation_status"),
                        com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus.class),
                    resultSet.getDate("graduation_date") == null ? null : resultSet.getDate("graduation_date").toLocalDate()
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

    private record StoredInput(
        StudentTranscriptImport transcriptImport,
        List<TransferApplicationRow> applications,
        List<TranscriptExcelRow> courses,
        Map<String, ApplicantSchoolInfoRow> schoolInfoByApplicant
    ) {}
}
