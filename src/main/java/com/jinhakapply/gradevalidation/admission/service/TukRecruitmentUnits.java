package com.jinhakapply.gradevalidation.admission.service;

import java.util.Set;
import java.util.Map;
import com.jinhakapply.gradevalidation.global.util.TextNormalizer;

/** 모집요강의 실제 모집단위와 교과 산출 그룹 간 대응. 모르는 학과는 추정하지 않는다. */
public final class TukRecruitmentUnits {
    private static final Map<String, String> PREVIOUS_NAMES_2027 = Map.of(
        "sw자율전공", "AI융합 자율전공",
        "전력응용시스템전공", "전기공학전공",
        "미래에너지시스템전공", "에너지공학전공");
    private static final Set<String> BUSINESS = Set.of(
        "경영자율전공", "경영학전공", "데이터사이언스경영전공", "it경영전공");
    private static final Set<String> ENGINEERING = Set.of(
        "컴퓨터공학전공", "소프트웨어전공", "게임공학과", "인공지능학과", "it반도체융합자율전공",
        "전자공학전공", "임베디드시스템전공", "나노반도체공학전공", "반도체시스템전공",
        "스마트기계융합자율전공", "기계공학과", "기계설계전공", "지능형모빌리티전공",
        "메카트로닉스전공", "ai로봇전공", "첨단융합자율전공", "신소재공학과", "생명화학공학과",
        "디자인공학부", "자유전공학부");
    private TukRecruitmentUnits() {}

    /** 과거 원본의 명칭을 검증 기준연도의 대응 모집단위로 연결한다. 원본 값은 보존한다. */
    public static String nameForRuleYear(int year, String unit) {
        return year == 2027
            ? PREVIOUS_NAMES_2027.getOrDefault(TextNormalizer.normalizePolicyText(unit), unit) : unit;
    }

    public static String group(int year, String unit) {
        if (year != 2026 && year != 2027) return null;
        String name = TextNormalizer.normalizePolicyText(nameForRuleYear(year, unit));
        if (BUSINESS.contains(name)) return "경영학부";
        Set<String> renamed = year == 2026
            ? Set.of("sw자율전공", "전력응용시스템전공", "미래에너지시스템전공")
            : Set.of("ai융합자율전공", "전기공학전공", "에너지공학전공");
        return ENGINEERING.contains(name) || renamed.contains(name) ? "공학계열" : null;
    }
}
