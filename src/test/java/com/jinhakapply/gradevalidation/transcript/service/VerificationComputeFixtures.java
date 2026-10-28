package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.domain.*;
import com.jinhakapply.gradevalidation.transcript.domain.*;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.springframework.test.util.ReflectionTestUtils;

/** Deterministic synthetic inputs; no source CSV, local server, or database is used. */
final class VerificationComputeFixtures {
    static Fixture create(int year, int applicants, boolean archive) {
        var rules = List.of(rule(year, 7L, "정원내 특별전형(일반고)", "전체 모집단위", "10"),
            rule(year, 8L, "정원내 특별전형(특성화고)", "전체 모집단위", "10"),
            rule(year, 9L, "정원내 일반전형(실기위주)", "실기학과", "2"),
            rule(year, 10L, "정원내 일반전형(면접위주)", "항공서비스과", "4"));
        var applications = new ArrayList<TransferApplicationRow>();
        var courses = new ArrayList<TranscriptExcelRow>();
        Map<String, ApplicantSchoolInfoRow> schoolInfo = new LinkedHashMap<>();
        Map<String, MjcArchiveApplicant> metadata = new LinkedHashMap<>();
        for (int index = 1; index <= applicants; index++) {
            String exam = "SYNTHETIC-" + index;
            int kind = index % 10;
            String track = kind == 6 ? "특별[특성화고]" : kind == 8 ? "일반[실기위주]"
                : kind == 9 ? "일반[면접위주]" : kind == 7 ? "전문대학이상[전문학사이상]" : "특별[일반고]";
            String unit = kind == 8 ? "실용음악과" : kind == 9 ? "항공서비스과" : "컴퓨터공학과";
            applications.add(new TransferApplicationRow((long) index, index, year, exam, "SYNTHETIC", track,
                "SYNTHETIC-UNIT", unit, year, "수시"));
            var type = kind == 3 ? HighSchoolType.TWO_YEAR : HighSchoolType.GENERAL;
            schoolInfo.put(exam, new ApplicantSchoolInfoRow(index, year, exam, year, null, null, null, null, null,
                null, EducationBackground.DOMESTIC_HIGH_SCHOOL, type));
            String issue = kind == 5 ? "STUDENT_RECORD_MISSING" : kind == 7 ? "PREVIOUS_COLLEGE_RECORD_REQUIRED" : null;
            if (archive) metadata.put(exam, new MjcArchiveApplicant("SYNTHETIC-A-" + index, exam, 1,
                kind == 6 ? "GED" : "DOMESTIC_HIGH_SCHOOL", kind == 5 ? null : "SYNTHETIC-B-" + index,
                kind == 6 ? "SYNTHETIC-G-" + index : null, issue,
                kind == 6 ? "2026-08-01" : null, kind == 6 ? gedSubjects() : List.of()));
            if (kind == 5 || kind == 6 || kind == 7) continue;
            for (int schoolYear = 1; schoolYear <= 3; schoolYear++) {
                for (int term = 1; term <= 2; term++) {
                    for (int subject = 0; subject < 5; subject++) {
                        int grade = kind == 1 ? 2 : (index + schoolYear + term + subject) % 9 + 1;
                        String sourceText = kind == 4 && schoolYear == 1 && term == 1 && subject == 0 ? "?" : null;
                        boolean zScore = kind == 2 && subject == 0;
                        courses.add(new TranscriptExcelRow(courses.size() + 1, exam, "합성 지원자", null, null, year,
                            schoolYear, term, SubjectCategory.values()[subject], "합성 과목 " + schoolYear + term + subject,
                            zScore || sourceText != null ? null : grade, GradeScale.NINE_LEVEL,
                            zScore ? AchievementLevel.A : null, zScore ? new BigDecimal("95") : null,
                            zScore ? new BigDecimal("80") : null, zScore ? new BigDecimal("10") : null,
                            100, null, null, null, new BigDecimal(subject == 4 ? "0" : "3"),
                            zScore, false, false, sourceText));
                    }
                }
            }
        }
        return new Fixture(year, rules, applications, courses, schoolInfo, metadata);
    }

    static EvaluationRule rule(int year, long id, String track, String unit, String multiplier) {
        var rule = EvaluationRule.create(University.create("MJC", "명지전문대학교"), "합성 규칙", year,
            track, unit, 3, decimals("30", "30", "40"), decimals("1", "1", "1", "1", "1", "1"),
            decimals("100", "90", "80", "70", "60", "50", "40", "30", "20"),
            SelectionStrategy.BEST_SEMESTER_PER_GRADE, 0, 0, 0, ScoreAggregation.COURSE_SCORE_AVERAGE,
            AchievementConversion.Z_SCORE, false, false, true, true, true, 5, RoundingMode.HALF_UP,
            5, RoundingMode.HALF_UP, new BigDecimal(multiplier), decimals("1", "3", "5"), decimals("100", "80", "60"),
            List.of(1, 2, 3, 4, 5, 6), "합성 모집요강", "합성 산출", null, null);
        ReflectionTestUtils.setField(rule, "id", id);
        rule.publish("synthetic", null);
        return rule;
    }

    private static List<MjcArchiveApplicant.GedSubject> gedSubjects() {
        String[] names = {"국어", "영어", "수학", "한국사", "사회", "과학", "도덕"};
        var subjects = new ArrayList<MjcArchiveApplicant.GedSubject>();
        for (int i = 0; i < names.length; i++) subjects.add(new MjcArchiveApplicant.GedSubject(
            "SYNTHETIC-S-" + i, names[i], BigDecimal.valueOf(100 - i * 3L)));
        return subjects;
    }

    private static List<BigDecimal> decimals(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }

    record Fixture(int year, List<EvaluationRule> rules, List<TransferApplicationRow> applications,
        List<TranscriptExcelRow> courses, Map<String, ApplicantSchoolInfoRow> schoolInfo,
        Map<String, MjcArchiveApplicant> metadata) {}
}
