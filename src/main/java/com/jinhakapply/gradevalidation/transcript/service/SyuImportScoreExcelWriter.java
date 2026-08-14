package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus.PUBLISHED;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreResult;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot.Attendance;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot.SchoolViolenceAction;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.admission.service.GuidebookQuantitativeScoreCalculator;
import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse.CourseCalculation;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptPreviewResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SyuImportScoreExcelWriter {
    private static final String COMMON_RULE_KEY = "학교장추천|일반학과(부)";
    private static final String COMMON_RESULT_SHEET_NAME = "지원자별 환산 결과";
    private static final String SCENARIO_RESULT_SHEET_NAME = "전형별 환산 결과";
    private static final String[] COMMON_RESULT_HEADERS = {
        "수험번호", "전체 과목수", "환산 가능 과목수", "반영 과목수",
        "환산점수×이수단위 합", "반영 이수단위 합",
        "1-1 학기", "1-2 학기", "2-1 학기", "2-2 학기", "3-1 학기", "3-2 학기",
        "최종 교과 성적"
    };
    private static final String[] SCENARIO_RESULT_HEADERS = {
        "수험번호", "전형명", "모집단위", "산출 상태",
        "전체 과목수", "환산 가능 과목수", "반영 과목수",
        "환산점수×이수단위 합", "반영 이수단위 합",
        "1-1 학기", "1-2 학기", "2-1 학기", "2-2 학기", "3-1 학기", "3-2 학기",
        "교과 기준점수(100점)", "교과 반영점수", "환산 결석일수", "출결 반영점수",
        "학교폭력 감점", "현재 산출점수", "전형 최종점수", "전형 총점 만점",
        "추가입력·미산출 요소", "경고·오류", "규칙 버전"
    };
    private static final CalculateApplicationScoreRequest EMPTY_ADDITIONAL_SCORES =
        new CalculateApplicationScoreRequest(null, null, null);

    private final JdbcTemplate jdbcTemplate;
    private final EvaluationRuleRepository ruleRepository;
    private final EvaluationService evaluationService;
    private final GuidebookQuantitativeScoreCalculator applicationScoreCalculator;

    SyuImportScoreExcelWriter(
        JdbcTemplate jdbcTemplate,
        EvaluationRuleRepository ruleRepository,
        EvaluationService evaluationService,
        GuidebookQuantitativeScoreCalculator applicationScoreCalculator
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.ruleRepository = ruleRepository;
        this.evaluationService = evaluationService;
        this.applicationScoreCalculator = applicationScoreCalculator;
    }

    TranscriptPreviewResponse preview(StudentTranscriptImport transcriptImport) {
        EvaluationRule rule = loadCommonRule(transcriptImport.getUniversity().getId(), transcriptImport.getAdmissionYear());
        int scenarioCount = loadScenarioRules(
            transcriptImport.getUniversity().getId(), transcriptImport.getAdmissionYear()
        ).size();
        List<TranscriptPreviewResponse.PreviewRow> sampleRows = new ArrayList<>();
        List<TranscriptPreviewResponse.VerificationResultRow> sampleResults = new ArrayList<>();
        int[] applicationRows = {0};
        int[] courseRows = {0};
        int[] successfulApplications = {0};
        int[] failedApplications = {0};

        streamCourses(transcriptImport, courses -> {
            applicationRows[0]++;
            courseRows[0] += courses.size();
            for (Course course : courses) {
                if (sampleRows.size() >= 50) break;
                sampleRows.add(new TranscriptPreviewResponse.PreviewRow(
                    course.sourceRowNumber(), course.applicantNumber(), "미등록",
                    course.schoolYear(), course.semester(), course.subjectCategory(), course.courseName(),
                    course.grade(), course.achievement(), course.credits()
                ));
            }
            ApplicantResult result = calculate(courses, rule, emptyCommonData());
            if (result.score() == null) {
                failedApplications[0]++;
                return;
            }
            successfulApplications[0]++;
            if (sampleResults.size() < 20) {
                GradeVerificationResponse score = result.score();
                sampleResults.add(new TranscriptPreviewResponse.VerificationResultRow(
                    applicationRows[0], courses.getFirst().applicantNumber(), "미등록",
                    rule.getAdmissionType(), rule.getRecruitmentUnit(), score.finalScore(),
                    score.averageGrade(), score.includedCourseCount()
                ));
            }
        });

        return new TranscriptPreviewResponse(
            transcriptImport.getOriginalFileName(), transcriptImport.getFileSha256(),
            transcriptImport.getSourceFormat(), applicationRows[0], transcriptImport.getTotalRows(),
            courseRows[0], transcriptImport.getFailedRows(), 0, List.copyOf(sampleRows),
            new TranscriptPreviewResponse.VerificationSummary(
                applicationRows[0], successfulApplications[0], failedApplications[0], List.copyOf(sampleResults)
            ),
            List.of(),
            List.of(
                "DB 가져오기 #%d의 교과 성적을 사용했습니다.".formatted(transcriptImport.getId()),
                "화면 미리보기는 '%s × %s' 교과 규칙을 적용합니다."
                    .formatted(rule.getAdmissionType(), rule.getRecruitmentUnit()),
                transcriptImport.getAdmissionYear() == 2027
                    ? "Excel 내보내기에는 게시된 %d개 전형·모집단위 가상 시나리오와 출결점수, 추가입력 요소가 각각 구분됩니다."
                        .formatted(scenarioCount)
                    : "실제 지원 전형·모집단위 정보가 없어 공통 교과 규칙을 적용한 가상 시나리오입니다."
            )
        );
    }

    byte[] write(StudentTranscriptImport transcriptImport) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(transcriptImport, output);
        return output.toByteArray();
    }

    ScenarioVerificationSummary verifyScenarios(
        StudentTranscriptImport transcriptImport,
        List<EvaluationRule> rules,
        BiConsumer<Long, GradeVerificationResponse> resultConsumer
    ) {
        Map<String, StudentCommonEvaluationSnapshot> commonData = loadCommonData(transcriptImport);
        int[] totalScenarios = {0};
        int[] successfulScenarios = {0};
        int[] failedScenarios = {0};

        streamCourses(transcriptImport, courses -> {
            String applicantNumber = courses.getFirst().applicantNumber();
            StudentCommonEvaluationSnapshot applicantData = commonData.getOrDefault(
                applicantNumber, emptyCommonData()
            );
            for (EvaluationRule rule : rules) {
                totalScenarios[0]++;
                ApplicantResult result = calculate(courses, rule, applicantData);
                if (result.score() == null) {
                    failedScenarios[0]++;
                    continue;
                }
                resultConsumer.accept(courses.getFirst().studentId(), result.score());
                successfulScenarios[0]++;
            }
        });
        return new ScenarioVerificationSummary(
            totalScenarios[0], successfulScenarios[0], failedScenarios[0]
        );
    }

    void write(StudentTranscriptImport transcriptImport, OutputStream output) {
        List<EvaluationRule> rules = loadScenarioRules(
            transcriptImport.getUniversity().getId(), transcriptImport.getAdmissionYear()
        );
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook) {
            Styles styles = new Styles(workbook);
            if (transcriptImport.getAdmissionYear() == 2027) {
                writeScenarioWorkbook(workbook, styles, transcriptImport, rules);
            } else {
                writeCommonWorkbook(workbook, styles, transcriptImport, loadCommonRule(
                    transcriptImport.getUniversity().getId(), transcriptImport.getAdmissionYear()
                ));
            }
            workbook.write(output);
        } catch (IOException exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "삼육대 환산 결과 Excel 파일을 생성하지 못했습니다.");
        }
    }

    private void writeCommonWorkbook(
        SXSSFWorkbook workbook,
        Styles styles,
        StudentTranscriptImport transcriptImport,
        EvaluationRule rule
    ) {
        Sheet results = createResultSheet(
            workbook, styles, COMMON_RESULT_SHEET_NAME, COMMON_RESULT_HEADERS
        );
        int[] rowIndex = {3};
        streamCourses(transcriptImport, courses -> {
            ApplicantResult result = calculate(courses, rule, emptyCommonData());
            writeCommonApplicantRow(results.createRow(rowIndex[0]++), result, styles);
        });
        applyResultSheetFeatures(results, rowIndex[0], COMMON_RESULT_HEADERS.length);
    }

    private void writeScenarioWorkbook(
        SXSSFWorkbook workbook,
        Styles styles,
        StudentTranscriptImport transcriptImport,
        List<EvaluationRule> rules
    ) {
        Map<String, StudentCommonEvaluationSnapshot> commonData = loadCommonData(transcriptImport);
        Sheet results = createResultSheet(
            workbook, styles, SCENARIO_RESULT_SHEET_NAME, SCENARIO_RESULT_HEADERS
        );
        int[] rowIndex = {3};

        streamCourses(transcriptImport, courses -> {
            String applicantNumber = courses.getFirst().applicantNumber();
            StudentCommonEvaluationSnapshot applicantData = commonData.getOrDefault(
                applicantNumber, emptyCommonData()
            );
            for (EvaluationRule rule : rules) {
                ApplicantResult result = calculate(courses, rule, applicantData);
                writeScenarioApplicantRow(
                    results.createRow(rowIndex[0]++), result, rule, styles
                );
            }
        });

        applyResultSheetFeatures(results, rowIndex[0], SCENARIO_RESULT_HEADERS.length);
    }

    private EvaluationRule loadCommonRule(Long universityId, int admissionYear) {
        EvaluationRule rule = loadScenarioRules(universityId, admissionYear).stream()
            .filter(candidate -> COMMON_RULE_KEY.equals(ruleKey(candidate)))
            .findFirst()
            .orElseThrow(() -> CustomException.of(INVALID_TRANSCRIPT_FILE,
                "삼육대 " + admissionYear + "학년도 공통 교과 환산 규칙이 없습니다."));
        return rule;
    }

    List<EvaluationRule> loadScenarioRules(Long universityId, int admissionYear) {
        Map<String, EvaluationRule> latestByScenario = ruleRepository
            .findAllByUniversityIdAndAdmissionYearAndStatus(universityId, admissionYear, PUBLISHED)
            .stream()
            .collect(Collectors.toMap(
                this::ruleKey,
                Function.identity(),
                (left, right) -> left.getVersion() >= right.getVersion() ? left : right,
                LinkedHashMap::new
            ));
        List<EvaluationRule> rules = latestByScenario.values().stream()
            .sorted(Comparator
                .comparingInt((EvaluationRule rule) -> admissionTypeOrder(rule.getAdmissionType()))
                .thenComparingInt(rule -> recruitmentUnitOrder(rule.getRecruitmentUnit()))
                .thenComparing(EvaluationRule::getAdmissionType)
                .thenComparing(EvaluationRule::getRecruitmentUnit))
            .toList();
        if (rules.isEmpty()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "삼육대 " + admissionYear + "학년도 게시 교과 환산 규칙이 없습니다.");
        }
        rules.forEach(this::initializeRuleCollections);
        return rules;
    }

    private int admissionTypeOrder(String admissionType) {
        return switch (admissionType) {
            case "학교장추천" -> 1;
            case "농어촌" -> 2;
            case "서해5도" -> 3;
            case "특성화고교" -> 4;
            case "특성화고졸재직자" -> 5;
            case "예체능인재" -> 6;
            default -> 100;
        };
    }

    private int recruitmentUnitOrder(String recruitmentUnit) {
        if (recruitmentUnit.contains("일반학과")) return 1;
        if (recruitmentUnit.contains("약학")) return 2;
        if (recruitmentUnit.contains("아트앤디자인")) return 3;
        if (recruitmentUnit.contains("체육")) return 4;
        return 100;
    }

    private void initializeRuleCollections(EvaluationRule rule) {
        rule.getGradeScores().size();
        rule.getAchievementGrades().size();
        rule.getAchievementScores().size();
        rule.getLegacyAchievementGrades().size();
        rule.getSubjectPriorities().size();
    }

    private String ruleKey(EvaluationRule rule) {
        return rule.getAdmissionType() + "|" + rule.getRecruitmentUnit();
    }

    private void streamCourses(StudentTranscriptImport transcriptImport, CourseGroupConsumer consumer) {
        jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                SELECT student.id AS student_id, course.applicant_number, course.source_row_number,
                       course.school_year, course.semester,
                       course.subject_category, course.course_name, course.grade_value, course.grade_scale,
                       course.achievement, course.raw_score, course.mean_score, course.standard_deviation,
                       course.student_count, course.rank_position, course.tied_rank_count,
                       course.legacy_achievement, course.credits, course.career_subject,
                       course.professional_course
                FROM student_transcript_import_course course
                JOIN student ON student.university_id = ?
                            AND student.admission_year = ?
                            AND student.applicant_number = course.applicant_number
                WHERE course.import_id = ?
                ORDER BY course.applicant_number, course.source_row_number
                """, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchSize(Integer.MIN_VALUE);
            statement.setLong(1, transcriptImport.getUniversity().getId());
            statement.setInt(2, transcriptImport.getAdmissionYear());
            statement.setLong(3, transcriptImport.getId());
            return statement;
        }, rs -> {
            List<Course> group = new ArrayList<>();
            String applicantNumber = null;
            while (rs.next()) {
                String current = rs.getString("applicant_number");
                if (applicantNumber != null && !applicantNumber.equals(current)) {
                    consumer.accept(group);
                    group = new ArrayList<>();
                }
                applicantNumber = current;
                group.add(mapCourse(rs));
            }
            if (!group.isEmpty()) consumer.accept(group);
            return null;
        });
    }

    private Course mapCourse(ResultSet rs) throws SQLException {
        return new Course(
            rs.getLong("student_id"), rs.getString("applicant_number"), rs.getInt("source_row_number"),
            rs.getInt("school_year"), rs.getInt("semester"),
            SubjectCategory.valueOf(rs.getString("subject_category")), rs.getString("course_name"),
            nullableInteger(rs, "grade_value"), GradeScale.valueOf(rs.getString("grade_scale")),
            enumValue(AchievementLevel.class, rs.getString("achievement")), rs.getBigDecimal("raw_score"),
            rs.getBigDecimal("mean_score"), rs.getBigDecimal("standard_deviation"),
            nullableInteger(rs, "student_count"), nullableInteger(rs, "rank_position"),
            nullableInteger(rs, "tied_rank_count"), rs.getBigDecimal("credits"),
            rs.getBoolean("career_subject"), rs.getBoolean("professional_course")
        );
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private ApplicantResult calculate(
        List<Course> courses,
        EvaluationRule rule,
        StudentCommonEvaluationSnapshot commonData
    ) {
        try {
            GradeVerificationResponse score = evaluationService.verify(rule, new VerifyGradeRequest(
                rule.getId(), false, highSchoolType(rule), null,
                courses.stream().map(Course::toRequest).toList()
            ));
            try {
                ApplicationScoreResult applicationScore = applicationScoreCalculator.calculate(
                    rule, rule.getAdmissionType(), score, EMPTY_ADDITIONAL_SCORES,
                    commonDataForRule(commonData, rule)
                );
                return new ApplicantResult(courses, score, applicationScore, null);
            } catch (CustomException exception) {
                return new ApplicantResult(courses, score, null, exception.getMessage());
            }
        } catch (CustomException exception) {
            return new ApplicantResult(courses, null, null, exception.getMessage());
        }
    }

    private HighSchoolType highSchoolType(EvaluationRule rule) {
        return rule.getAdmissionType().contains("특성화고")
            ? HighSchoolType.SPECIALIZED : HighSchoolType.GENERAL;
    }

    private StudentCommonEvaluationSnapshot commonDataForRule(
        StudentCommonEvaluationSnapshot data,
        EvaluationRule rule
    ) {
        return new StudentCommonEvaluationSnapshot(
            data.educationBackground(), highSchoolType(rule), data.graduationStatus(), data.graduationYear(),
            data.gedAverageScore(), data.gedSubjectScores(), data.legacyGradeSummaries(),
            data.attendance(), data.schoolViolenceActions()
        );
    }

    private void writeCommonApplicantRow(Row row, ApplicantResult result, Styles styles) {
        GradeVerificationResponse score = result.score();
        Object[] values = {
            result.courses().getFirst().applicantNumber(),
            result.courses().size(),
            gradableCount(result.courses()),
            score == null ? null : score.includedCourseCount(),
            score == null ? null : score.calculationSummary().convertedScoreTimesCreditsSum(),
            score == null ? null : score.calculationSummary().totalIncludedCredits(),
            semesterIntermediate(score, 1, 1),
            semesterIntermediate(score, 1, 2),
            semesterIntermediate(score, 2, 1),
            semesterIntermediate(score, 2, 2),
            semesterIntermediate(score, 3, 1),
            semesterIntermediate(score, 3, 2),
            score == null ? null : score.baseScore()
        };
        writeRow(row, values, styles);
        row.setHeightInPoints(24);
    }

    private void writeScenarioApplicantRow(
        Row row,
        ApplicantResult result,
        EvaluationRule rule,
        Styles styles
    ) {
        GradeVerificationResponse score = result.score();
        ApplicationScoreResult applicationScore = result.applicationScore();
        Object[] values = {
            result.courses().getFirst().applicantNumber(),
            rule.getAdmissionType(),
            rule.getRecruitmentUnit(),
            statusLabel(result),
            result.courses().size(),
            gradableCount(result.courses()),
            score == null ? null : score.includedCourseCount(),
            score == null ? null : score.calculationSummary().convertedScoreTimesCreditsSum(),
            score == null ? null : score.calculationSummary().totalIncludedCredits(),
            semesterIntermediate(score, 1, 1),
            semesterIntermediate(score, 1, 2),
            semesterIntermediate(score, 2, 1),
            semesterIntermediate(score, 2, 2),
            semesterIntermediate(score, 3, 1),
            semesterIntermediate(score, 3, 2),
            score == null ? null : score.baseScore(),
            applicationScore == null ? null : applicationScore.academicScore(),
            applicationScore == null ? null : applicationScore.equivalentAbsenceDays(),
            applicationScore == null ? null : applicationScore.attendanceScore(),
            applicationScore == null ? null : applicationScore.schoolViolenceDeduction(),
            applicationScore == null ? null : applicationScore.scoreAfterDeduction(),
            applicationScore == null ? null : applicationScore.finalScore(),
            applicationScore == null ? null : applicationScore.maximumTotalScore(),
            applicationScore == null ? pendingDescription(rule)
                : String.join(", ", applicationScore.pendingComponents()),
            warningAndError(result),
            rule.getVersion()
        };
        writeRow(row, values, styles);
        row.setHeightInPoints(24);
    }

    private String statusLabel(ApplicantResult result) {
        if (result.score() == null) return "교과 산출 실패";
        if (result.applicationScore() == null) return "정량 산출 실패";
        return switch (result.applicationScore().status()) {
            case COMPLETE -> "산출 완료";
            case QUALITATIVE_PENDING -> "외부점수 입력 필요";
            case INELIGIBLE -> "지원자격 확인 필요";
        };
    }

    private String warningAndError(ApplicantResult result) {
        List<String> messages = new ArrayList<>();
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            messages.add(result.errorMessage());
        }
        if (result.score() != null) messages.addAll(result.score().warnings());
        if (result.applicationScore() != null) {
            messages.addAll(result.applicationScore().ineligibilityReasons());
            messages.addAll(result.applicationScore().warnings());
        }
        return String.join(" / ", messages);
    }

    private BigDecimal semesterIntermediate(GradeVerificationResponse score, int schoolYear, int semester) {
        if (score == null) return null;
        BigDecimal weightedScoreSum = BigDecimal.ZERO;
        BigDecimal appliedWeightSum = BigDecimal.ZERO;
        for (CourseCalculation calculation : score.calculations()) {
            if (!calculation.included()
                || calculation.schoolYear() != schoolYear
                || calculation.semester() != semester) {
                continue;
            }
            weightedScoreSum = weightedScoreSum.add(calculation.weightedScore());
            appliedWeightSum = appliedWeightSum.add(calculation.appliedWeight());
        }
        return semesterIntermediate(
            weightedScoreSum,
            appliedWeightSum,
            score.calculationSummary().intermediateScale(),
            score.calculationSummary().intermediateRounding()
        );
    }

    static BigDecimal semesterIntermediate(
        BigDecimal weightedScoreSum,
        BigDecimal appliedWeightSum,
        int scale,
        RoundingMode roundingMode
    ) {
        if (appliedWeightSum.signum() == 0) return null;
        return weightedScoreSum.divide(appliedWeightSum, scale, roundingMode);
    }

    private long gradableCount(List<Course> courses) {
        return courses.stream().filter(course ->
            course.grade() != null || course.achievement() != null
        ).count();
    }

    private Map<String, StudentCommonEvaluationSnapshot> loadCommonData(
        StudentTranscriptImport transcriptImport
    ) {
        Map<String, CommonDataBuilder> builders = new HashMap<>();
        jdbcTemplate.query("""
            SELECT student.applicant_number, attendance.school_year,
                   attendance.unexcused_absence_days, attendance.unexcused_tardy_count,
                   attendance.unexcused_early_leave_count, attendance.unexcused_class_absence_count
            FROM student_attendance attendance
            JOIN student student ON student.id = attendance.student_id
            WHERE student.university_id = ? AND student.admission_year = ?
            ORDER BY student.applicant_number, attendance.school_year
            """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> builders.computeIfAbsent(
                rs.getString("applicant_number"), ignored -> new CommonDataBuilder()
            ).attendance().add(new Attendance(
                rs.getInt("school_year"), rs.getInt("unexcused_absence_days"),
                rs.getInt("unexcused_tardy_count"), rs.getInt("unexcused_early_leave_count"),
                rs.getInt("unexcused_class_absence_count")
            )), transcriptImport.getUniversity().getId(), transcriptImport.getAdmissionYear());

        jdbcTemplate.query("""
            SELECT student.applicant_number, action.school_year, action.action_number,
                   action.action_date, action.active, action.note
            FROM student_school_violence_action action
            JOIN student student ON student.id = action.student_id
            WHERE student.university_id = ? AND student.admission_year = ?
            ORDER BY student.applicant_number, action.action_number
            """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> builders.computeIfAbsent(
                rs.getString("applicant_number"), ignored -> new CommonDataBuilder()
            ).schoolViolenceActions().add(new SchoolViolenceAction(
                nullableInteger(rs, "school_year"), rs.getInt("action_number"),
                rs.getDate("action_date") == null ? null : rs.getDate("action_date").toLocalDate(),
                rs.getBoolean("active"), rs.getString("note")
            )), transcriptImport.getUniversity().getId(), transcriptImport.getAdmissionYear());

        return builders.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> entry.getValue().build()
        ));
    }

    private StudentCommonEvaluationSnapshot emptyCommonData() {
        return new StudentCommonEvaluationSnapshot(
            EducationBackground.DOMESTIC_HIGH_SCHOOL, HighSchoolType.GENERAL,
            GraduationStatus.EXPECTED_GRADUATE, null, null,
            List.of(), List.of(), List.of(), List.of()
        );
    }

    private String pendingDescription(EvaluationRule rule) {
        if (isAthleticTalent(rule)) return "1단계 수상실적 600점, 2단계 면접 200점";
        if (isPracticalTrack(rule)) {
            return rule.getRecruitmentUnit().contains("아트앤디자인")
                ? "실기고사 800점" : "실기고사 600점";
        }
        return "없음";
    }

    private boolean isAthleticTalent(EvaluationRule rule) {
        return rule.getAdmissionType().equals("예체능인재")
            && rule.getRecruitmentUnit().contains("체육학과");
    }

    private boolean isPracticalTrack(EvaluationRule rule) {
        return (rule.getAdmissionType().equals("학교장추천") || rule.getAdmissionType().equals("농어촌"))
            && (rule.getRecruitmentUnit().contains("아트앤디자인")
                || rule.getRecruitmentUnit().contains("체육학과"));
    }

    private Sheet createResultSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        String sheetName,
        String[] headers
    ) {
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.setDisplayGridlines(false);
        Row title = sheet.createRow(0);
        title.setHeightInPoints(32);
        set(title.createCell(0), sheetName, styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

        Row header = sheet.createRow(2);
        header.setHeightInPoints(36);
        for (int index = 0; index < headers.length; index++) {
            set(header.createCell(index), headers[index], styles.header);
            sheet.setColumnWidth(index, Math.min(36, Math.max(14, headers[index].length() + 5)) * 256);
        }
        sheet.createFreezePane(1, 3);
        return sheet;
    }

    private void applyResultSheetFeatures(Sheet sheet, int nextRowIndex, int headerCount) {
        sheet.setAutoFilter(new CellRangeAddress(
            2, Math.max(2, nextRowIndex - 1), 0, headerCount - 1
        ));
    }

    private void writeRow(Row row, Object[] values, Styles styles) {
        for (int index = 0; index < values.length; index++) {
            CellStyle style = values[index] instanceof Number ? styles.number : styles.value;
            set(row.createCell(index), values[index], style);
        }
    }

    private void set(Cell cell, Object value, CellStyle style) {
        cell.setCellStyle(style);
        if (value instanceof BigDecimal decimal) cell.setCellValue(decimal.doubleValue());
        else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value == null ? "" : value.toString());
    }

    static String resultSheetName() {
        return SCENARIO_RESULT_SHEET_NAME;
    }

    static List<String> resultHeaders() {
        return List.of(SCENARIO_RESULT_HEADERS);
    }

    private record Course(
        Long studentId, String applicantNumber, int sourceRowNumber, int schoolYear, int semester,
        SubjectCategory subjectCategory, String courseName, Integer grade, GradeScale gradeScale,
        AchievementLevel achievement, BigDecimal rawScore, BigDecimal meanScore,
        BigDecimal standardDeviation, Integer studentCount, Integer rankPosition,
        Integer tiedRankCount, BigDecimal credits, boolean careerSubject, boolean professionalCourse
    ) {
        VerifyGradeRequest.CourseGrade toRequest() {
            return new VerifyGradeRequest.CourseGrade(
                schoolYear, semester, subjectCategory, courseName, grade, gradeScale, achievement,
                rawScore, meanScore, standardDeviation, studentCount, rankPosition, tiedRankCount,
                null, careerSubject, professionalCourse, credits
            );
        }
    }

    private record ApplicantResult(
        List<Course> courses,
        GradeVerificationResponse score,
        ApplicationScoreResult applicationScore,
        String errorMessage
    ) {}

    record ScenarioVerificationSummary(
        int totalScenarios,
        int successfulScenarios,
        int failedScenarios
    ) {}

    private static final class CommonDataBuilder {
        private final List<Attendance> attendance = new ArrayList<>();
        private final List<SchoolViolenceAction> schoolViolenceActions = new ArrayList<>();

        private List<Attendance> attendance() {
            return attendance;
        }

        private List<SchoolViolenceAction> schoolViolenceActions() {
            return schoolViolenceActions;
        }

        private StudentCommonEvaluationSnapshot build() {
            return new StudentCommonEvaluationSnapshot(
                EducationBackground.DOMESTIC_HIGH_SCHOOL, HighSchoolType.GENERAL,
                GraduationStatus.EXPECTED_GRADUATE, null, null,
                List.of(), List.of(), List.copyOf(attendance), List.copyOf(schoolViolenceActions)
            );
        }
    }

    @FunctionalInterface
    private interface CourseGroupConsumer {
        void accept(List<Course> courses);
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle header;
        private final CellStyle value;
        private final CellStyle number;

        private Styles(SXSSFWorkbook workbook) {
            title = style(workbook, IndexedColors.DARK_GREEN, IndexedColors.WHITE, true, false);
            header = style(workbook, IndexedColors.DARK_GREEN, IndexedColors.WHITE, true, true);
            value = style(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false, false);
            number = style(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false, false);
            number.setDataFormat(workbook.createDataFormat().getFormat("#,##0.####"));
        }

        private static CellStyle style(
            SXSSFWorkbook workbook, IndexedColors fill, IndexedColors fontColor, boolean bold, boolean wrap
        ) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(fill.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setWrapText(wrap);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            Font font = workbook.createFont();
            font.setBold(bold);
            font.setColor(fontColor.getIndex());
            style.setFont(font);
            return style;
        }
    }
}
