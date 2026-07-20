package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TranscriptExcelParserTest {

    private final TranscriptExcelParser parser = new TranscriptExcelParser();

    @Test
    void parsesValidRowsAndReportsInvalidRows() throws Exception {
        String[] headers = {
            "수험번호", "성명", "출신고교코드", "출신고교명", "졸업연도",
            "학년", "학기", "교과구분", "과목명", "석차등급", "성취도",
            "원점수", "과목평균", "표준편차", "수강자수", "단위수", "진로선택", "전문교과"
        };
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("학생부");
            writeRow(sheet.createRow(0), headers);
            writeRow(sheet.createRow(1), new Object[] {
                "A-001", "홍길동", "S001", "테스트고", 2027,
                2, 1, "과학", "물리학Ⅰ", null, "A",
                95.5, 72.1, 12.3, 120, 3, "Y", "N"
            });
            writeRow(sheet.createRow(2), new Object[] {
                "A-002", "김학생", "S002", "샘플고", 2027,
                1, 1, "국어", "국어", 10, null,
                null, null, null, null, 3, "N", "N"
            });

            TranscriptExcelParseResult result = parser.parse(file(workbook));

            assertThat(result.totalRows()).isEqualTo(2);
            assertThat(result.rows()).hasSize(1);
            assertThat(result.errors()).hasSize(1);
            TranscriptExcelRow row = result.rows().getFirst();
            assertThat(row.applicantNumber()).isEqualTo("A-001");
            assertThat(row.subjectCategory()).isEqualTo(SubjectCategory.SCIENCE);
            assertThat(row.achievement()).isEqualTo(AchievementLevel.A);
            assertThat(row.credits()).isEqualByComparingTo(new BigDecimal("3"));
            assertThat(row.careerSubject()).isTrue();
            assertThat(result.errors().getFirst().rowNumber()).isEqualTo(3);
            assertThat(result.errors().getFirst().reason()).contains("1~9");
        }
    }

    @Test
    void rejectsWorkbookWithoutRequiredHeaders() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("학생부");
            writeRow(sheet.createRow(0), new Object[] {"지원자번호", "학생명"});

            assertThatThrownBy(() -> parser.parse(file(workbook)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_TRANSCRIPT_FILE));
        }
    }

    @Test
    void classifiesOnlyEnglishRelatedForeignLanguageCoursesAsEnglish() throws Exception {
        String[] headers = {
            "수험번호", "성명", "학년", "학기", "교과구분", "과목명", "석차등급", "단위수"
        };
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("학생부");
            writeRow(sheet.createRow(0), headers);
            writeRow(sheet.createRow(1), new Object[] {
                "A-001", "홍길동", 1, 1, "외국어에 관한 교과", "영어회화", 2, 3
            });
            writeRow(sheet.createRow(2), new Object[] {
                "A-001", "홍길동", 1, 1, "제2외국어", "일본어회화", 1, 3
            });

            TranscriptExcelParseResult result = parser.parse(file(workbook));

            assertThat(result.errors()).isEmpty();
            assertThat(result.rows()).extracting(TranscriptExcelRow::subjectCategory)
                .containsExactly(SubjectCategory.ENGLISH, SubjectCategory.OTHER);
        }
    }

    private MockMultipartFile file(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return new MockMultipartFile(
            "file",
            "student-transcript.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            output.toByteArray()
        );
    }

    private void writeRow(Row row, Object[] values) {
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Number number) {
                row.createCell(index).setCellValue(number.doubleValue());
            } else if (value != null) {
                row.createCell(index).setCellValue(value.toString());
            }
        }
    }
}
