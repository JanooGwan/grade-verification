package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.domain.*;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TukSourceExcelParserTest {
    private final TransferExcelParser parser = new TransferExcelParser();

    @Test void readsSourceProfilesDatesAndCoursesWithoutTreatingPassOrZeroRankAsGrades() throws Exception {
        var file=workbook(false,false);
        assertThat(parser.supports(file)).isTrue();
        var result=parser.parse(file,2026);
        assertThat(result.sourceFormat()).isEqualTo(TukSourceExcelParser.SOURCE_FORMAT);
        assertThat(result.invalidRows()).isZero();
        assertThat(result.applications()).hasSize(3);
        assertThat(result.courses()).hasSize(3);
        assertThat(result.skippedRows()).isEqualTo(1);
        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.applicantProfiles().get("TEST-A").graduationDate()).isEqualTo(LocalDate.of(2026,2,1));
        assertThat(result.applicantProfiles().get("TEST-B").graduationDate()).isEqualTo(LocalDate.of(2024,3,1));
        assertThat(result.applicantProfiles().get("TEST-C").educationBackground()).isEqualTo(EducationBackground.GED);
        assertThat(result.applicantProfiles().get("TEST-B").graduationStatus()).isEqualTo(GraduationStatus.GRADUATE);
        assertThat(result.courses().get(0).subjectCategory()).isEqualTo(SubjectCategory.SOCIAL);
        assertThat(result.courses().get(1).careerSubject()).isTrue();
        assertThat(result.courses().get(1).rankPosition()).isNull();
        assertThat(result.courses().get(2).grade()).isNull();
        assertThat(result.warnings()).anyMatch(s->s.contains("연결되지 않는 과목 1건"));
    }
    @Test void rejectsDuplicateApplicantsAndDoesNotDetectUnrelatedSheetNames() throws Exception {
        assertThatThrownBy(()->parser.parse(workbook(true,false),2026)).isInstanceOfSatisfying(CustomException.class,
            error -> assertThat(error.getDetail()).contains("중복"));
        assertThat(parser.supports(workbook(false,true))).isFalse();
    }
    @Test void requiresExplicitSupportedAdmissionYear() throws Exception {
        assertThatThrownBy(()->parser.parse(workbook(false,false))).isInstanceOf(CustomException.class);
        assertThatThrownBy(()->parser.parse(workbook(false,false),2025)).isInstanceOf(CustomException.class);
    }
    @Test void keepsGraduateStatusWhenApplyingOriginalDateAndEducationType() {
        Student student=Student.create(2026,"TEST","합성",null,null,2026);
        var profile=new TukApplicantProfile(EducationBackground.FOREIGN_HIGH_SCHOOL,HighSchoolType.GENERAL,
            GraduationStatus.GRADUATE,LocalDate.of(2026,1,1));
        profile.applyTo(student);
        assertThat(student.getGraduationStatus()).isEqualTo(GraduationStatus.GRADUATE);
        assertThat(student.getGraduationDate()).isEqualTo(LocalDate.of(2026,1,1));
        student.updateProfile("수정",null,null,2025);
        assertThat(student.getGraduationDate()).isNull();
    }
    @Test void validatesDateAndKeepsCoreFallbackMappings() {
        assertThat(TukSourceExcelParser.graduationDate("20240229")).isEqualTo(LocalDate.of(2024,2,29));
        assertThatThrownBy(()->TukSourceExcelParser.graduationDate("20260230")).isInstanceOf(IllegalArgumentException.class);
        assertThat(TukSourceExcelParser.category(null,"과학 계열")).isEqualTo(SubjectCategory.SCIENCE);
        assertThat(TukSourceExcelParser.category(null,"영어")).isEqualTo(SubjectCategory.ENGLISH);
    }
    @Test void evaluatesPreviousYearSourceAgainst2027RulesWithoutChangingSourceDatesOrNames() throws Exception {
        try (var book = new XSSFWorkbook(workbook(false, false).getInputStream());
             var output = new ByteArrayOutputStream()) {
            book.getSheet("Sheet1").getRow(1).getCell(2).setCellValue("SW 자율전공");
            book.write(output);
            var file = new MockMultipartFile("file", "synthetic.xlsx", null, output.toByteArray());
            assertThat(parser.parse(file, 2026).invalidRows()).isZero();
            var result = parser.parse(file, 2027);
            assertThat(result.invalidRows()).isZero();
            assertThat(result.applications()).allSatisfy(application -> assertThat(application.admissionYear()).isEqualTo(2027));
            assertThat(result.applications().getFirst().recruitmentUnitName()).isEqualTo("SW 자율전공");
            assertThat(result.applicantProfiles().get("TEST-A").graduationDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(result.applicantProfiles().get("TEST-A").graduationStatus()).isEqualTo(GraduationStatus.EXPECTED_GRADUATE);
            assertThat(result.applicantProfiles().get("TEST-B").graduationDate()).isEqualTo(LocalDate.of(2024, 3, 1));
            assertThat(result.warnings()).anyMatch(warning -> warning.contains("지원자 1건") && warning.contains("2027학년도 규칙"));
        }
    }

    private MockMultipartFile workbook(boolean duplicate,boolean badHeaders) throws Exception {
        try(var book=new XSSFWorkbook(); var output=new ByteArrayOutputStream()) {
            Sheet apps=book.createSheet("Sheet1"), courses=book.createSheet("Sheet2");
            row(apps,0,badHeaders ? new Object[]{"기타"} : new Object[]{"수험번호","전형명","모집단위명","계열","고교유형","졸업구분","졸업연월"});
            row(apps,1,"TEST-A","학생부교과(지역균형)","기계공학과","공학","일반계고교","졸업예정",20260201);
            var dateStyle=book.createCellStyle(); dateStyle.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
            apps.getRow(1).getCell(6).setCellStyle(dateStyle);
            row(apps,2,duplicate?"TEST-A":"TEST-B","논술(논술우수자)","경영학전공","경영","일반계고교","졸업",DateUtil.getExcelDate(LocalDate.of(2024,3,1)));
            apps.getRow(2).getCell(6).setCellStyle(dateStyle);
            row(apps,3,"TEST-C","학생부교과(교과우수자)","기계공학과","공학","검정고시 출신","해당없음",20250801);
            row(courses,0,"SUHUMNO","GRADE","TERM","ORGANIZATIONNAME","SUBJECTNAME","ISUUNIT","STDRANKINGGRADE","ACHIEVEMENT","HSBRANK","SAMERANK","STUDENTCOUNT","SUBJECTSEPARATIONCODE","CODENAME");
            row(courses,1,"TEST-A",1,1,"한국사","한국사",3,4,"[NULL]","[NULL]","[NULL]",120,"일반","[NULL]");
            row(courses,2,"TEST-A",2,1,"과학","진로과학",4,"[NULL]","C",0,0,100,"진로","과학");
            row(courses,3,"TEST-A",2,1,"교양","철학",1,"P","[NULL]","[NULL]","[NULL]",100,"일반","[NULL]");
            row(courses,4,"UNLINKED",1,1,"국어","국어",3,4,"[NULL]","[NULL]","[NULL]",100,"일반","국어");
            book.write(output);
            return new MockMultipartFile("file","synthetic.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }
    private static void row(Sheet sheet,int index,Object... values) {
        var row=sheet.createRow(index);
        for(int i=0;i<values.length;i++) {
            var cell=row.createCell(i); Object value=values[i];
            if(value instanceof Number n) cell.setCellValue(n.doubleValue()); else cell.setCellValue(value.toString());
        }
    }
}
