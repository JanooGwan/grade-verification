package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.Set;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class VocationalTrainingExcelParserTest {
    private final VocationalTrainingExcelParser parser = new VocationalTrainingExcelParser();

    @Test
    void parsesMarkedSemestersByApplicantNumber() throws Exception {
        MockMultipartFile file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeRow(sheet.createRow(0), new Object[] {
                "입학연도", "모집시기", "대학명", "전형명", "모집학과명", "수험번호", "성명", "고교명",
                "1학년 1학기", "1학년 2학기", "2학년 1학기", "2학년 2학기", "3학년 1학기", "3학년 2학기"
            });
            writeRow(sheet.createRow(1), new Object[] {
                2026, "수시1차", "경복대학교", "특성화고", "간호학과", "K-001", "학생", "일반고",
                null, null, null, null, "직업교육 위탁과정", "직업교육 위탁과정"
            });
            workbook.write(output);
            file = new MockMultipartFile(
                "vocationalTrainingFile", "경복대 직업과정 위탁생.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()
            );
        }

        VocationalTrainingParseResult result = parser.parse(file, 2026);

        assertThat(result.applicantCount()).isEqualTo(1);
        assertThat(result.semesterCount()).isEqualTo(2);
        assertThat(result.semesters("K-001")).isEqualTo(Set.of(
            new VocationalTrainingSemester(3, 1),
            new VocationalTrainingSemester(3, 2)
        ));
    }

    @Test
    void distinguishesSpecializationFromVocationalAndAlternativeEducationInEveryYear() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeRow(sheet.createRow(0), new Object[] {"입학연도", "수험번호",
                "1학년 1학기", "1학년 2학기", "2학년 1학기", "2학년 2학기", "3학년 1학기", "3학년 2학기"});
            writeRow(sheet.createRow(1), new Object[] {2026, "SYNTHETIC-1",
                "대안교육위탁기관", "직업과정위탁교육", "(세부전공) 디자인", "(세부 전공) 컴퓨터",
                "(세부전공) 디자인\n직업교육 위탁과정", "   "});
            workbook.write(output);
            var result = parser.parse(new MockMultipartFile("file", "synthetic.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()), 2026);
            assertThat(result.semesters("SYNTHETIC-1")).containsExactlyInAnyOrder(
                new VocationalTrainingSemester(1, 1), new VocationalTrainingSemester(1, 2),
                new VocationalTrainingSemester(3, 1));
        }
    }

    private void writeRow(Row row, Object[] values) {
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value == null) continue;
            if (value instanceof Number number) row.createCell(index).setCellValue(number.doubleValue());
            else row.createCell(index).setCellValue(value.toString());
        }
    }
}
