package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;

import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ApplicantSchoolInfoExcelParserTest {
    private final ApplicantSchoolInfoExcelParser parser = new ApplicantSchoolInfoExcelParser();

    @Test
    void classifiesSchoolTypeByApplicantSchoolInformationNotAdmissionTrack() throws Exception {
        ApplicantSchoolInfoParseResult result = parser.parse(workbook());

        assertThat(result.byApplicantNumber().get("GENERAL").educationBackground())
            .isEqualTo(EducationBackground.DOMESTIC_HIGH_SCHOOL);
        assertThat(result.byApplicantNumber().get("GENERAL").highSchoolType())
            .isEqualTo(HighSchoolType.GENERAL);
        assertThat(result.byApplicantNumber().get("SPECIALIZED").highSchoolType())
            .isEqualTo(HighSchoolType.SPECIALIZED);
        assertThat(result.byApplicantNumber().get("COMPREHENSIVE").highSchoolType())
            .isEqualTo(HighSchoolType.COMPREHENSIVE_VOCATIONAL);
        assertThat(result.byApplicantNumber().get("LIFELONG").highSchoolType())
            .isEqualTo(HighSchoolType.LIFELONG_EDUCATION_FACILITY);
        assertThat(result.byApplicantNumber().get("GED").educationBackground())
            .isEqualTo(EducationBackground.GED);
        assertThat(result.byApplicantNumber().get("FOREIGN_LANGUAGE_HIGH").educationBackground())
            .isEqualTo(EducationBackground.DOMESTIC_HIGH_SCHOOL);
    }

    private MockMultipartFile workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            write(sheet.createRow(0), new Object[] {
                "입학연도", "모집시기", "수험번호", "졸업연도", "고교코드", "고교명",
                "학과코드", "고교타입", "고교구분", "지원자_고교구분코드"
            });
            writeInfo(sheet, 1, "GENERAL", "일반고등학교", "인문고", "일반고", "일반계고교");
            writeInfo(sheet, 2, "SPECIALIZED", "직업고등학교", "실업고", "특성화고", "전문계고교");
            writeInfo(sheet, 3, "COMPREHENSIVE", "종합고등학교", "종합", "일반고", "전문계고교");
            writeInfo(sheet, 4, "LIFELONG", "학력인정 평생교육시설", "특수목적", "기타", "일반계고교");
            writeInfo(sheet, 5, "GED", "서울-고등학교졸업자격검정고시", "검정고시", "검정고시", null);
            writeInfo(sheet, 6, "FOREIGN_LANGUAGE_HIGH", "가상외국어고등학교", "외국어고", "특수목적고", "일반계고교");
            workbook.write(output);
            return new MockMultipartFile(
                "schoolInfoFile", "지원자-추가정보.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()
            );
        }
    }

    private void writeInfo(
        Sheet sheet,
        int rowIndex,
        String applicantNumber,
        String schoolName,
        String schoolType,
        String schoolCategory,
        String applicantCategory
    ) {
        write(sheet.createRow(rowIndex), new Object[] {
            2026, 1, applicantNumber, 2026, "SCHOOL-" + rowIndex, schoolName,
            "학과(" + rowIndex + ")", schoolType, schoolCategory, applicantCategory
        });
    }

    private void write(Row row, Object[] values) {
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Number number) row.createCell(index).setCellValue(number.doubleValue());
            else if (value != null) row.createCell(index).setCellValue(value.toString());
        }
    }
}
