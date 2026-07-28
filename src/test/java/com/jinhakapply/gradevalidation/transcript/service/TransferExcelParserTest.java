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
        assertThat(result.courses()).hasSize(3);
        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.skippedRows()).isZero();
        assertThat(result.courses().getFirst()).satisfies(course -> {
            assertThat(course.applicantNumber()).isEqualTo("A-001");
            assertThat(course.subjectCategory()).isEqualTo(SubjectCategory.MATH);
            assertThat(course.courseName()).isEqualTo("수학");
            assertThat(course.grade()).isEqualTo(5);
            assertThat(course.credits()).isEqualByComparingTo(new BigDecimal("4"));
        });
        assertThat(result.courses().get(2)).satisfies(course -> {
            assertThat(course.subjectCategory()).isEqualTo(SubjectCategory.OTHER);
            assertThat(course.courseName()).isEqualTo("C프로그래밍");
            assertThat(course.professionalCourse()).isTrue();
        });
        assertThat(result.courses().get(1)).satisfies(course -> {
            assertThat(course.courseName()).isEqualTo("진로와 직업");
            assertThat(course.grade()).isNull();
            assertThat(course.achievement()).isNull();
        });
        assertThat(result.invalidRows()).isZero();
        assertThat(result.warnings()).anyMatch(item ->
            item.contains("수치 환산할 수 없는 성적의 1개 행")
                && item.contains("최소 반영 과목 수 충족 여부에서 제외"));
    }

    @Test
    void classifiesOnlyCoreSubjectOrganizationsAsCoreSubjects() throws Exception {
        MockMultipartFile file = hanshinSubjectCategoryWorkbook();

        TransferExcelParseResult result = parser.parse(file);

        assertThat(result.courses()).extracting(TranscriptExcelRow::courseName, TranscriptExcelRow::subjectCategory)
            .containsExactly(
                org.assertj.core.api.Assertions.tuple("기술·가정", SubjectCategory.OTHER),
                org.assertj.core.api.Assertions.tuple("일본어Ⅰ", SubjectCategory.OTHER),
                org.assertj.core.api.Assertions.tuple("한문Ⅰ", SubjectCategory.OTHER),
                org.assertj.core.api.Assertions.tuple("한국지리", SubjectCategory.SOCIAL),
                org.assertj.core.api.Assertions.tuple("세계지리", SubjectCategory.SOCIAL),
                org.assertj.core.api.Assertions.tuple("세계사", SubjectCategory.SOCIAL),
                org.assertj.core.api.Assertions.tuple("교육학", SubjectCategory.OTHER),
                org.assertj.core.api.Assertions.tuple("미술 창작", SubjectCategory.OTHER),
                org.assertj.core.api.Assertions.tuple("운동과 건강", SubjectCategory.OTHER)
            );
        assertThat(result.courses()).allMatch(course -> !course.professionalCourse());
    }

    @Test
    void classifiesAmbiguousMissingFormationAsNonOrdinaryWhenAnyCatalogFormationIsProfessional() throws Exception {
        MockMultipartFile file = hanshinMissingFormationWorkbook();

        TransferExcelParseResult result = parser.parse(file);

        assertThat(result.courses()).singleElement().satisfies(course -> {
            assertThat(course.courseName()).isEqualTo("환경 화학 기초");
            assertThat(course.subjectCategory()).isEqualTo(SubjectCategory.OTHER);
            assertThat(course.professionalCourse()).isTrue();
        });
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
                1, 0, 0, 0, 0, 0, 0, "P", null
            });
            writeRow(courses.createRow(3), new Object[] {
                2026, 1, "A-001", 2, 1, "000", " ", "0000001000", "C프로그래밍",
                2, 0, 0, 0, 0, 0, 0, 3, null
            });
            workbook.createSheet("CodeFormation");
            workbook.write(output);
            return new MockMultipartFile(
                "file", "한신대-전달양식.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()
            );
        }
    }

    private MockMultipartFile hanshinSubjectCategoryWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet applications = workbook.createSheet("vwapplyinfo");
            writeRow(applications.createRow(0), new Object[] {
                "입학연도", "모집시기", "모집시기명", "수험번호", "군ID", "계열", "계열명",
                "전형코드", "전형명", "모집단위코드", "모집단위명", "졸업연도", "동의코드", "동의"
            });
            writeRow(applications.createRow(1), new Object[] {
                2027, 1, "수시", "TEST-001", 0, 1, "인문", "06", "참인재", "21", "한국어문학", 2027, 1, "동의"
            });

            Sheet courses = workbook.createSheet("hsbsubjectscore");
            writeRow(courses.createRow(0), new Object[] {
                "입학연도", "모집시기", "수험번호", "학년", "학기", "편제코드", "편제명", "과목코드",
                "과목명", "이수단위", "석차", "재적수", "동석차", "원점수", "평균", "표준편차", "석차등급", "성취도"
            });
            writeSubjectRow(courses, 1, "기술·가정/제2외국어/한문/교양", "기술·가정");
            writeSubjectRow(courses, 2, "기술·가정/제2외국어/한문/교양", "일본어Ⅰ");
            writeSubjectRow(courses, 3, "기술·가정/제2외국어/한문/교양", "한문Ⅰ");
            writeSubjectRow(courses, 4, "사회(역사/도덕포함)", "한국지리");
            writeSubjectRow(courses, 5, "사회(역사/도덕포함)", "세계지리");
            writeSubjectRow(courses, 6, "사회(역사/도덕포함)", "세계사");
            writeSubjectRow(courses, 7, "기술·가정/제2외국어/한문/교양", "교육학");
            writeSubjectRow(courses, 8, "예술(음악/미술)", "미술 창작");
            writeSubjectRow(courses, 9, "체육", "운동과 건강");
            workbook.createSheet("CodeFormation");
            workbook.write(output);
            return new MockMultipartFile(
                "file", "한신대-교과분류-검증.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()
            );
        }
    }

    private MockMultipartFile hanshinMissingFormationWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet applications = workbook.createSheet("vwapplyinfo");
            writeRow(applications.createRow(0), new Object[] {
                "입학연도", "모집시기", "모집시기명", "수험번호", "군ID", "계열", "계열명",
                "전형코드", "전형명", "모집단위코드", "모집단위명", "졸업연도", "동의코드", "동의"
            });
            writeRow(applications.createRow(1), new Object[] {
                2027, 1, "수시", "TEST-002", 0, 1, "인문", "10", "기회균형선발",
                "20", "사회복지학", 2027, 1, "동의"
            });

            Sheet courses = workbook.createSheet("hsbsubjectscore");
            writeRow(courses.createRow(0), new Object[] {
                "입학연도", "모집시기", "수험번호", "학년", "학기", "편제코드", "편제명", "과목코드",
                "과목명", "이수단위", "석차", "재적수", "동석차", "원점수", "평균", "표준편차", "석차등급", "성취도"
            });
            writeRow(courses.createRow(1), new Object[] {
                2027, 1, "TEST-002", 1, 1, "000", " ", "0000000619", "환경 화학 기초",
                3, 0, 29, 0, 86, 61.9, 20.3, 3, null
            });

            Sheet formations = workbook.createSheet("CodeFormation");
            writeRow(formations.createRow(0), new Object[] {
                "입학연도", "모집시기", "편제코드", "편제명", "교과코드",
                "교과명", "과목코드", "과목명", "과목구분코드", "과목구분"
            });
            writeRow(formations.createRow(1), new Object[] {
                2027, 1, "011122301010401", "기술·가정/제2외국어/한문/교양", "000",
                " ", "0000000619", "환경 화학 기초", null, null
            });
            writeRow(formations.createRow(2), new Object[] {
                2027, 1, "011122301020215", "환경·안전", "000",
                " ", "0000000619", "환경 화학 기초", null, null
            });
            workbook.write(output);
            return new MockMultipartFile(
                "file", "한신대-편제누락-검증.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()
            );
        }
    }

    private void writeSubjectRow(Sheet sheet, int rowIndex, String organizationName, String courseName) {
        writeRow(sheet.createRow(rowIndex), new Object[] {
            2027, 1, "TEST-001", 1, 1, "000", organizationName, "0000000000", courseName,
            3, 0, 100, 0, 80, 70, 10, 4, null
        });
    }

    private void writeRow(Row row, Object[] values) {
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Number number) row.createCell(index).setCellValue(number.doubleValue());
            else if (value != null) row.createCell(index).setCellValue(value.toString());
        }
    }
}
