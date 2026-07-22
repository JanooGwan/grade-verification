package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TransferExcelParserTest {

    private final TransferExcelParser parser = new TransferExcelParser();

    @Test
    void detectsAndParsesHanshinMultiSheetTransferWorkbook() throws Exception {
        MockMultipartFile file = hanshinWorkbook();

        assertThat(parser.supports(file)).isTrue();
        TransferExcelParseResult result = parser.parse(file);

        assertThat(result.sourceFormat()).isEqualTo("HANSHIN_MULTI_SHEET_V1");
        assertThat(result.applications()).singleElement().satisfies(application -> {
            assertThat(application.admissionYear()).isEqualTo(2026);
            assertThat(application.applicantNumber()).isEqualTo("A-001");
            assertThat(application.admissionTrackName()).isEqualTo("학생부교과");
            assertThat(application.recruitmentUnitCode()).isEqualTo("21");
        });
        assertThat(result.courses()).hasSize(2);
        assertThat(result.courses().getFirst()).satisfies(course -> {
            assertThat(course.applicantNumber()).isEqualTo("A-001");
            assertThat(course.subjectCategory()).isEqualTo(SubjectCategory.MATH);
            assertThat(course.courseName()).isEqualTo("수학");
            assertThat(course.grade()).isEqualTo(5);
            assertThat(course.credits()).isEqualByComparingTo(new BigDecimal("4"));
        });
        assertThat(result.invalidRows()).isZero();
        assertThat(result.warnings()).anyMatch(item -> item.contains("성적값이 없는 1개 행"));
    }

    private MockMultipartFile hanshinWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet applications = workbook.createSheet("vwapplyinfo");
            writeRow(applications.createRow(0), new Object[] {
                "입학연도", "모집시기", "모집시기명", "수험번호", "군ID", "계열", "계열명",
                "전형코드", "전형명", "모집단위코드", "모집단위명", "졸업연도", "동의코드", "동의"
            });
            writeRow(applications.createRow(1), new Object[] {
                2026, 1, "수시", "A-001", 0, 1, "인문", "06", "학생부교과", "21", "한국어문학", 2026, 1, "동의"
            });

            Sheet courses = workbook.createSheet("hsbsubjectscore");
            writeRow(courses.createRow(0), new Object[] {
                "입학연도", "모집시기", "수험번호", "학년", "학기", "편제코드", "편제명", "과목코드",
                "과목명", "이수단위", "석차", "재적수", "동석차", "원점수", "평균", "표준편차", "석차등급", "성취도"
            });
            writeRow(courses.createRow(1), new Object[] {
                2026, 1, "A-001", 1, 2, "11122301010302", "수학", "0000000006", "수학",
                4, 0, 226, 0, 66, 62.5, 21.5, 5, null
            });
            writeRow(courses.createRow(2), new Object[] {
                2026, 1, "A-001", 1, 2, "11122301010999", "기타", "0000000999", "진로와 직업",
                1, 0, 0, 0, 0, 0, 0, 0, null
            });
            workbook.createSheet("CodeFormation");
            workbook.write(output);
            return new MockMultipartFile(
                "file", "한신대-전달양식.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()
            );
        }
    }

    private void writeRow(Row row, Object[] values) {
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Number number) row.createCell(index).setCellValue(number.doubleValue());
            else if (value != null) row.createCell(index).setCellValue(value.toString());
        }
    }
}
