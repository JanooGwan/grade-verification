package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus.PUBLISHED;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
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
    private static final String RESULT_SHEET_NAME = "지원자별 환산 결과";
    private static final String[] RESULT_HEADERS = {
        "수험번호", "전체 과목수", "환산 가능 과목수", "반영 과목수",
        "환산점수×이수단위 합", "반영 이수단위 합",
        "1-1 학기", "1-2 학기", "2-1 학기", "2-2 학기", "3-1 학기", "3-2 학기",
        "최종 교과 성적"
    };

    private final JdbcTemplate jdbcTemplate;
    private final EvaluationRuleRepository ruleRepository;
    private final EvaluationService evaluationService;

    SyuImportScoreExcelWriter(
        JdbcTemplate jdbcTemplate,
        EvaluationRuleRepository ruleRepository,
        EvaluationService evaluationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.ruleRepository = ruleRepository;
        this.evaluationService = evaluationService;
    }

    void write(StudentTranscriptImport transcriptImport, OutputStream output) {
        EvaluationRule rule = loadCommonRule(transcriptImport.getUniversity().getId(), transcriptImport.getAdmissionYear());
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook) {
            Styles styles = new Styles(workbook);
            Sheet results = createResultSheet(workbook, styles);
            int[] rowIndex = {3};

            streamCourses(transcriptImport, courses -> {
                ApplicantResult result = calculate(courses, rule);
                writeApplicantRow(results.createRow(rowIndex[0]++), result, styles);
            });

            results.setAutoFilter(new CellRangeAddress(
                2, Math.max(2, rowIndex[0] - 1), 0, RESULT_HEADERS.length - 1
            ));
            workbook.write(output);
        } catch (IOException exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "삼육대 환산 결과 Excel 파일을 생성하지 못했습니다.");
        }
    }

    private EvaluationRule loadCommonRule(Long universityId, int admissionYear) {
        EvaluationRule rule = ruleRepository
            .findAllByUniversityIdAndAdmissionYearAndStatus(universityId, admissionYear, PUBLISHED)
            .stream()
            .filter(candidate -> COMMON_RULE_KEY.equals(ruleKey(candidate)))
            .max(Comparator.comparingInt(EvaluationRule::getVersion))
            .orElseThrow(() -> CustomException.of(INVALID_TRANSCRIPT_FILE,
                "삼육대 " + admissionYear + "학년도 공통 교과 환산 규칙이 없습니다."));
        initializeRuleCollections(rule);
        return rule;
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
                SELECT course.applicant_number, course.source_row_number, course.school_year, course.semester,
                       course.subject_category, course.course_name, course.grade_value, course.grade_scale,
                       course.achievement, course.raw_score, course.mean_score, course.standard_deviation,
                       course.student_count, course.rank_position, course.tied_rank_count,
                       course.legacy_achievement, course.credits, course.career_subject,
                       course.professional_course
                FROM student_transcript_import_course course
                WHERE course.import_id = ?
                ORDER BY course.applicant_number, course.source_row_number
                """, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchSize(Integer.MIN_VALUE);
            statement.setLong(1, transcriptImport.getId());
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
            rs.getString("applicant_number"), rs.getInt("source_row_number"),
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

    private ApplicantResult calculate(List<Course> courses, EvaluationRule rule) {
        try {
            GradeVerificationResponse score = evaluationService.verify(rule, new VerifyGradeRequest(
                rule.getId(), false, HighSchoolType.GENERAL, null,
                courses.stream().map(Course::toRequest).toList()
            ));
            return new ApplicantResult(courses, score);
        } catch (CustomException exception) {
            return new ApplicantResult(courses, null);
        }
    }

    private void writeApplicantRow(Row row, ApplicantResult result, Styles styles) {
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

    private Sheet createResultSheet(SXSSFWorkbook workbook, Styles styles) {
        Sheet sheet = workbook.createSheet(RESULT_SHEET_NAME);
        sheet.setDisplayGridlines(false);
        Row title = sheet.createRow(0);
        title.setHeightInPoints(32);
        set(title.createCell(0), RESULT_SHEET_NAME, styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, RESULT_HEADERS.length - 1));

        Row header = sheet.createRow(2);
        header.setHeightInPoints(36);
        for (int index = 0; index < RESULT_HEADERS.length; index++) {
            set(header.createCell(index), RESULT_HEADERS[index], styles.header);
            sheet.setColumnWidth(index, Math.min(36, Math.max(14, RESULT_HEADERS[index].length() + 5)) * 256);
        }
        sheet.createFreezePane(1, 3);
        return sheet;
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
        return RESULT_SHEET_NAME;
    }

    static List<String> resultHeaders() {
        return List.of(RESULT_HEADERS);
    }

    private record Course(
        String applicantNumber, int sourceRowNumber, int schoolYear, int semester,
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
        List<Course> courses, GradeVerificationResponse score
    ) {}

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
