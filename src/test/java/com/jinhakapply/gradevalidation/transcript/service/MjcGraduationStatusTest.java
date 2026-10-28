package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhakapply.gradevalidation.transcript.service.MjcSourceCsvReader.ApplicantRow;
import com.jinhakapply.gradevalidation.transcript.service.MjcSourceCsvReader.BaseInfoRow;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MjcGraduationStatusTest {
    @ParameterizedTest
    @CsvSource(nullValues = "NULL", textBlock = """
        NULL,20270201,2026,2027,EXPECTED_GRADUATE
        NULL,20260201,2027,2027,GRADUATE
        NULL,2027-02-01,2026,2027,EXPECTED_GRADUATE
        NULL,NULL,2027,2027,EXPECTED_GRADUATE
        NULL,NULL,2026,2027,GRADUATE
        NULL,NULL,NULL,2027,EXPECTED_GRADUATE
        NULL,unavailable,2026,2027,GRADUATE
        '',NULL,2027,2027,EXPECTED_GRADUATE
        UNKNOWN,NULL,2027,2027,EXPECTED_GRADUATE
        졸업예정,20260201,2026,2027,EXPECTED_GRADUATE
        EXPECTED_GRADUATE,20260201,2026,2027,EXPECTED_GRADUATE
        졸업,20270201,2027,2027,GRADUATE
        GRADUATE,20270201,2027,2027,GRADUATE
        NULL,20260201,2025,2026,EXPECTED_GRADUATE
        NULL,NULL,2025,2026,GRADUATE
        """)
    void usesExplicitStatusThenGraduationYearWithSharedUnknownYearDefault(
        String status, String date, Integer baseYear, int admissionYear, String expected) {
        var applicant = new ApplicantRow("SYNTHETIC", "0105", "특별[일반고]", "UNIT", "합성학과",
            null, date, status);
        var base = new BaseInfoRow("SYNTHETIC", baseYear, null, null, null);
        assertThat(MjcSourceImportProcessor.graduationStatus(applicant, base, admissionYear)).isEqualTo(expected);
    }
}
