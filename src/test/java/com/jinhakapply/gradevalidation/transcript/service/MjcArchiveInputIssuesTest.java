package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MjcArchiveInputIssuesTest {
    @Test
    void separatesClassificationProblemsFromMissingAndMultipleOriginalRecords() {
        String message = MjcArchiveInputIssues.summary(List.of(
            source("ACADEMIC_TYPE_UNRESOLVED"), source("ACADEMIC_TYPE_UNRESOLVED"),
            source("STUDENT_RECORD_MISSING"), source("STUDENT_RECORD_MULTIPLE"),
            source("VALID_GED_RECORD_MISSING"), source("VALID_GED_RECORD_MULTIPLE")));
        assertThat(message).contains("학력 분류 코드 인식 필요 2건", "학생부 원본 미연결 1건",
            "학생부 원본 복수 연결 1건", "유효 검정고시 원본 없음 1건", "유효 검정고시 원본 복수 연결 1건")
            .doesNotContain("SYNTHETIC-PRIVATE-ID");
    }

    @Test
    void reportsNoWarningForKnownCorrectlyLinkedApplicants() {
        assertThat(MjcArchiveInputIssues.summary(List.of(source(null)))).isNull();
        assertThat(MjcArchiveInputIssues.summary(List.of())).isNull();
    }

    @Test
    void failureReasonExplainsTheActualCategory() {
        assertThat(MjcArchiveInputIssues.reason("ACADEMIC_TYPE_UNRESOLVED"))
            .contains("학력 분류 코드", "자료 미수신을 뜻하지 않습니다");
        assertThat(MjcArchiveInputIssues.reason("STUDENT_RECORD_MISSING")).contains("학생부 원본이 없습니다");
        assertThat(MjcArchiveInputIssues.reason("STUDENT_RECORD_MULTIPLE")).contains("학생부 원본이 여러 건");
    }

    @Test
    void distinguishesPreviousCollegeInputsFromMissingHighSchoolRecords() {
        assertThat(MjcArchiveInputIssues.summary(List.of(source("PREVIOUS_COLLEGE_RECORD_REQUIRED"))))
            .contains("전적대학 성적 입력 필요 1건").doesNotContain("학생부 원본 미연결", "기타 원자료");
        assertThat(MjcArchiveInputIssues.reason("PREVIOUS_COLLEGE_RECORD_REQUIRED"))
            .contains("전적대학 성적", "학생부·검정고시 점수로 대체하지 않습니다");
    }

    private static MjcArchiveApplicant source(String issue) {
        return new MjcArchiveApplicant("SYNTHETIC-PRIVATE-ID", "SYNTHETIC-PRIVATE-EXAM", 1,
            "DOMESTIC_HIGH_SCHOOL", "SYNTHETIC-RECORD", null, issue, null, List.of());
    }
}
