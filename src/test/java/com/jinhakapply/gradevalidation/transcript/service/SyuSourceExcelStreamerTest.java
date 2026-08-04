package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.SourceAttendanceRow;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.SourceCourseRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class SyuSourceExcelStreamerTest {
    private final SyuSourceExcelStreamer streamer = new SyuSourceExcelStreamer();

    @Test
    void streamsSyuCourseAndAttendanceSheets() throws Exception {
        Path workbook = createWorkbook();
        try {
            SyuSourceExcelStreamer.SourceScanResult scan = streamer.scan(workbook);
            List<SourceCourseRow> courses = new ArrayList<>();
            List<SourceAttendanceRow> attendance = new ArrayList<>();

            SyuSourceExcelStreamer.StreamResult courseResult = streamer.streamCourses(
                workbook, 10, courses::addAll
            );
            SyuSourceExcelStreamer.StreamResult attendanceResult = streamer.streamAttendance(
                workbook, 10, attendance::addAll
            );

            assertThat(scan.admissionYears()).containsExactly(2026);
            assertThat(scan.applicantNumbers()).containsExactly("1001");
            assertThat(scan.courseRows()).isEqualTo(1);
            assertThat(courseResult.importedRows()).isEqualTo(1);
            assertThat(courseResult.failedRows()).isZero();
            assertThat(courses).singleElement().satisfies(course -> {
                assertThat(course.subjectCategory()).isEqualTo(SubjectCategory.KOREAN);
                assertThat(course.courseName()).isEqualTo("국어");
                assertThat(course.grade()).isEqualTo(2);
                assertThat(course.achievement()).isEqualTo(AchievementLevel.A);
            });
            assertThat(attendanceResult.importedRows()).isEqualTo(1);
            assertThat(attendance).singleElement().satisfies(row -> {
                assertThat(row.absenceDays()).isEqualTo(2);
                assertThat(row.tardyCount()).isEqualTo(3);
                assertThat(row.earlyLeaveCount()).isEqualTo(4);
                assertThat(row.classAbsenceCount()).isEqualTo(5);
            });
        } finally {
            Files.deleteIfExists(workbook);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SYU_SOURCE_TEST_FILE", matches = ".+")
    void scansConfiguredRealSourceWorkbook() {
        Path workbook = Path.of(System.getenv("SYU_SOURCE_TEST_FILE"));

        SyuSourceExcelStreamer.SourceScanResult scan = streamer.scan(workbook);

        assertThat(scan.admissionYears()).containsExactly(2026);
        assertThat(scan.courseRows()).isGreaterThan(800_000);
        assertThat(scan.applicantNumbers()).isNotEmpty();
    }

    private Path createWorkbook() throws Exception {
        Path path = Files.createTempFile("syu-source-test-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var courses = workbook.createSheet("학생부 교과 성적");
            writeRow(courses.createRow(0), List.of(
                "입학연도", "모집시기", "수험번호", "학년", "학기", "편제코드", "편제명", "교과코드",
                "교과명", "과목코드", "과목명", "이수단위", "석차", "재적수", "동석차", "원점수",
                "평균", "표준편차", "석차등급", "성취도"
            ));
            writeRow(courses.createRow(1), List.of(
                "2026", "1", "1001", "1", "1", "01", "보통교과", "01", "국어", "101", "국어",
                "3", "2", "100", "1", "95", "80", "5", "2", "A"
            ));
            var attendance = workbook.createSheet("학생부출결");
            writeRow(attendance.createRow(0), List.of(
                "입학연도", "모집시기", "수험번호", "학년", "수업일수", "결석(질병)", "결석(사고)",
                "결석(기타)", "지각(질병)", "지각(사고)", "지각(기타)", "조퇴(질병)", "조퇴(사고)",
                "조퇴(기타)", "결과(질병)", "결과(사고)", "결과(기타)"
            ));
            writeRow(attendance.createRow(1), List.of(
                "2026", "1", "1001", "1", "190", "0", "2", "0", "0", "3", "0", "0", "4", "0", "0", "5", "0"
            ));
            try (OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
        return path;
    }

    private void writeRow(org.apache.poi.ss.usermodel.Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) row.createCell(index).setCellValue(values.get(index));
    }
}
