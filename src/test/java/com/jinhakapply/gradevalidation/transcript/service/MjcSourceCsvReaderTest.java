package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MjcSourceCsvReaderTest {

    @TempDir Path directory;
    private final MjcSourceCsvReader reader = new MjcSourceCsvReader();

    @Test
    void readsQuotedApplicantAndBaseInfoCsv() throws Exception {
        Path applicants = write("01.csv", """
            \uFEFF"examNumber","admissionTypeCode","admissionTypeName","recruitmentUnitCode","recruitmentUnitName","highSchoolCode","graduationDate","graduationStatus"
            "MJC-SYN-001","0105","특별[일반고]","1200203","컴퓨터공학과","SYN-HS","20260201","졸업예정"
            """);
        Path baseInfo = write("04.csv", """
            "examNumber","graduateYear","graduateGrade","specializedSchoolYN","applicantScCode"
            "MJC-SYN-001","2025","2","N","1"
            """);

        var applicantRows = reader.readApplicants(applicants);
        var baseRows = reader.readBaseInfo(baseInfo);

        assertThat(applicantRows).singleElement().satisfies(row -> {
            assertThat(row.admissionTypeCode()).isEqualTo("0105");
            assertThat(row.recruitmentUnitName()).isEqualTo("컴퓨터공학과");
        });
        assertThat(baseRows.get("MJC-SYN-001").graduateGrade()).isEqualTo(2);
    }

    @Test
    void streamsCoursesAndSkipsNonPositiveCredits() throws Exception {
        Path subjects = write("05.csv", """
            "examNumber","grade","term","organizationName","subjectName","unit","rankingGrade","achievement","originalScore","avgScore","standardDeviation","studentCount","rank","sameRank","subjectSeparationCode"
            "MJC-SYN-001","1","1","국어","화법과 작문","3","2","\\N","90","80","10","100","\\N","\\N","01"
            "MJC-SYN-001","2","1","과학","과학탐구실험","2","\\N","A","95","80","8","100","\\N","\\N","02"
            "MJC-SYN-001","3","1","체육","운동과 건강","0","\\N","P","\\N","\\N","\\N","100","\\N","\\N","03"
            """);
        List<MjcSourceCsvReader.CourseRow> rows = new ArrayList<>();

        var result = reader.streamCourses(subjects, 2, rows::addAll);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.importedRows()).isEqualTo(2);
        assertThat(result.skippedRows()).isEqualTo(1);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).subjectCategory()).isEqualTo(SubjectCategory.KOREAN);
        assertThat(rows.get(1).careerSubject()).isTrue();
    }

    @Test
    void parsesEscapedQuotesAndEmptyValues() {
        assertThat(MjcSourceCsvReader.parseLine("\"과목,명\",\"A\"\"B\",\"\""))
            .containsExactly("과목,명", "A\"B", "");
    }

    private Path write(String name, String content) throws Exception {
        Path path = directory.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}
