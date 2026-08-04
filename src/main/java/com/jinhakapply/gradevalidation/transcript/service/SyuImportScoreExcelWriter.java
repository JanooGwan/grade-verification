package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus.PUBLISHED;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private static final String GENERAL = "학교장추천|일반학과(부)";
    private static final String ART = "학교장추천|아트앤디자인학과";
    private static final String SPORTS = "학교장추천|체육학과";
    private static final String TALENT = "예체능인재|체육학과";
    private static final String SPECIALIZED = "특성화고교|일반학과(부)";
    private static final String[] RESULT_HEADERS = {
        "수험번호", "전체 과목수", "환산 가능 과목수",
        "일반 반영과목수", "일반 환산점수×이수단위 합", "일반 반영 이수단위 합",
        "일반 100점 평균", "학교장추천 일반 교과점수(1,000점)",
        "상위 2개 교과영역", "상위2 반영과목수", "상위2 환산점수×이수단위 합",
        "상위2 반영 이수단위 합", "상위2 100점 평균",
        "학교장추천 체육 교과점수(400점)", "학교장추천 미술 교과점수(200점)",
        "예체능인재 교과점수(360점)", "무단결석", "무단지각", "무단조퇴", "무단결과",
        "등가결석일수", "출결 환산점수(100점)", "출결 반영점수(40점)",
        "예체능인재 학생부점수(400점)", "특성화고 반영과목수",
        "특성화고 100점 평균", "특성화고 교과점수(1,000점)", "검증 상태/안내"
    };
    private static final String[] DETAIL_HEADERS = {
        "수험번호", "원본 행", "학년", "학기", "원본 교과", "적용 교과", "과목명",
        "이수단위", "석차등급", "성취도", "진로선택", "전문교과", "수강자수",
        "환산점수", "환산점수×이수단위", "일반 반영", "일반 제외 사유",
        "상위2 반영", "상위2 제외 사유", "특성화고 반영", "특성화고 제외 사유"
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
        Map<String, EvaluationRule> rules = loadRules(transcriptImport.getAdmissionYear());
        Map<String, Attendance> attendance = loadAttendance(transcriptImport.getAdmissionYear());
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook) {
            Styles styles = new Styles(workbook);
            Sheet guide = createGuideSheet(workbook, transcriptImport, styles);
            Sheet results = createTableSheet(workbook, "지원자별 환산 결과", RESULT_HEADERS, styles);
            Sheet details = createTableSheet(workbook, "과목별 계산 근거", DETAIL_HEADERS, styles);
            int[] rowIndexes = {3, 3};
            int[] applicantCount = {0};

            streamCourses(transcriptImport, courses -> {
                ApplicantResult result = calculate(courses, rules, attendance.getOrDefault(
                    courses.getFirst().applicantNumber(), Attendance.EMPTY
                ));
                writeApplicantRow(results.createRow(rowIndexes[0]++), result, styles);
                writeCourseRows(details, rowIndexes, result, styles);
                applicantCount[0]++;
            });

            guide.getRow(10).getCell(3).setCellValue(applicantCount[0]);
            results.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowIndexes[0] - 1), 0, RESULT_HEADERS.length - 1));
            details.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowIndexes[1] - 1), 0, DETAIL_HEADERS.length - 1));
            workbook.write(output);
        } catch (IOException exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "삼육대 환산 결과 Excel 파일을 생성하지 못했습니다.");
        }
    }

    private Map<String, EvaluationRule> loadRules(int admissionYear) {
        Long universityId = jdbcTemplate.queryForObject(
            "SELECT id FROM university WHERE code = 'SY'", Long.class
        );
        Map<String, EvaluationRule> rules = new HashMap<>();
        ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(universityId, admissionYear, PUBLISHED)
            .forEach(rule -> {
                initializeRuleCollections(rule);
                rules.merge(key(rule), rule,
                    (left, right) -> left.getVersion() >= right.getVersion() ? left : right);
            });
        List<String> missing = List.of(GENERAL, ART, SPORTS, TALENT, SPECIALIZED).stream()
            .filter(key -> !rules.containsKey(key)).toList();
        if (!missing.isEmpty()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "삼육대 " + admissionYear + "학년도 환산 규칙이 없습니다: " + String.join(", ", missing));
        }
        return rules;
    }

    private void initializeRuleCollections(EvaluationRule rule) {
        rule.getGradeScores().size();
        rule.getAchievementGrades().size();
        rule.getAchievementScores().size();
        rule.getLegacyAchievementGrades().size();
        rule.getSubjectPriorities().size();
    }

    private String key(EvaluationRule rule) {
        return rule.getAdmissionType() + "|" + rule.getRecruitmentUnit();
    }

    private Map<String, Attendance> loadAttendance(int admissionYear) {
        Map<String, Attendance> result = new HashMap<>();
        jdbcTemplate.query("""
            SELECT student.applicant_number,
                   COALESCE(SUM(attendance.unexcused_absence_days), 0) absence_days,
                   COALESCE(SUM(attendance.unexcused_tardy_count), 0) tardy_count,
                   COALESCE(SUM(attendance.unexcused_early_leave_count), 0) early_leave_count,
                   COALESCE(SUM(attendance.unexcused_class_absence_count), 0) class_absence_count
            FROM student
            LEFT JOIN student_attendance attendance ON attendance.student_id = student.id
            WHERE student.admission_year = ?
            GROUP BY student.id, student.applicant_number
            """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(rs.getString("applicant_number"), new Attendance(
                rs.getInt("absence_days"), rs.getInt("tardy_count"),
                rs.getInt("early_leave_count"), rs.getInt("class_absence_count")
            )), admissionYear);
        return result;
    }

    private void streamCourses(StudentTranscriptImport transcriptImport, CourseGroupConsumer consumer) {
        jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                SELECT student.applicant_number, course.source_row_number, course.school_year, course.semester,
                       course.subject_category, course.course_name, course.grade_value, course.grade_scale,
                       course.achievement, course.raw_score, course.mean_score, course.standard_deviation,
                       course.student_count, course.rank_position, course.tied_rank_count,
                       course.legacy_achievement, course.credits, course.career_subject,
                       course.professional_course
                FROM student_transcript_course course
                JOIN student ON student.id = course.student_id
                WHERE student.admission_year = ? AND course.source_file_name = ?
                ORDER BY student.applicant_number, course.source_row_number, course.id
                """, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchSize(Integer.MIN_VALUE);
            statement.setInt(1, transcriptImport.getAdmissionYear());
            statement.setString(2, transcriptImport.getOriginalFileName());
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

    private ApplicantResult calculate(
        List<Course> courses,
        Map<String, EvaluationRule> rules,
        Attendance attendance
    ) {
        Map<String, GradeVerificationResponse> scores = new LinkedHashMap<>();
        Map<String, List<String>> failures = new LinkedHashMap<>();
        for (String scenario : List.of(GENERAL, ART, SPORTS, TALENT, SPECIALIZED)) {
            try {
                EvaluationRule rule = rules.get(scenario);
                scores.put(scenario, evaluationService.verify(rule, new VerifyGradeRequest(
                    rule.getId(), false, HighSchoolType.GENERAL, null,
                    courses.stream().map(Course::toRequest).toList()
                )));
            } catch (CustomException exception) {
                failures.computeIfAbsent(exception.getFullMessage(), ignored -> new ArrayList<>())
                    .add(scenario.replace('|', ' '));
            }
        }
        int equivalentAbsence = attendance.absenceDays()
            + (attendance.tardyCount() + attendance.earlyLeaveCount() + attendance.classAbsenceCount()) / 3;
        BigDecimal attendanceBase = attendanceScore(equivalentAbsence);
        return new ApplicantResult(courses, scores, attendance, equivalentAbsence, attendanceBase,
            failures.entrySet().stream()
                .map(entry -> String.join(", ", entry.getValue()) + ": " + entry.getKey())
                .collect(Collectors.joining(" / ")));
    }

    static BigDecimal attendanceScore(int equivalentAbsence) {
        if (equivalentAbsence <= 3) return new BigDecimal("100");
        if (equivalentAbsence <= 7) return new BigDecimal("98");
        if (equivalentAbsence <= 12) return new BigDecimal("96");
        if (equivalentAbsence <= 20) return new BigDecimal("94");
        if (equivalentAbsence <= 40) return new BigDecimal("90");
        return BigDecimal.ZERO;
    }

    private void writeApplicantRow(Row row, ApplicantResult result, Styles styles) {
        GradeVerificationResponse general = result.scores().get(GENERAL);
        GradeVerificationResponse topTwo = result.scores().get(SPORTS);
        GradeVerificationResponse art = result.scores().get(ART);
        GradeVerificationResponse talent = result.scores().get(TALENT);
        GradeVerificationResponse specialized = result.scores().get(SPECIALIZED);
        BigDecimal attendanceApplied = result.attendanceBase().multiply(new BigDecimal("0.4"));
        BigDecimal talentStudentRecord = talent == null ? null : talent.finalScore().add(attendanceApplied);
        Object[] values = {
            result.courses().getFirst().applicantNumber(), result.courses().size(), gradableCount(result.courses()),
            included(general), weightedSum(general), credits(general), base(general), finalScore(general),
            selectedSubjects(topTwo), included(topTwo), weightedSum(topTwo), credits(topTwo), base(topTwo),
            finalScore(topTwo), finalScore(art), finalScore(talent),
            result.attendance().absenceDays(), result.attendance().tardyCount(),
            result.attendance().earlyLeaveCount(), result.attendance().classAbsenceCount(),
            result.equivalentAbsence(), result.attendanceBase(), attendanceApplied,
            talentStudentRecord, included(specialized), base(specialized), finalScore(specialized),
            result.warning().isBlank() ? "계산 완료(전형·모집단위 미매핑 시나리오 결과)" : result.warning()
        };
        writeRow(row, values, styles);
        row.setHeightInPoints(result.warning().isBlank() ? 32 : 64);
        row.getCell(RESULT_HEADERS.length - 1).setCellStyle(styles.note);
    }

    private long gradableCount(List<Course> courses) {
        return courses.stream().filter(course ->
            course.grade() != null || course.achievement() != null
        ).count();
    }

    private Integer included(GradeVerificationResponse response) {
        return response == null ? null : response.includedCourseCount();
    }

    private BigDecimal weightedSum(GradeVerificationResponse response) {
        return response == null ? null : response.calculationSummary().convertedScoreTimesCreditsSum();
    }

    private BigDecimal credits(GradeVerificationResponse response) {
        return response == null ? null : response.calculationSummary().totalIncludedCredits();
    }

    private BigDecimal base(GradeVerificationResponse response) {
        return response == null ? null : response.baseScore();
    }

    private BigDecimal finalScore(GradeVerificationResponse response) {
        return response == null ? null : response.finalScore();
    }

    private String selectedSubjects(GradeVerificationResponse response) {
        if (response == null) return "";
        return response.calculations().stream().filter(CourseCalculation::included)
            .map(CourseCalculation::appliedSubjectCategory)
            .map(this::subjectDomainLabel).distinct().collect(Collectors.joining(", "));
    }

    private String subjectDomainLabel(SubjectCategory category) {
        return category == SubjectCategory.SOCIAL || category == SubjectCategory.SCIENCE
            ? "탐구" : subjectLabel(category);
    }

    private String subjectLabel(SubjectCategory category) {
        return switch (category) {
            case KOREAN -> "국어";
            case ENGLISH -> "영어";
            case MATH -> "수학";
            case SOCIAL -> "사회";
            case SCIENCE -> "과학";
            case OTHER -> "기타";
        };
    }

    private void writeCourseRows(Sheet sheet, int[] rowIndexes, ApplicantResult result, Styles styles) {
        Map<Integer, CourseCalculation> general = calculationsByIndex(result.scores().get(GENERAL));
        Map<Integer, CourseCalculation> topTwo = calculationsByIndex(result.scores().get(SPORTS));
        Map<Integer, CourseCalculation> specialized = calculationsByIndex(result.scores().get(SPECIALIZED));
        for (int index = 0; index < result.courses().size(); index++) {
            Course course = result.courses().get(index);
            CourseCalculation common = firstNonNull(general.get(index), topTwo.get(index), specialized.get(index));
            CourseCalculation generalCalculation = general.get(index);
            CourseCalculation topCalculation = topTwo.get(index);
            CourseCalculation specializedCalculation = specialized.get(index);
            writeRow(sheet.createRow(rowIndexes[1]++), new Object[] {
                course.applicantNumber(), course.sourceRowNumber(), course.schoolYear(), course.semester(),
                subjectLabel(course.subjectCategory()), common == null ? "" : subjectLabel(common.appliedSubjectCategory()),
                course.courseName(), course.credits(), course.grade(), course.achievement(),
                course.careerSubject() ? "Y" : "N", course.professionalCourse() ? "Y" : "N", course.studentCount(),
                common == null ? null : common.convertedScore(),
                convertedScoreTimesCredits(common, course.credits()),
                includedLabel(generalCalculation), exclusion(generalCalculation),
                includedLabel(topCalculation), exclusion(topCalculation),
                includedLabel(specializedCalculation), exclusion(specializedCalculation)
            }, styles);
        }
    }

    private BigDecimal convertedScoreTimesCredits(CourseCalculation calculation, BigDecimal credits) {
        return calculation == null || calculation.convertedScore() == null
            ? null : calculation.convertedScore().multiply(credits);
    }

    private Map<Integer, CourseCalculation> calculationsByIndex(GradeVerificationResponse response) {
        Map<Integer, CourseCalculation> result = new HashMap<>();
        if (response == null) return result;
        for (int index = 0; index < response.calculations().size(); index++) {
            result.put(index, response.calculations().get(index));
        }
        return result;
    }

    private CourseCalculation firstNonNull(CourseCalculation... values) {
        for (CourseCalculation value : values) if (value != null) return value;
        return null;
    }

    private String includedLabel(CourseCalculation calculation) {
        return calculation == null ? "계산 실패" : calculation.included() ? "Y" : "N";
    }

    private String exclusion(CourseCalculation calculation) {
        return calculation == null || calculation.included() ? "" : calculation.exclusionReason();
    }

    private Sheet createGuideSheet(SXSSFWorkbook workbook, StudentTranscriptImport source, Styles styles) {
        Sheet sheet = workbook.createSheet("안내 및 산식");
        sheet.setDisplayGridlines(false);
        title(sheet, "삼육대학교 2026 학생부 환산 결과", 3, styles);
        int row = 2;
        row = keyValue(sheet, row, "가져오기 작업", source.getId(), "모집연도", source.getAdmissionYear(), styles);
        row = keyValue(sheet, row, "원본 파일명", source.getOriginalFileName(), "원천 형식", source.getSourceFormat(), styles);
        row++;
        row = section(sheet, row, "중요 안내", styles);
        row = note(sheet, row, "원본 통합문서에는 지원자의 실제 전형·모집단위 정보가 없습니다. 따라서 아래 점수는 가능한 전형별 시나리오이며, 지원정보가 연결되기 전에는 최종 합격 산정점수로 사용하면 안 됩니다.", styles);
        row = note(sheet, row, "학교장추천 일반은 1,000점, 체육은 교과 400점, 미술은 교과 200점, 예체능인재 체육은 교과 360점+출결 40점, 특성화고는 1,000점 시나리오입니다.", styles);
        row++;
        row = section(sheet, row, "처리 집계", styles);
        row = keyValue(sheet, row, "원본 처리 행", source.getImportedRows(), "지원자 수", 0, styles);
        row++;
        row = section(sheet, row, "공식 산식", styles);
        row = note(sheet, row, "교과 100점 평균 = Σ(과목 환산점수×이수단위) ÷ Σ(이수단위). 일반은 국·영·수·사·과 전 과목, 예체능은 성적이 높은 2개 교과영역, 특성화고는 국·영·수 전 과목을 반영합니다.", styles);
        row = note(sheet, row, "석차등급 환산: 1=100, 2=99, 3=98, 4=96.5, 5=95, 6=92, 7=85, 8=60, 9=0. 진로선택 성취도: A=100, B=99, C=96.5.", styles);
        row = note(sheet, row, "등가결석일수 = 무단결석 + floor((무단지각+무단조퇴+무단결과)÷3). 출결 100점 환산 후 0.4를 곱해 40점 만점으로 반영합니다.", styles);
        row = note(sheet, row, "근거: 2026학년도 삼육대학교 수시모집요강 39~40쪽. 교과 환산 최종값은 소수점 5째 자리에서 버리고 4째 자리까지 표시합니다.", styles);
        note(sheet, row, "공식 출처: https://ipsi.syu.ac.kr/2016_syu/pages/index.asp?b=B_1_1&bn=64594&m=read&p=29", styles);
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 42 * 256);
        sheet.setColumnWidth(2, 22 * 256);
        sheet.setColumnWidth(3, 38 * 256);
        return sheet;
    }

    private Sheet createTableSheet(SXSSFWorkbook workbook, String name, String[] headers, Styles styles) {
        Sheet sheet = workbook.createSheet(name);
        sheet.setDisplayGridlines(false);
        title(sheet, name, headers.length - 1, styles);
        Row header = sheet.createRow(2);
        header.setHeightInPoints(36);
        for (int index = 0; index < headers.length; index++) {
            set(header.createCell(index), headers[index], styles.header);
            sheet.setColumnWidth(index, Math.min(42, Math.max(12, headers[index].length() + 4)) * 256);
        }
        sheet.createFreezePane(1, 3);
        return sheet;
    }

    private void title(Sheet sheet, String label, int lastColumn, Styles styles) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(32);
        set(row.createCell(0), label, styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));
    }

    private int section(Sheet sheet, int rowIndex, String label, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        set(row.createCell(0), label, styles.section);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 3));
        return rowIndex + 1;
    }

    private int keyValue(Sheet sheet, int rowIndex, Object key1, Object value1, Object key2, Object value2, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        set(row.createCell(0), key1, styles.key);
        set(row.createCell(1), value1, styles.value);
        set(row.createCell(2), key2, styles.key);
        set(row.createCell(3), value2, styles.value);
        return rowIndex + 1;
    }

    private int note(Sheet sheet, int rowIndex, String value, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(36);
        set(row.createCell(0), value, styles.note);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 3));
        return rowIndex + 1;
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

    private record Attendance(int absenceDays, int tardyCount, int earlyLeaveCount, int classAbsenceCount) {
        private static final Attendance EMPTY = new Attendance(0, 0, 0, 0);
    }

    private record ApplicantResult(
        List<Course> courses, Map<String, GradeVerificationResponse> scores, Attendance attendance,
        int equivalentAbsence, BigDecimal attendanceBase, String warning
    ) {}

    @FunctionalInterface
    private interface CourseGroupConsumer {
        void accept(List<Course> courses);
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle section;
        private final CellStyle header;
        private final CellStyle key;
        private final CellStyle value;
        private final CellStyle number;
        private final CellStyle note;

        private Styles(SXSSFWorkbook workbook) {
            title = style(workbook, IndexedColors.DARK_GREEN, IndexedColors.WHITE, true, false);
            section = style(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.DARK_GREEN, true, false);
            header = style(workbook, IndexedColors.DARK_GREEN, IndexedColors.WHITE, true, true);
            key = style(workbook, IndexedColors.PALE_BLUE, IndexedColors.DARK_GREEN, true, false);
            value = style(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false, false);
            number = style(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false, false);
            number.setDataFormat(workbook.createDataFormat().getFormat("#,##0.####"));
            note = style(workbook, IndexedColors.LIGHT_YELLOW, IndexedColors.DARK_RED, false, true);
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
