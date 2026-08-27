package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Statement;

import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import org.junit.jupiter.api.Test;

class TransferImportServiceTest {

    @Test
    void classifiesCreatedUpdatedUnchangedAndUnknownBatchResultsSeparately() {
        TransferImportService.CourseResult result = TransferImportService.classifyBatchResults(new int[][] {
            {1, 2, 0, Statement.SUCCESS_NO_INFO, Statement.EXECUTE_FAILED}
        });

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.unknown()).isEqualTo(2);
    }

    @Test
    void keepsGraduationStatusInferredFromTransferWhenSchoolInfoHasNoGraduationYear() {
        Student student = Student.create(2027, "A-001", "미등록", null, null, 2026);
        ApplicantSchoolInfoRow schoolInfo = new ApplicantSchoolInfoRow(
            2, 2027, "A-001", null, "S-001", "직업고등학교", "전문학과",
            "실업고", "특성화고", "전문계고교",
            EducationBackground.DOMESTIC_HIGH_SCHOOL, HighSchoolType.SPECIALIZED
        );

        TransferImportService.applySchoolInfo(student, schoolInfo);

        assertThat(student.getGraduationStatus()).isEqualTo(GraduationStatus.GRADUATE);
        assertThat(student.getHighSchoolType()).isEqualTo(HighSchoolType.SPECIALIZED);
    }
}
