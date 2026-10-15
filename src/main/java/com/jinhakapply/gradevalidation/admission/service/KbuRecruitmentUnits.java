package com.jinhakapply.gradevalidation.admission.service;

import static com.jinhakapply.gradevalidation.global.util.TextNormalizer.normalizePolicyText;

/** 경복대 모집단위를 학생부 배점별 규칙 그룹에 연결한다. */
public final class KbuRecruitmentUnits {
    private KbuRecruitmentUnits() {}

    public static String group(int admissionYear, String admissionType, String unitName) {
        String unit = normalizePolicyText(unitName);
        if (unit.contains("실용음악") || unit.contains("공연예술")) return "실용음악과·공연예술과";
        if (unit.contains("항공서비스") || unit.contains("준오헤어디자인")) {
            return admissionYear == 2027 && normalizePolicyText(admissionType).contains("정시")
                ? "일반학과" : "항공서비스과·준오헤어디자인과";
        }
        if (unit.contains("간호") || unit.contains("치위생") || unit.contains("작업치료")
            || unit.contains("임상병리") || unit.contains("물리치료")) {
            return "간호·치위생·작업치료·임상병리·물리치료";
        }
        return "일반학과";
    }
}
