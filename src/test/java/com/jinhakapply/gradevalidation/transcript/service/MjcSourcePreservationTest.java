package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.jinhakapply.gradevalidation.admission.service.EvaluationRuleMatcher;
import com.jinhakapply.gradevalidation.evaluation.domain.AchievementConversion;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.domain.ScoreAggregation;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MjcSourcePreservationTest {
    private static final String APPLICANT = "SYNTHETIC-MJC-PRESERVATION";
    private static final String TRACK = "정원내 특별전형(일반고)";
    private static final List<String> NON_NUMERIC_GRADES = List.of("P", "·", "이수", "우수", "보통", "미흡");
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void preservesUnselectedSemesterAndSourceGradesWithoutAddingMissingGradeCourses(int admissionYear) throws Exception {
        StringBuilder csv = new StringBuilder(
            "examNumber,grade,term,organizationName,subjectName,unit,rankingGrade,achievement,originalScore,avgScore,standardDeviation,studentCount,rank,sameRank,subjectSeparationCode\n"
        );
        for (int index = 1; index <= 17; index++) {
            append(csv, 1, 1, "비반영 원천과목 " + index, "3", "9");
        }
        append(csv, 1, 2, "1학년 우수학기", "3", "2");
        append(csv, 2, 1, "2학년 우수학기", "3", "3");
        for (String grade : NON_NUMERIC_GRADES) {
            append(csv, 3, 1, "비수치 원천과목 " + grade, "1", grade);
        }
        append(csv, 2, 2, "0단위 원천과목", "0", "1");
        Path source = directory.resolve("synthetic.csv");
        Files.writeString(source, csv);
        List<MjcSourceCsvReader.CourseRow> sourceRows = new ArrayList<>();
        var imported = new MjcSourceCsvReader().streamCourses(source, 7, sourceRows::addAll);
        List<TranscriptExcelRow> courses = sourceRows.stream().map(row -> toCourse(row, admissionYear)).toList();

        assertThat(imported.totalRows()).isEqualTo(26);
        assertThat(imported.importedRows()).isEqualTo(26);
        assertThat(imported.skippedRows()).isZero();
        var result = verify(admissionYear, courses);
        var success = result.successes().getFirst();
        assertThat(result.failures()).isEmpty();
        assertThat(success.verification().includedCourseCount()).isEqualTo(2);
        assertThat(success.verification().excludedCourseCount()).isEqualTo(24);
        assertThat(success.verification().calculationSummary().totalIncludedCredits()).isEqualByComparingTo("6");
        assertThat(success.verification().finalScore()).isEqualByComparingTo("590.00000");
        assertThat(success.selectedCourses()).extracting(selected -> selected.source().courseName())
            .containsExactly("1학년 우수학기", "2학년 우수학기");

        try (var workbook = export(courses, result)) {
            var details = workbook.getSheet("학생별 과목 비교");
            assertThat(details.getLastRowNum()).isEqualTo(courses.size() + 2);
            List<String> grades = new ArrayList<>();
            int unselectedFirstSemester = 0;
            for (int index = 3; index <= details.getLastRowNum(); index++) {
                var row = details.getRow(index);
                String name = row.getCell(11).getStringCellValue();
                assertThat(name).doesNotContain("성적 미기재");
                grades.add(new DataFormatter().formatCellValue(row.getCell(12)));
                if (name.startsWith("비반영 원천과목")) {
                    unselectedFirstSemester++;
                    assertThat(row.getCell(5).getStringCellValue()).isEqualTo("미선택");
                }
                if (name.equals("0단위 원천과목")) {
                    assertThat(row.getCell(14).getNumericCellValue()).isZero();
                    assertThat(row.getCell(5).getStringCellValue()).isEqualTo("미선택");
                }
            }
            assertThat(unselectedFirstSemester).isEqualTo(17);
            assertThat(grades).containsAll(NON_NUMERIC_GRADES);
            var student = workbook.getSheet("학생별 검증 결과").getRow(3);
            assertThat(student.getCell(14).getNumericCellValue()).isEqualTo(6);
            assertThat(student.getCell(18).getNumericCellValue()).isEqualTo(590);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void labelsEqualSemesterAveragesWithoutChangingSelectedCoursesOrCredits(int admissionYear) throws Exception {
        var courses = List.of(
            course(admissionYear, 1, 1, "1학기 동률과목", 2, "2"),
            course(admissionYear, 1, 2, "2학기 동률과목", 2, "5"),
            course(admissionYear, 2, 1, "2학년 과목", 3, "3"),
            course(admissionYear, 3, 1, "3학년 과목", 4, "3")
        );
        var result = verify(admissionYear, courses);
        var success = result.successes().getFirst();
        assertThat(success.verification().finalScore()).isEqualByComparingTo("790.00000");
        assertThat(success.verification().calculationSummary().totalIncludedCredits()).isEqualByComparingTo("11");
        assertThat(success.selectedCourses()).extracting(selected -> selected.source().courseName())
            .containsExactly("2학기 동률과목", "2학년 과목", "3학년 과목");
        try (var workbook = export(courses, result)) {
            var student = workbook.getSheet("학생별 검증 결과").getRow(3);
            assertThat(student.getCell(6).getStringCellValue()).isEqualTo("2학기 우수(동률)");
            assertThat(student.getCell(9).getStringCellValue()).isEqualTo("1학기 우수");
            assertThat(student.getCell(11).getStringCellValue()).isEqualTo("1학기 반영");
            var details = workbook.getSheet("학생별 과목 비교");
            assertThat(details.getRow(3).getCell(5).getStringCellValue()).isEqualTo("미선택");
            assertThat(details.getRow(4).getCell(5).getStringCellValue()).isEqualTo("선택됨");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void keepsTwoYearSchoolSemestersAsAllReflectedEvenWhenAveragesAreEqual(int admissionYear) throws Exception {
        var courses = List.of(
            course(admissionYear, 1, 1, "1학기 과목", 2, "2"),
            course(admissionYear, 1, 2, "2학기 과목", 2, "5"),
            course(admissionYear, 2, 1, "2학년 과목", 3, "3")
        );
        var schoolInfo = new ApplicantSchoolInfoRow(2, admissionYear, APPLICANT, admissionYear,
            null, null, null, null, null, null, EducationBackground.DOMESTIC_HIGH_SCHOOL, HighSchoolType.TWO_YEAR);
        var result = verify(admissionYear, courses, Map.of(APPLICANT, schoolInfo));
        assertThat(result.successes().getFirst().verification().finalScore()).isEqualByComparingTo("860.00000");
        try (var workbook = export(courses, result)) {
            var student = workbook.getSheet("학생별 검증 결과").getRow(3);
            assertThat(student.getCell(6).getStringCellValue()).isEqualTo("1·2학기 모두 반영");
            assertThat(student.getCell(14).getNumericCellValue()).isEqualTo(10);
        }
    }

    private void append(StringBuilder csv, int year, int semester, String name, String units, String grade) {
        csv.append(APPLICANT).append(',').append(year).append(',').append(semester)
            .append(",국어,").append(name).append(',').append(units).append(',').append(grade)
            .append(",\\N,\\N,\\N,\\N,100,\\N,\\N,01\n");
    }

    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void storesUnknownGradeButDoesNotSilentlySubstituteMissingGradeOrDropItsCredits(int admissionYear) throws Exception {
        StringBuilder csv = syntheticGradeCsv();
        append(csv, 1, 1, "합성 정상과목1", "3", "1");
        append(csv, 2, 1, "합성 정상과목2", "3", "2");
        append(csv, 3, 1, "합성 정상과목3", "3", "3");
        append(csv, 1, 2, "합성 미해석과목", "3", "SYNTHETIC_UNRESOLVED");
        Path path = directory.resolve("unknown-grade.csv");
        Files.writeString(path, csv);
        List<MjcSourceCsvReader.CourseRow> imported = new ArrayList<>();
        var count = new MjcSourceCsvReader().streamCourses(path, 2, imported::addAll);
        assertThat(count.importedRows()).isEqualTo(4);
        assertThat(count.unresolvedGrades()).isEqualTo(1);
        var courses = imported.stream().map(row -> toCourse(row, admissionYear)).toList();
        var result = verify(admissionYear, courses);
        assertThat(result.successes()).isEmpty();
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.code()).isEqualTo("MJC_SOURCE_GRADE_REVIEW_REQUIRED");
            assertThat(failure.reason()).doesNotContain("SYNTHETIC_UNRESOLVED");
        });
        try (var workbook = export(courses, result)) {
            var details = workbook.getSheet("학생별 과목 비교");
            assertThat(details.getLastRowNum()).isEqualTo(6);
            assertThat(new DataFormatter().formatCellValue(details.getRow(4).getCell(12))).isEqualTo("SYNTHETIC_UNRESOLVED");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void unknownGradesOutsideReflectionScopeDoNotChangeValidScores(int admissionYear) throws Exception {
        StringBuilder csv = syntheticGradeCsv();
        append(csv, 1, 1, "합성 정상과목1", "3", "1");
        append(csv, 2, 1, "합성 정상과목2", "3", "2");
        append(csv, 3, 1, "합성 정상과목3", "3", "3");
        append(csv, 1, 2, "합성 0단위", "0", "SYNTHETIC_UNRESOLVED");
        append(csv, 3, 2, "합성 미반영학기", "3", "SYNTHETIC_UNRESOLVED");
        Path path = directory.resolve("outside-scope.csv");
        Files.writeString(path, csv);
        List<MjcSourceCsvReader.CourseRow> imported = new ArrayList<>();
        new MjcSourceCsvReader().streamCourses(path, 10, imported::addAll);
        var courses = imported.stream().map(row -> toCourse(row, admissionYear)).toList();
        var result = verify(admissionYear, courses);
        assertThat(result.failures()).isEmpty();
        var success = result.successes().getFirst();
        assertThat(success.verification().finalScore()).isEqualByComparingTo("890.00000");
        assertThat(success.verification().calculationSummary().totalIncludedCredits()).isEqualByComparingTo("9");
        assertThat(success.verification().includedCourseCount()).isEqualTo(3);
        assertThat(success.verification().excludedCourseCount()).isEqualTo(2);
    }

    private StringBuilder syntheticGradeCsv() {
        return new StringBuilder("examNumber,grade,term,organizationName,subjectName,unit,rankingGrade,achievement,originalScore,avgScore,standardDeviation,studentCount,rank,sameRank,subjectSeparationCode\n");
    }

    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void recognizedSourceLabelsReachZScoreConversionWithoutNumericGradeOrAchievement(int admissionYear) throws Exception {
        for (String sourceGrade : List.of("A", "B", "C", "D", "E", ".", "·", " a ")) {
            for (String rawScore : List.of("90", "70")) {
                var courses = readWithSourceLabel(admissionYear, sourceGrade, rawScore, "80", "10");
                List<GradeVerificationResponse> savedResults = new ArrayList<>();
                var result = verify(admissionYear, courses, Map.of(), (application, value) -> savedResults.add(value));
                assertThat(result.failures()).as("source=%s, raw=%s", sourceGrade, rawScore).isEmpty();
                var verification = savedResults.getFirst();
                var calculation = verification.calculations().getLast();
                assertThat(calculation.grade()).isNull();
                assertThat(calculation.achievement()).isNull();
                assertThat(calculation.sourceGradeText()).isEqualTo(sourceGrade);
                assertThat(calculation.effectiveGrade()).isEqualByComparingTo(rawScore.equals("90") ? "3" : "7");
                assertThat(calculation.included()).isTrue();
                assertThat(verification.includedCourseCount()).isEqualTo(4);
                assertThat(verification.calculationSummary().totalIncludedCredits()).isEqualByComparingTo("11");
                assertThat(verification.finalScore()).isEqualByComparingTo(rawScore.equals("90") ? "806.00000" : "742.00000");

                try (var workbook = export(courses, result)) {
                    var details = workbook.getSheet("학생별 과목 비교");
                    assertThat(new DataFormatter().formatCellValue(details.getRow(6).getCell(12))).isEqualTo(sourceGrade);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void recognizedLabelsWithoutValidZInputsExcludeOnlyTheCourseAndKeepNumericScores(int admissionYear) throws Exception {
        for (String sourceGrade : List.of("A", "B", "C", "D", "E", ".", "·", "P", "이수", "우수", "보통", "미흡")) {
            for (String[] inputs : List.of(
                new String[]{"\\N", "\\N", "\\N"},
                new String[]{"\\N", "80", "10"},
                new String[]{"90", "\\N", "10"},
                new String[]{"90", "80", "\\N"},
                new String[]{"90", "80", "0"},
                new String[]{"90", "80", "-1"}
            )) {
                var courses = readWithSourceLabel(admissionYear, sourceGrade, inputs[0], inputs[1], inputs[2]);
                List<GradeVerificationResponse> savedResults = new ArrayList<>();
                var result = verify(admissionYear, courses, Map.of(), (application, value) -> savedResults.add(value));
                assertThat(result.failures()).as("source=%s, inputs=%s", sourceGrade, Arrays.toString(inputs)).isEmpty();
                var verification = savedResults.getFirst();
                var calculation = verification.calculations().getLast();
                assertThat(calculation.sourceGradeText()).isEqualTo(sourceGrade);
                assertThat(calculation.included()).isFalse();
                assertThat(calculation.effectiveGrade()).isNull();
                assertThat(calculation.exclusionReason()).isNotBlank();
                assertThat(verification.includedCourseCount()).isEqualTo(3);
                assertThat(verification.excludedCourseCount()).isEqualTo(1);
                assertThat(verification.calculationSummary().totalIncludedCredits()).isEqualByComparingTo("9");
                assertThat(verification.finalScore()).isEqualByComparingTo("790.00000");
            }
        }
    }

    private List<TranscriptExcelRow> readWithSourceLabel(int admissionYear, String sourceGrade,
        String rawScore, String mean, String standardDeviation) throws Exception {
        var csv = syntheticGradeCsv();
        append(csv, 1, 1, "합성 숫자과목1", "3", "2");
        append(csv, 2, 1, "합성 숫자과목2", "3", "3");
        append(csv, 3, 1, "합성 숫자과목3", "3", "4");
        csv.append(APPLICANT).append(",3,1,과학,합성 성취과목,2,").append(sourceGrade)
            .append(",\\N,").append(rawScore).append(',').append(mean).append(',').append(standardDeviation)
            .append(",100,\\N,\\N,01\n");
        Path path = directory.resolve("recognized-source-label.csv");
        Files.writeString(path, csv);
        List<MjcSourceCsvReader.CourseRow> imported = new ArrayList<>();
        var count = new MjcSourceCsvReader().streamCourses(path, 2, imported::addAll);
        assertThat(count.importedRows()).isEqualTo(4);
        assertThat(count.unresolvedGrades()).isZero();
        return imported.stream().map(row -> toCourse(row, admissionYear)).toList();
    }

    private TranscriptExcelRow toCourse(MjcSourceCsvReader.CourseRow row, int admissionYear) {
        return new TranscriptExcelRow(row.rowNumber(), row.examNumber(), "합성 지원자", null, null, admissionYear,
            row.schoolYear(), row.semester(), row.subjectCategory(), row.courseName(), row.grade(), GradeScale.NINE_LEVEL,
            row.achievement(), row.rawScore(), row.meanScore(), row.standardDeviation(), row.studentCount(),
            row.rankPosition(), row.tiedRankCount(), null, row.credits(), row.careerSubject(), row.professionalCourse(),
            false, row.sourceGradeText());
    }

    private TranscriptExcelRow course(int admissionYear, int year, int semester, String name, int grade, String units) {
        return new TranscriptExcelRow(year * 10 + semester, APPLICANT, "합성 지원자", null, null, admissionYear,
            year, semester, SubjectCategory.KOREAN, name, grade, GradeScale.NINE_LEVEL, null, null, null, null,
            100, null, null, null, new BigDecimal(units), false, false);
    }

    private TranscriptBatchVerificationResult verify(int admissionYear, List<TranscriptExcelRow> courses) {
        return verify(admissionYear, courses, Map.of());
    }

    private TranscriptBatchVerificationResult verify(int admissionYear, List<TranscriptExcelRow> courses,
        Map<String, ApplicantSchoolInfoRow> schoolInfo) {
        return verify(admissionYear, courses, schoolInfo, (application, verification) -> { });
    }

    private TranscriptBatchVerificationResult verify(int admissionYear, List<TranscriptExcelRow> courses,
        Map<String, ApplicantSchoolInfoRow> schoolInfo,
        BiConsumer<TransferApplicationRow, GradeVerificationResponse> verifiedResultConsumer) {
        var repository = mock(EvaluationRuleRepository.class);
        when(repository.findAllByUniversityIdAndAdmissionYearAndStatus(1L, admissionYear, EvaluationRuleStatus.PUBLISHED))
            .thenReturn(List.of(rule(admissionYear)));
        var service = new TranscriptBatchVerificationService(repository, new EvaluationService(null, null, null),
            new EvaluationRuleMatcher());
        var application = new TransferApplicationRow(2, admissionYear, APPLICANT, "TEST", TRACK,
            "TEST", "컴퓨터공학과", admissionYear);
        return service.verify(1L, admissionYear, List.of(application), courses, schoolInfo, verifiedResultConsumer);
    }

    private XSSFWorkbook export(List<TranscriptExcelRow> courses, TranscriptBatchVerificationResult result) throws Exception {
        byte[] bytes = new TranscriptValidationExcelWriter().write("synthetic.csv", "MJC_SOURCE_CSV_BUNDLE_V1",
            "명지전문대학교", 1, courses.size(), List.of(), courses, List.of(), List.of(), result);
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private EvaluationRule rule(int admissionYear) {
        var rule = EvaluationRule.create(University.create("MJC", "명지전문대학교"), "합성 규칙", admissionYear,
            TRACK, "전체 모집단위", 3, decimals("30", "30", "40"), decimals("1", "1", "1", "1", "1", "1"),
            decimals("100", "90", "80", "70", "60", "50", "40", "30", "20"),
            SelectionStrategy.BEST_SEMESTER_PER_GRADE, 0, 0, 0, ScoreAggregation.COURSE_SCORE_AVERAGE,
            AchievementConversion.Z_SCORE, false, false, true, true, true, 5, RoundingMode.HALF_UP,
            5, RoundingMode.HALF_UP, BigDecimal.TEN, decimals("1", "3", "5"), decimals("100", "80", "60"),
            List.of(1, 2, 3, 4, 5, 6), "수시 모집요강", "학생부 산출", null, null);
        rule.publish("test", null);
        return rule;
    }

    private List<BigDecimal> decimals(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }
}
