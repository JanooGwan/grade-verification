package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.BatchUpdateException;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MjcSourceImportDiagnosticsTest {
    @Test
    void exposesDatabaseCodesButNeverSqlTextOrBoundValues() {
        var sql = new SQLException("Duplicate entry 'SYNTHETIC-PRIVATE' for key student", "23000", 1062);
        var error = new DataIntegrityViolationException("INSERT SQL WITH SYNTHETIC-PRIVATE", sql);
        String diagnostic = MjcSourceImportDiagnostics.describe(error, MjcSourceImportDiagnostics.Stage.SAVE_STUDENTS);
        assertThat(diagnostic).contains("단계=SAVE_STUDENTS", "SQLState=23000", "DB코드=1062")
            .doesNotContain("SYNTHETIC-PRIVATE", "INSERT SQL", "Duplicate entry");
    }

    @Test
    void followsBatchExceptionCodesWithoutPrintingAnyDatabaseMessage() {
        var batch = new BatchUpdateException("PRIVATE-BATCH", null, 0, new int[0]);
        batch.setNextException(new SQLException("PRIVATE-DETAIL", "22001", 1406));
        String diagnostic = MjcSourceImportDiagnostics.describe(batch, MjcSourceImportDiagnostics.Stage.SAVE_COURSES);
        assertThat(diagnostic).contains("SQLState=22001", "DB코드=1406").doesNotContain("PRIVATE");
    }

    @Test
    void exposesCanonicalRowPartAndAllowlistedColumnReasonWithoutValues() {
        var error = new IllegalArgumentException("canonical-courses.csv 234567행 [part=4]: rankingGrade 값이 석차등급 또는 인식 가능한 비등급 값이 아닙니다.");
        String diagnostic = MjcSourceImportDiagnostics.describe(error, MjcSourceImportDiagnostics.Stage.READ_COURSES);
        assertThat(diagnostic).contains("part=4", "행=234567", "컬럼=rankingGrade", "검사=값이 석차등급");
        var untrusted = new IllegalArgumentException("canonical-courses.csv 2행: SYNTHETIC-PRIVATE 값이 없습니다.");
        assertThat(MjcSourceImportDiagnostics.describe(untrusted, MjcSourceImportDiagnostics.Stage.READ_COURSES))
            .doesNotContain("SYNTHETIC-PRIVATE");
    }

    @Test
    void exposesSourceLocationWithoutIncludingTheUnderlyingValue() {
        var error = MjcSourceImportDiagnostics.source(7, "raw_student_subjects", 25, false,
            new IllegalArgumentException("SYNTHETIC-PRIVATE"));
        String diagnostic = MjcSourceImportDiagnostics.describe(error, MjcSourceImportDiagnostics.Stage.ARCHIVE_VALIDATION);
        assertThat(diagnostic).contains("part=7", "파일종류=raw_student_subjects", "데이터행=25").doesNotContain("SYNTHETIC-PRIVATE");
    }

    @Test
    void reportsCountMismatchAndFixedMissingFileNamesOnly() {
        var count = MjcSourceImportDiagnostics.countMismatch(2, "raw_student_records", 3);
        assertThat(MjcSourceImportDiagnostics.describe(count, MjcSourceImportDiagnostics.Stage.ARCHIVE_VALIDATION))
            .contains("지원자 명세의 원자료 건수 불일치", "part=2");
        var missing = new IllegalArgumentException("MJC_ZIP_MISSING_FILE:parts/current-part-0002-applicants.csv");
        assertThat(MjcSourceImportDiagnostics.describe(missing, MjcSourceImportDiagnostics.Stage.ARCHIVE_VALIDATION))
            .contains("누락파일=parts/current-part-0002-applicants.csv");
        var privateName = new IllegalArgumentException("MJC_ZIP_MISSING_FILE:SYNTHETIC-PRIVATE.csv");
        assertThat(MjcSourceImportDiagnostics.describe(privateName, MjcSourceImportDiagnostics.Stage.ARCHIVE_VALIDATION))
            .doesNotContain("SYNTHETIC-PRIVATE");
    }

    @Test
    void suppressesUnknownExceptionMessagesAndPaths() {
        var error = new IllegalArgumentException("SYNTHETIC-PRIVATE C:/private/applicants.csv 00000001");
        String diagnostic = MjcSourceImportDiagnostics.describe(error, MjcSourceImportDiagnostics.Stage.ARCHIVE_VALIDATION);
        assertThat(diagnostic).contains("유형=IllegalArgumentException", "기존 저장본은 유지됩니다")
            .doesNotContain("SYNTHETIC-PRIVATE", "C:/", "00000001");
    }
}
