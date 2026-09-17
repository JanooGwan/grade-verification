package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository.ApplicationExportProjection;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TukUnscoredExportTest {
    @Test void unavailableOriginalApplicationsAreNotReportedAsZeroFailures() throws Exception {
        byte[] bytes = new TranscriptValidationExcelWriter().write("synthetic.xlsx", "TUK", "한국공학대학교",
            0, 0, List.of(), List.of(), List.of(), List.of(),
            new TranscriptBatchVerificationResult(List.of(), List.of(), false));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("검증 요약");
            boolean found = false;
            for (var row : sheet) {
                if (row.getCell(0) != null && "미산출 합계".equals(row.getCell(0).toString())) {
                    assertThat(row.getCell(1).getStringCellValue()).isEqualTo("확인 불가"); found = true;
                }
            }
            assertThat(found).isTrue();
        }
    }

    static ApplicationExportProjection applicant(long id, String track, String background, LocalDate date, int courses) {
        return new ApplicationExportProjection(id, (int) id, "TEST-" + id, "테스트", track, "TEST", "테스트학과",
            date == null ? null : date.getYear(), date, "GRADUATE", background, "GENERAL", courses);
    }

    @Test void usesTheBatchYearAndKeepsUnknownReasonsExplicit() {
        var row = applicant(1, "논술(논술우수자)", "DOMESTIC_HIGH_SCHOOL", LocalDate.of(2025, 2, 1), 20);
        assertThat(TukUnscoredExport.failure(2026, row).code()).isEqualTo("UNSCORED_REASON_UNAVAILABLE");
        assertThat(TukUnscoredExport.failure(2027, row).code()).isEqualTo("ESSAY_SCORE_REQUIRED");
        assertThat(TukUnscoredExport.failure(2027, row).reason()).contains("2025년 2월");
        var missingDate = applicant(2, "논술(논술우수자)", "DOMESTIC_HIGH_SCHOOL", null, 20);
        assertThat(TukUnscoredExport.failure(2027, missingDate).code()).isEqualTo("GRADUATION_DATE_REQUIRED");
        var boundary = applicant(3, "논술(논술우수자)", "DOMESTIC_HIGH_SCHOOL", LocalDate.of(2024, 2, 29), 20);
        assertThat(TukUnscoredExport.failure(2026, boundary).code()).isEqualTo("ESSAY_SCORE_REQUIRED");
        var after = applicant(4, "논술(논술우수자)", "DOMESTIC_HIGH_SCHOOL", LocalDate.of(2024, 3, 1), 20);
        assertThat(TukUnscoredExport.failure(2026, after).code()).isEqualTo("UNSCORED_REASON_UNAVAILABLE");
    }

    @Test void exportsReasonCountsAndDetailsForBothYears() throws Exception {
        for (int year : List.of(2026, 2027)) {
            var failures = new ArrayList<TranscriptBatchVerificationResult.Failure>();
            for (int i = 1; i <= 2; i++) failures.add(TukUnscoredExport.failure(year,
                applicant(i, "논술(논술우수자)", "DOMESTIC_HIGH_SCHOOL", LocalDate.of(year - 2, 2, 1), 10)));
            failures.add(TukUnscoredExport.failure(year,
                applicant(3, "논술(논술우수자)", "GED", LocalDate.of(2025, 1, 1), 0)));
            failures.add(TukUnscoredExport.failure(year,
                applicant(4, "학생부교과(교과우수자)", "GED", LocalDate.of(2025, 1, 1), 0)));
            byte[] bytes = new TranscriptValidationExcelWriter().write("synthetic.xlsx", "TUK", "한국공학대학교",
                4, 20, List.of(), List.of(), List.of(), List.of(),
                new TranscriptBatchVerificationResult(List.of(), failures));
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                var sheet = workbook.getSheet("검증 요약");
                var texts = new ArrayList<String>();
                int groups = 0;
                for (var r : sheet) {
                    for (var c : r) if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) texts.add(c.getStringCellValue());
                    String label = r.getCell(0) == null ? "" : r.getCell(0).toString();
                    if (label.equals("논술전형 · 학생부 있음")) {
                        assertThat(r.getCell(2).getNumericCellValue()).isEqualTo(2);
                        assertThat(r.getCell(3).getStringCellValue()).contains((year - 2) + "년 2월");
                        groups++;
                    }
                    if (label.equals("논술전형 · 학생부 과목 데이터 없음") || label.equals("검정고시 출신")) {
                        assertThat(r.getCell(2).getNumericCellValue()).isEqualTo(1); groups++;
                    }
                    if (label.equals("합계")) assertThat(r.getCell(2).getNumericCellValue()).isEqualTo(4);
                }
                assertThat(groups).isEqualTo(3);
                assertThat(texts).contains("미산출 지원자 상세", "미산출은 불합격을 의미하지 않습니다.");
                assertThat(texts).contains("TEST-1", "TEST-2", "TEST-3", "TEST-4");
            }
        }
    }
}
