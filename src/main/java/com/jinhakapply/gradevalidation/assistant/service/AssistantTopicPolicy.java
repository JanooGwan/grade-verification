package com.jinhakapply.gradevalidation.assistant.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class AssistantTopicPolicy {

    private static final List<String> ADMISSION_CONTEXT_TERMS = List.of(
        "입학", "입시", "대학", "전형", "모집", "지원자", "수험", "학생부", "내신", "석차", "이수단위",
        "졸업", "검정고시", "외국고", "출결", "결석", "지각", "조퇴", "학교폭력", "학폭",
        "환산", "모집요강", "합격", "모집단위", "학년도", "수시", "정시", "지원한", "지원 현황",
        "지원 통계", "수강", "과목 수", "과목별", "업로드", "가져오기", "입학 데이터", "성적 데이터",
        "admission", "transcript", "evaluation_rule",
        "student_application", "university"
    );

    private static final List<String> CLEARLY_OUT_OF_SCOPE_TERMS = List.of(
        "날씨", "기온", "미세먼지", "환율", "주가", "주식", "코인", "비트코인", "가상화폐",
        "경제뉴스", "경제 뉴스", "최신 경제", "주요 경제", "대통령", "국회의원", "스포츠", "축구",
        "야구", "농구", "맛집", "여행지", "레시피", "요리법", "영화 추천", "음악 추천", "연예뉴스",
        "로또", "운세"
    );

    public boolean isInScope(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (CLEARLY_OUT_OF_SCOPE_TERMS.stream().anyMatch(normalized::contains)) {
            return false;
        }
        return ADMISSION_CONTEXT_TERMS.stream().anyMatch(normalized::contains);
    }
}
