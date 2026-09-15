package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementConversion;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.ScoreAggregation;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.policy.DeclarativeSelectionPolicyEngine;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class TransferSubjectSettingsTest {
    private final TransferExcelParser parser = new TransferExcelParser();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void appliesExplicitSettingsRegardlessOfSheetOrderAndPreservesCourseData(boolean settingsFirst) throws Exception {
        var result = parser.parse(workbook(book -> {
            if (settingsFirst) book.setSheetOrder("반영교과설정", 0);
        }));
        assertThat(result.invalidRows()).isZero();
        assertThat(result.courses()).extracting(TranscriptExcelRow::subjectCategory).containsExactly(
            SubjectCategory.MATH, SubjectCategory.ENGLISH, SubjectCategory.SOCIAL,
            SubjectCategory.OTHER, SubjectCategory.OTHER);
        assertThat(result.courses().get(3)).satisfies(course -> {
            assertThat(course.courseName()).isEqualTo("아동 복지");
            assertThat(course.grade()).isEqualTo(1);
            assertThat(course.credits()).isEqualByComparingTo("2");
            assertThat(course.professionalCourse()).isFalse();
        });
        assertThat(result.courses().get(1).professionalCourse()).isFalse();
    }

    @Test
    void excludesUnassignedCoursesFromHealthButRetainsThemForGeneralSemesterSelection() throws Exception {
        var parsed = parser.parse(workbook(book -> {}));
        var courses = parsed.courses().stream().map(course -> new VerifyGradeRequest.CourseGrade(
            course.schoolYear(), course.semester(), course.subjectCategory(), course.courseName(),
            course.grade(), course.achievement(), course.rawScore(), course.meanScore(),
            course.standardDeviation(), course.studentCount(), course.careerSubject(),
            course.professionalCourse(), course.credits()
        )).toList();
        var service = new EvaluationService(null, null, new DeclarativeSelectionPolicyEngine());
        var request = new VerifyGradeRequest(1L, courses);

        var health = service.verify(rule(true), request);
        assertThat(health.finalScore()).isEqualByComparingTo("75");
        assertThat(health.calculations()).filteredOn(GradeVerificationResponse.CourseCalculation::included)
            .extracting(GradeVerificationResponse.CourseCalculation::courseName)
            .containsExactlyInAnyOrder("수학", "영어Ⅰ", "사회");

        var general = service.verify(rule(false), request);
        assertThat(general.includedCourseCount()).isEqualTo(5);
        assertThat(general.finalScore()).isEqualByComparingTo("85");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4, 6})
    void doesNotBorrowAssignmentFromAnotherYearPeriodOrganizationSubjectOrCourse(int keyColumn) throws Exception {
        var result = parser.parse(workbook(book ->
            book.getSheet("반영교과설정").getRow(2).getCell(keyColumn).setCellValue("999")));
        assertThat(result.courses().get(1).subjectCategory()).isEqualTo(SubjectCategory.OTHER);
    }

    @Test
    void doesNotInferCoreSubjectWhenNoSettingsKeyMatches() throws Exception {
        var result = parser.parse(workbook(book -> {
            Sheet settings = book.getSheet("반영교과설정");
            settings.removeRow(settings.getRow(1));
        }));
        assertThat(result.courses().getFirst().subjectCategory()).isEqualTo(SubjectCategory.OTHER);
    }

    @Test
    void missingSettingsUsesFallbackWithoutTreatingForeignLanguagesAsKorean() throws Exception {
        var result = parser.parse(workbook(book -> book.removeSheetAt(book.getSheetIndex("반영교과설정"))));
        assertThat(result.courses().getFirst().subjectCategory()).isEqualTo(SubjectCategory.MATH);
        assertThat(result.courses().get(1).subjectCategory()).isEqualTo(SubjectCategory.OTHER);
        assertThat(result.warnings()).anyMatch(w -> w.contains("반영교과설정 시트가 없어"));
    }

    @Test
    void rejectsConflictingAssignmentsInsteadOfSilentlyChoosingLastRow() {
        assertThatThrownBy(() -> parser.parse(workbook(book ->
            row(book.getSheet("반영교과설정"), 5, 2026, 1, 102, "외국어", 0, "", 12, "영어Ⅰ", 10, "국어"))))
            .isInstanceOfSatisfying(CustomException.class, error ->
                assertThat(error.getDetail()).contains("동일 코드 조합"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalidHeader", "invalidCode", "mismatchedLabel", "missingCode", "emptySheet"})
    void rejectsMalformedSettings(String issue) {
        assertThatThrownBy(() -> parser.parse(workbook(book -> {
            Sheet sheet = book.getSheet("반영교과설정");
            switch (issue) {
                case "invalidHeader" -> sheet.getRow(0).getCell(8).setCellValue("다른열");
                case "invalidCode" -> sheet.getRow(2).getCell(8).setCellValue("99");
                case "mismatchedLabel" -> sheet.getRow(2).getCell(9).setCellValue("국어");
                case "missingCode" -> sheet.getRow(2).getCell(8).setBlank();
                case "emptySheet" -> {
                    book.removeSheetAt(book.getSheetIndex("반영교과설정"));
                    book.createSheet("반영교과설정");
                }
                default -> throw new IllegalArgumentException(issue);
            }
        }))).isInstanceOfSatisfying(CustomException.class, error ->
            assertThat(error.getDetail()).contains("반영교과설정"));
    }

    @Test
    void acceptsRepeatedIdenticalAssignmentAndCodeWithoutOptionalLabel() throws Exception {
        var result = parser.parse(workbook(book -> {
            Sheet settings = book.getSheet("반영교과설정");
            settings.getRow(2).getCell(9).setBlank();
            row(settings, 5, 2026, 1, 102, "외국어", 0, "", 12, "이름이 달라도 코드 기준", 20, "영어");
        }));
        assertThat(result.courses().get(1).subjectCategory()).isEqualTo(SubjectCategory.ENGLISH);
    }

    private MockMultipartFile workbook(Consumer<XSSFWorkbook> customize) throws Exception {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet applications = book.createSheet("지원자정보");
            row(applications, 0, "header");
            row(applications, 1, 2026, 1, "수시1차", "SYNTHETIC-001", 0, 2, "자연", "T1", "일반", "U1", "간호학과", "U1", 2026);
            Sheet courses = book.createSheet("교과학습발달");
            row(courses, 0, "header");
            course(courses, 1, "00101", "수학", "00011", "수학", 3, 1);
            course(courses, 2, "00102", "외국어", "00012", "영어Ⅰ", 2, 1);
            course(courses, 3, "00103", "사회", "00013", "사회", 4, 1);
            course(courses, 4, "00103", "사회", "00014", "아동 복지", 1, 1);
            course(courses, 5, "00103", "사회", "00014", "아동 복지", 1, 2);
            Sheet settings = book.createSheet("반영교과설정");
            row(settings, 0, "입학연도", "모집시기", "편제코드", "편제명", "교과코드", "교과명", "과목코드", "과목명", "과목구분코드", "과목구분");
            row(settings, 1, 2026, 1, 101, "수학", 0, "", 11, "수학", 30, "수학");
            row(settings, 2, 2026, 1, 102, "외국어", 0, "", 12, "영어Ⅰ", 20, "영어");
            row(settings, 3, 2026, 1, 103, "사회", 0, "", 13, "사회", 40, "사회");
            row(settings, 4, 2026, 1, 103, "사회", 0, "", 14, "아동 복지", null, null);
            customize.accept(book);
            book.write(output);
            return new MockMultipartFile("file", "synthetic-settings.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private void course(Sheet sheet, int index, String organizationCode, String organization,
        String code, String name, int grade, int semester) {
        row(sheet, index, 2026, "01", "SYNTHETIC-001", 1, semester, organizationCode, organization,
            "000", "", code, name, 2, 0, 100, 0, 80, 70, 10, grade, null, "01");
    }

    private void row(Sheet sheet, int index, Object... values) {
        var row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof Number n) row.createCell(i).setCellValue(n.doubleValue());
            else if (values[i] != null) row.createCell(i).setCellValue(values[i].toString());
        }
    }

    private EvaluationRule rule(boolean health) {
        var rule = EvaluationRule.create(University.create("TEST", "경복대학교"), "합성 검증 규칙", 2026,
            "학생부교과", health ? "간호학과" : "일반학과", 1, decimals("1", "1", "1"),
            decimals("1", "1", "1", "1", "1", health ? "0" : "1"),
            decimals("100", "87.5", "75", "62.5", "50", "37.5", "25", "12.5", "0"),
            health ? SelectionStrategy.TOP_N_SUBJECTS : SelectionStrategy.TOP_N_SEMESTERS,
            health ? 3 : 2, 0, 0, ScoreAggregation.AVERAGE_GRADE_THEN_SCORE, AchievementConversion.Z_SCORE,
            false, false, true, false, false, 1, RoundingMode.DOWN, 2, RoundingMode.HALF_UP,
            BigDecimal.ONE, decimals("1", "3", "5"), decimals("100", "75", "50"),
            List.of(3, 2, 4, 5, 1, 6), "합성 검증", "1", null, null);
        rule.markVerified("test", null);
        rule.publish("test", null);
        return rule;
    }

    private List<BigDecimal> decimals(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }
}
