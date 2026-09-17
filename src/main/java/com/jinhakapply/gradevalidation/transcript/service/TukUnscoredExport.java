package com.jinhakapply.gradevalidation.transcript.service;

import java.util.List;

import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.admission.service.TukEssayPolicy;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository.ApplicationExportProjection;

/** 저장 점수를 재계산하지 않고, 미산출 지원자의 추가 입력 요건을 확인한다. */
final class TukUnscoredExport {
    private TukUnscoredExport() {}

    static TranscriptBatchVerificationResult.Failure failure(int year, ApplicationExportProjection row) {
        var application = new TransferApplicationRow(row.applicationId(), row.rowNumber(), year,
            row.applicantNumber(), null, row.trackName(), row.unitCode(), row.unitName(), row.graduationYear(), null);
        String code = "UNSCORED_REASON_UNAVAILABLE";
        String reason = "저장된 산출 결과가 없습니다. 당시 실패 사유가 저장되지 않아 별도 확인이 필요합니다.";
        if (row.educationBackground() != null) {
            var background = EducationBackground.valueOf(row.educationBackground());
            var status = row.graduationStatus() == null
                ? (row.graduationYear() != null && row.graduationYear() < year
                    ? GraduationStatus.GRADUATE : GraduationStatus.EXPECTED_GRADUATE)
                : GraduationStatus.valueOf(row.graduationStatus());
            var common = new StudentCommonEvaluationSnapshot(background, HighSchoolType.GENERAL,
                status, row.graduationYear(), null,
                List.of(), List.of(), List.of(), List.of(), row.graduationDate());
            var mode = TukEssayPolicy.mode(year, row.trackName(), common);
            if (mode == TukEssayPolicy.Mode.COMPARISON) {
                code = "ESSAY_SCORE_REQUIRED";
                reason = essayReason(year, row.courseCount());
            } else if (mode == TukEssayPolicy.Mode.GRADUATION_DATE_REQUIRED) {
                code = "GRADUATION_DATE_REQUIRED";
                reason = "논술 비교내신 적용 여부를 확인하려면 졸업연월이 필요합니다.";
            } else if (background != EducationBackground.DOMESTIC_HIGH_SCHOOL) {
                code = "ALTERNATIVE_ACADEMIC_INPUT_REQUIRED";
                reason = background == EducationBackground.GED
                    ? "검정고시 과목별 점수가 필요합니다."
                    : "외국고 출신자의 전형별 대체 환산 입력이 필요합니다.";
            }
        }
        return new TranscriptBatchVerificationResult.Failure(application, row.studentName(), row.courseCount(), code, reason);
    }

    static String essayReason(int year, int courses) {
        return courses > 0
            ? "%d학년도 기준 %d년 2월 및 이전 졸업자 등 비교내신 대상입니다. 학생부가 있어도 논술고사 점수로 환산해야 합니다."
                .formatted(year, TukEssayPolicy.comparisonTranscriptCutoffYear(year))
            : "학생부 과목 데이터가 없으며, 비교내신 산출에 논술고사 점수가 필요합니다.";
    }

    static String category(TranscriptBatchVerificationResult.Failure failure) {
        return switch (failure.code()) {
            case "ESSAY_SCORE_REQUIRED" -> failure.availableCourseCount() > 0
                ? "논술전형 · 학생부 있음" : "논술전형 · 학생부 과목 데이터 없음";
            case "ALTERNATIVE_ACADEMIC_INPUT_REQUIRED" -> failure.reason().contains("검정고시")
                ? "검정고시 출신" : "외국고 등 대체 성적 필요";
            case "GRADUATION_DATE_REQUIRED" -> "졸업연월 확인 필요";
            default -> "기타 미산출 · 확인 필요";
        };
    }
}
