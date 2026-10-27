package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;

/** Academic scores only; no interview/practical scores or final applicant ranking. */
final class MjcArchiveScores {
    private static final Set<String> REQUIRED = Set.of("국어", "영어", "수학", "한국사", "사회", "과학");
    private MjcArchiveScores() {}

    static Map<String, String> sourceValues(MjcArchiveApplicant source) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("원본 지원자 ID", source.sourceApplicantId());
        values.put("part", Integer.toString(source.part()));
        values.put("학력 분류", source.academicType());
        if (source.studentRecordId() != null) values.put("학생부 원본 ID", source.studentRecordId());
        if (source.gedRecordId() != null) values.put("검정고시 원본 ID", source.gedRecordId());
        return values;
    }

    static GradeVerificationResponse ged(EvaluationRule rule, String trackName, MjcArchiveApplicant source) {
        if (trackName.contains("일반고") || trackName.contains("연계") || trackName.contains("농어촌")) {
            throw new IllegalArgumentException("해당 전형의 검정고시 성적 적용 여부를 확인해야 합니다.");
        }
        var subjects = source.gedSubjects();
        for (String name : REQUIRED) {
            if (subjects.stream().filter(subject -> name.equals(normalize(subject.name()))).count() != 1) {
                throw new IllegalArgumentException("검정고시 필수 6과목은 각각 한 점수가 필요합니다.");
            }
        }
        if (subjects.stream().map(MjcArchiveApplicant.GedSubject::id).distinct().count() != subjects.size()
            || subjects.stream().map(subject -> normalize(subject.name())).distinct().count() != subjects.size()) {
            throw new IllegalArgumentException("검정고시 과목이 중복되어 자동 합산할 수 없습니다.");
        }
        Map<String, String> values = sourceValues(source);
        BigDecimal totalUnits = BigDecimal.ZERO;
        BigDecimal gradeUnits = BigDecimal.ZERO;
        BigDecimal languageMath = BigDecimal.ZERO;
        BigDecimal socialScience = BigDecimal.ZERO;
        BigDecimal electiveSum = BigDecimal.ZERO;
        BigDecimal electiveBest = null;
        for (var subject : subjects) {
            String name = normalize(subject.name());
            int units = Set.of("국어", "영어", "수학").contains(name) ? 6 : REQUIRED.contains(name) ? 4 : 2;
            int grade = grade(subject.score());
            totalUnits = totalUnits.add(BigDecimal.valueOf(units));
            gradeUnits = gradeUnits.add(BigDecimal.valueOf((long) grade * units));
            values.put("검정고시 과목 " + subject.id(), subject.name() + ": 원점수=" + subject.score().toPlainString()
                + ", 환산등급=" + grade + ", 단위=" + units);
            values.put("동점자 " + name + " 원점수", subject.score().toPlainString());
            if (units == 6) languageMath = languageMath.add(subject.score());
            else if (units == 4) socialScience = socialScience.add(subject.score());
            else {
                electiveSum = electiveSum.add(subject.score());
                electiveBest = electiveBest == null ? subject.score() : electiveBest.max(subject.score());
            }
        }
        values.put("동점자 국어·영어·수학 합", languageMath.toPlainString());
        values.put("동점자 사회·과학·한국사 합", socialScience.toPlainString());
        values.put("동점자 선택과목 합", electiveSum.toPlainString());
        if (electiveBest != null) values.put("동점자 선택과목 우수점수", electiveBest.toPlainString());
        if (source.acceptedDate() != null) values.put("동점자 합격일", source.acceptedDate());
        BigDecimal average = gradeUnits.divide(totalUnits, 5, RoundingMode.HALF_UP);
        int lower = average.intValue();
        int upper = Math.min(lower + 1, 9);
        BigDecimal lowerScore = rule.getGradeScores().get(lower);
        BigDecimal upperScore = rule.getGradeScores().get(upper);
        if (lowerScore == null || upperScore == null) throw new IllegalArgumentException("게시 규칙의 환산표가 없습니다.");
        BigDecimal base = lowerScore.add(upperScore.subtract(lowerScore).multiply(average.subtract(BigDecimal.valueOf(lower))))
            .setScale(5, RoundingMode.HALF_UP);
        BigDecimal beforeRounding = base.multiply(rule.getScoreMultiplier());
        BigDecimal score = beforeRounding.setScale(rule.getFinalScale(), rule.getFinalRounding());
        var summary = new GradeVerificationResponse.CalculationSummary(
            "검정고시 Σ(과목 환산등급 × 단위) / Σ단위 → 소수 5자리 반올림 → 게시 규칙 환산표 → 전형 배율",
            gradeUnits, null, gradeUnits, null, totalUnits, totalUnits, average, base, rule.getScoreMultiplier(),
            beforeRounding, 5, RoundingMode.HALF_UP, rule.getFinalScale(), rule.getFinalRounding(), Map.of());
        return new GradeVerificationResponse(rule.getId(), rule.getName(), rule.getVersion(), rule.getUniversity().getName(),
            trackName, rule.getRecruitmentUnit(), score, base, average, SelectionStrategy.ALL_COURSES,
            rule.getScoreAggregation(), rule.getSourceDocument(), rule.getSourcePages(), subjects.size(), 0, summary,
            List.of(), List.of("검정고시 내신점수만 산출했습니다. 실기·면접 및 최종 순위는 포함하지 않습니다."), values);
    }

    private static String normalize(String name) { return name.replaceAll("\\s", ""); }
    static int grade(BigDecimal score) {
        if (score == null || score.signum() < 0 || score.compareTo(new BigDecimal("100")) > 0) throw MjcArchiveCsv.invalid();
        int[] thresholds = {100, 98, 96, 94, 92, 90, 80, 70};
        for (int i = 0; i < thresholds.length; i++) if (score.compareTo(BigDecimal.valueOf(thresholds[i])) >= 0) return i + 1;
        return 9;
    }
}
