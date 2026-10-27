package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class MjcArchiveExportTest {
    @Test
    void splitsComparisonSheetsAtCapacityWithoutLosingApplicantsOrCourses() throws Exception {
        List<TranscriptExcelRow> courses = new ArrayList<>();
        List<TranscriptBatchVerificationResult.Failure> failures = new ArrayList<>();
        for (int applicant = 1; applicant <= 3; applicant++) {
            String exam = "SYNTHETIC-" + applicant;
            var application = new TransferApplicationRow(null, applicant, 2027, exam, "0105", "특별[일반고]", "UNIT", "합성학과", 2027, null);
            failures.add(new TranscriptBatchVerificationResult.Failure(application, "합성 지원자", 5,
                "STUDENT_RECORD_MULTIPLE", "복수 학생부 확인 필요"));
            for (int course = 1; course <= 5; course++) courses.add(new TranscriptExcelRow(
                applicant * 10 + course, exam, "합성 지원자", null, null, 2027,
                1, 1, SubjectCategory.KOREAN, "합성과목" + course, 2, GradeScale.NINE_LEVEL,
                null, null, null, null, 100, null, null, null, BigDecimal.ONE, false, false));
        }
        var result = new TranscriptBatchVerificationResult(List.of(), failures);
        var writer = new TranscriptValidationExcelWriter(10);
        byte[] bytes = writer.write("synthetic.zip", MjcSourceArchiveExtractor.SOURCE_FORMAT, "명지전문대학",
            3, 15, List.of(), courses, List.of(), List.of(), result);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            int count = 0;
            for (int page = 1; page <= 3; page++) {
                var sheet = workbook.getSheet(page == 1 ? "학생별 과목 비교" : "학생별 과목 비교 " + page);
                assertThat(sheet).isNotNull();
                assertThat(sheet.getLastRowNum()).isEqualTo(7);
                assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("SYNTHETIC-" + page);
                count += sheet.getLastRowNum() - 2;
            }
            assertThat(count).isEqualTo(15);
            assertThat(workbook.getSheet("검증 요약")).isNotNull();
        }
    }
}
