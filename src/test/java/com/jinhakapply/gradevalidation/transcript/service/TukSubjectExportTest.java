package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.jinhakapply.gradevalidation.evaluation.domain.*;
import com.jinhakapply.gradevalidation.evaluation.dto.*;
import com.jinhakapply.gradevalidation.evaluation.policy.DeclarativeSelectionPolicyEngine;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TukSubjectExportTest {
    private final EvaluationService evaluator = new EvaluationService(null, null, new DeclarativeSelectionPolicyEngine());
    private final TranscriptBatchVerificationService batch = new TranscriptBatchVerificationService(null, null, null);

    @Test void showsPreselectionRawCreditsAndActualChoiceIncludingSocialTieBreak() throws Exception {
        for (int year : List.of(2026, 2027)) {
            for (int careerCredits : List.of(5, 6, 7)) {
                var rule = rule(year, "경영학부");
                var courses = new ArrayList<VerifyGradeRequest.CourseGrade>();
                for (int grade = 1; grade <= 5; grade++) courses.add(course(SubjectCategory.SOCIAL, "사회" + grade, grade, null, false, 2));
                courses.add(course(SubjectCategory.SCIENCE, "과학", 4, null, false, 4));
                courses.add(course(SubjectCategory.SCIENCE, "진로과학", null, AchievementLevel.C, true, careerCredits));
                courses.add(course(SubjectCategory.SOCIAL, "한국사", 2, null, false, 30));
                courses.add(course(SubjectCategory.SCIENCE, "평가정보 없는 과학", null, AchievementLevel.A, false, 100));
                courses.add(new VerifyGradeRequest.CourseGrade(3, 2, SubjectCategory.SCIENCE, "미반영학기 과학", 1,
                    null, null, null, null, 100, false, false, new BigDecimal("100")));
                var result = evaluator.verify(rule, new VerifyGradeRequest(1L, courses));
                // 실제 저장 결과 내보내기처럼 JSON 복원 후 중간값을 만든다.
                var mapper = new JsonMapper();
                result = mapper.readValue(mapper.writeValueAsString(result), GradeVerificationResponse.class);
                var summaries = batch.buildIntermediateCalculations(rule, result);
                var social = find(summaries, TukSubjectCalculations.INQUIRY, "사회");
                var science = find(summaries, TukSubjectCalculations.INQUIRY, "과학");
                assertThat(social.totalCredits()).isEqualByComparingTo("10");
                assertThat(science.totalCredits()).isEqualByComparingTo(Integer.toString(4 + careerCredits));
                assertThat(social.selected()).isEqualTo(careerCredits <= 6);
                assertThat(science.selected()).isEqualTo(careerCredits > 6);
                var chosen = find(summaries, TukSubjectCalculations.SUBJECT, careerCredits > 6 ? "과학" : "사회");
                assertThat(chosen.courseCount()).isEqualTo(careerCredits > 6 ? 3 : 4);
                if (careerCredits > 6) {
                    assertThat(chosen.totalCredits()).isEqualByComparingTo("35");
                    assertThat(chosen.averageGrade()).isEqualByComparingTo("2.2857");
                    assertThat(chosen.averageConvertedScore()).isEqualByComparingTo("98.7143");
                }
                try (var workbook = export(rule, result, summaries)) {
                    assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
                    assertThat(workbook.getSheet("성적 산출 중간값")).isNull();
                    Sheet main = workbook.getSheet("학생별 검증 결과");
                    assertThat(value(main, "사회 선택 전 이수단위").getNumericCellValue()).isEqualTo(10);
                    assertThat(value(main, "과학 선택 전 이수단위").getNumericCellValue()).isEqualTo(4 + careerCredits);
                    assertThat(value(main, "선택 탐구교과").getStringCellValue()).isEqualTo(
                        careerCredits == 6 ? "사회(이수단위 동률)" : careerCredits > 6 ? "과학" : "사회");
                    assertThat(value(main, "교과 반영점수").getNumericCellValue()).isEqualTo(result.finalScore().doubleValue());
                    assertThat(main.getRow(2)).extracting(Cell::getStringCellValue)
                        .doesNotContain("등급×이수단위 합", "환산점수×이수단위 합", "총 반영 이수단위");
                    assertThat(main.getRow(2).getLastCellNum()).isEqualTo(main.getRow(3).getLastCellNum());
                    Sheet detail = workbook.getSheet("교과별 산출 근거");
                    var total = detail.getRow(detail.getLastRowNum());
                    assertThat(total.getCell(4).getStringCellValue()).isEqualTo("전체");
                    assertThat(total.getCell(8).getNumericCellValue()).isEqualTo(result.calculationSummary().convertedScoreTimesCreditsSum().doubleValue());
                }
            }
        }
    }

    @Test void showsSpecializedAllCoursesAndOtherSubjectsWithoutInquiryComparison() throws Exception {
        var rule = rule(2027, "전체 모집단위");
        var courses = new ArrayList<VerifyGradeRequest.CourseGrade>();
        for (int index = 0; index < 6; index++) courses.add(course(SubjectCategory.OTHER, "전문과목" + index, 3, null, false, 3));
        courses.add(course(SubjectCategory.SCIENCE, "과학", 5, null, false, 2));
        courses.add(course(SubjectCategory.SCIENCE, "진로과학", null, AchievementLevel.A, true, 10));
        var result = evaluator.verify(rule, new VerifyGradeRequest(1L, courses));
        var summaries = batch.buildIntermediateCalculations(rule, result);
        assertThat(find(summaries, TukSubjectCalculations.SUBJECT, "기타").courseCount()).isEqualTo(6);
        assertThat(summaries).noneMatch(value -> TukSubjectCalculations.INQUIRY.equals(value.groupType()));
        try (var workbook = export(rule, result, summaries)) {
            Sheet main = workbook.getSheet("학생별 검증 결과");
            assertThat(value(main, "반영 방식").getStringCellValue()).isEqualTo("석차등급 있는 전 과목");
            assertThat(value(main, "선택 탐구교과").getStringCellValue()).isEqualTo("전 교과 반영");
            assertThat(value(main, "사회 선택 전 이수단위").getStringCellValue()).isEmpty();
            assertThat(value(main, "기타 반영 과목수").getNumericCellValue()).isEqualTo(6);
            assertThat(value(main, "기타 가중평균등급").getNumericCellValue()).isEqualTo(3);
            assertThat(value(main, "기타 가중평균환산점수").getNumericCellValue()).isEqualTo(98);
        }
    }

    private XSSFWorkbook export(EvaluationRule rule, GradeVerificationResponse result,
        List<TranscriptBatchVerificationResult.IntermediateCalculation> summaries) throws Exception {
        var application = new TransferApplicationRow(2, rule.getAdmissionYear(), "TEST-001", null,
            rule.getAdmissionType(), null, "합성 모집단위", 2026);
        var success = new TranscriptBatchVerificationResult.Success(application, "합성", result, List.of(), summaries, null);
        byte[] file = new TranscriptValidationExcelWriter().write("synthetic.xlsx", "TUK_SOURCE_WORKBOOK_V1", "한국공학대학교",
            1, result.calculations().size(), List.of(), List.of(), List.of(), List.of(),
            new TranscriptBatchVerificationResult(List.of(success), List.of()));
        return new XSSFWorkbook(new ByteArrayInputStream(file));
    }

    private Cell value(Sheet sheet, String header) {
        for (Cell cell : sheet.getRow(2)) if (header.equals(cell.getStringCellValue())) return sheet.getRow(3).getCell(cell.getColumnIndex());
        throw new AssertionError("Missing header: " + header);
    }

    private TranscriptBatchVerificationResult.IntermediateCalculation find(
        List<TranscriptBatchVerificationResult.IntermediateCalculation> values, String type, String name) {
        return values.stream().filter(value -> value.groupType().equals(type) && value.groupName().equals(name)).findFirst().orElseThrow();
    }

    private VerifyGradeRequest.CourseGrade course(SubjectCategory subject, String name, Integer grade, AchievementLevel achievement, boolean career, int credits) {
        return new VerifyGradeRequest.CourseGrade(1, 1, subject, name, grade, achievement, null, null, null, 100, career, false, BigDecimal.valueOf(credits));
    }

    private EvaluationRule rule(int year, String unit) {
        boolean all = unit.equals("전체 모집단위");
        var rule = EvaluationRule.create(University.create("TUK", "한국공학대학교"), "합성 규칙", year,
            all ? "학생부교과(특성화고교졸업자)" : "학생부교과(교과우수자)", unit, 1,
            numbers("1,1,1"), numbers(all ? "1,1,1,1,1,1" : "1,1,1,1,1,0"), numbers("100,99,98,97,96,94,80,60,25"),
            all ? SelectionStrategy.ALL_COURSES : SelectionStrategy.CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N,
            all ? 0 : 4, all ? 0 : 2, 0, ScoreAggregation.COURSE_SCORE_AVERAGE,
            all ? AchievementConversion.EXCLUDE : AchievementConversion.DIRECT_TABLE, false, true, true, false, false,
            4, RoundingMode.HALF_UP, 4, RoundingMode.HALF_UP, new BigDecimal("5"),
            numbers("1,2,4"), numbers("100,99,97"), List.of(3,4,5,1,2,6), "합성", "1", null, null);
        rule.markVerified("test", null); rule.publish("test", null); return rule;
    }

    private List<BigDecimal> numbers(String values) { return Arrays.stream(values.split(",")).map(BigDecimal::new).toList(); }
}
