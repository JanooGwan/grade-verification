-- 모집요강 원문 재검수 결과에 따라 과도하게 합쳐져 있던 규칙을 정량식 단위로 분리한다.

-- 한국공학대학교: 학년/교과 가중치 없음, 500점 환산, 졸업자는 3-2 포함.
UPDATE evaluation_rule er
JOIN university u ON u.id = er.university_id
SET er.name = '2027 교과우수자 공학계열',
    er.admission_type = '학생부교과(교과우수자)',
    er.recruitment_unit = '공학계열',
    er.selection_strategy = 'CORE_SCIENCE_TOP_N',
    er.apply_grade_weights = FALSE,
    er.include_third_year_second_semester_for_graduates = TRUE,
    er.include_professional_courses = TRUE,
    er.intermediate_scale = 4,
    er.intermediate_rounding = 'HALF_UP',
    er.final_scale = 4,
    er.final_rounding = 'HALF_UP',
    er.score_multiplier = 5.0000,
    er.status = 'DRAFT', er.active = FALSE,
    er.reviewer = NULL, er.review_note = NULL, er.reviewed_at = NULL,
    er.interpretation_note = '공학계열은 국어·영어·수학·과학 교과별 석차등급 상위 4과목과 진로선택 최대 2과목을 반영합니다. 진로선택과목 이수단위는 1로 적용합니다. 졸업예정자는 3학년 1학기, 졸업자는 전 학기를 반영하며 학년·교과 가중치는 없습니다.',
    er.change_summary = '원문 34~36쪽 재검수: 계열 분리, 500점 환산, 학년 가중치 제거, 졸업자 3-2 및 진로선택 단위 보정'
WHERE u.code = 'TUK' AND er.admission_year = 2027 AND er.version = 1;

-- 공통 규칙 복제용: 기존 TUK 규칙을 기준으로 경영·지역균형·특성화고교졸업자 규칙 생성.
INSERT INTO evaluation_rule (
    university_id, name, admission_year, admission_type, recruitment_unit, version,
    grade1_weight, grade2_weight, grade3_weight,
    korean_weight, math_weight, english_weight, social_weight, science_weight, other_weight,
    selection_strategy, selection_count, achievement_selection_count, minimum_course_count,
    score_aggregation, achievement_conversion,
    include_third_year_second_semester, include_third_year_second_semester_for_graduates,
    include_professional_courses, apply_grade_weights, normalize_grade_weights,
    intermediate_scale, intermediate_rounding, final_scale, final_rounding, score_multiplier,
    source_document, source_pages, interpretation_note, change_summary, extraction_id,
    active, status, created_at, updated_at
)
SELECT er.university_id, variants.name, er.admission_year, variants.admission_type, variants.recruitment_unit, 1,
    er.grade1_weight, er.grade2_weight, er.grade3_weight,
    er.korean_weight, er.math_weight, er.english_weight, er.social_weight, er.science_weight, variants.other_weight,
    variants.selection_strategy, er.selection_count, er.achievement_selection_count, er.minimum_course_count,
    er.score_aggregation, variants.achievement_conversion,
    er.include_third_year_second_semester, er.include_third_year_second_semester_for_graduates,
    TRUE, FALSE, FALSE, er.intermediate_scale, er.intermediate_rounding, er.final_scale, er.final_rounding, 5.0000,
    er.source_document, '34-36', variants.note,
    '원문 34~36쪽 재검수 후 전형·계열별 규칙 분리', NULL,
    FALSE, 'DRAFT', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule er
JOIN university u ON u.id = er.university_id AND u.code = 'TUK'
JOIN (
    SELECT '2027 교과우수자 경영학부' name, '학생부교과(교과우수자)' admission_type,
           '경영학부' recruitment_unit, 'CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N' selection_strategy,
           'DIRECT_TABLE' achievement_conversion, 0.0000 other_weight,
           '경영학부는 국어·영어·수학과 사회/과학 중 총 이수단위가 많은 교과를 반영하며 동률이면 사회를 반영합니다.' note
    UNION ALL SELECT '2027 지역균형 공학계열', '학생부교과(지역균형)', '공학계열',
           'CORE_SCIENCE_TOP_N', 'DIRECT_TABLE', 0.0000,
           '공학계열 교과 규칙을 적용하며 학교폭력 조치사항이 있으면 지원 자격 미달입니다.'
    UNION ALL SELECT '2027 지역균형 경영학부', '학생부교과(지역균형)', '경영학부',
           'CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N', 'DIRECT_TABLE', 0.0000,
           '경영학부 교과 규칙을 적용하며 학교폭력 조치사항이 있으면 지원 자격 미달입니다.'
    UNION ALL SELECT '2027 특성화고교졸업자 전 교과', '학생부교과(특성화고교졸업자)', '전체 모집단위',
           'ALL_COURSES', 'EXCLUDE', 1.0000,
           '석차등급이 있는 전 교과목을 반영하고 성취평가제 과목은 제외합니다.'
) variants
WHERE er.admission_year = 2027 AND er.name = '2027 교과우수자 공학계열'
  AND NOT EXISTS (
      SELECT 1 FROM evaluation_rule existing
      WHERE existing.university_id = er.university_id AND existing.admission_year = er.admission_year
        AND existing.admission_type = variants.admission_type
        AND existing.recruitment_unit = variants.recruitment_unit AND existing.version = 1
  );

-- 명지전문대학교: 공통 30/30/40 산식은 유지하되 실제 학생부 배점별로 규칙을 분리한다.
UPDATE evaluation_rule er
JOIN university u ON u.id = er.university_id
SET er.name = '2027 협약통한연계교육 학생부 1000점',
    er.admission_type = '정원내 특별전형(협약통한연계교육)',
    er.recruitment_unit = '협약학과 전체',
    er.achievement_conversion = 'Z_SCORE',
    er.include_professional_courses = TRUE,
    er.intermediate_scale = 5,
    er.intermediate_rounding = 'HALF_UP',
    er.final_scale = 5,
    er.final_rounding = 'HALF_UP',
    er.score_multiplier = 10.0000,
    er.status = 'DRAFT', er.active = FALSE,
    er.interpretation_note = '1·2학년은 우수학기 각 30%, 3학년은 1학기 40%를 반영합니다. 성취평가제 과목은 Z값을 소수 셋째 자리에서 반올림한 뒤 석차백분율과 석차등급으로 환산합니다.',
    er.change_summary = '원문 19·21·35~38쪽 재검수: 항공서비스 400점과 협약학과 1000점 분리, Z값 환산 및 소수 5자리 보정'
WHERE u.code = 'MJC' AND er.admission_year = 2027;

INSERT INTO evaluation_rule (
    university_id, name, admission_year, admission_type, recruitment_unit, version,
    grade1_weight, grade2_weight, grade3_weight,
    korean_weight, math_weight, english_weight, social_weight, science_weight, other_weight,
    selection_strategy, selection_count, achievement_selection_count, minimum_course_count,
    score_aggregation, achievement_conversion,
    include_third_year_second_semester, include_third_year_second_semester_for_graduates,
    include_professional_courses, apply_grade_weights, normalize_grade_weights,
    intermediate_scale, intermediate_rounding, final_scale, final_rounding, score_multiplier,
    source_document, source_pages, interpretation_note, change_summary,
    active, status, created_at, updated_at
)
SELECT er.university_id, '2027 어학우수자 항공서비스 학생부 400점', er.admission_year,
    '정원내 특별전형(어학우수자)', '항공서비스과', 1,
    er.grade1_weight, er.grade2_weight, er.grade3_weight,
    er.korean_weight, er.math_weight, er.english_weight, er.social_weight, er.science_weight, er.other_weight,
    er.selection_strategy, er.selection_count, er.achievement_selection_count, er.minimum_course_count,
    er.score_aggregation, 'Z_SCORE', FALSE, FALSE, TRUE, TRUE, TRUE,
    5, 'HALF_UP', 5, 'HALF_UP', 4.0000,
    er.source_document, '19, 35-38',
    '학생부 40%(400점)와 면접 60%(600점) 전형입니다. 1·2학년 우수학기 각 30%, 3학년 1학기 40%를 적용합니다.',
    '원문 재검수 후 항공서비스과 전용 400점 규칙 분리', FALSE, 'DRAFT', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule er
JOIN university u ON u.id = er.university_id AND u.code = 'MJC'
WHERE er.admission_year = 2027 AND er.name = '2027 협약통한연계교육 학생부 1000점'
  AND NOT EXISTS (
      SELECT 1 FROM evaluation_rule existing WHERE existing.university_id = er.university_id
        AND existing.admission_year = 2027 AND existing.admission_type = '정원내 특별전형(어학우수자)'
        AND existing.recruitment_unit = '항공서비스과' AND existing.version = 1
  );

-- 경복대학교: 학년 가중치가 아니라 5개 학기 중 우수 학기를 선택한다.
UPDATE evaluation_rule er
JOIN university u ON u.id = er.university_id
SET er.name = '2026 일반학과 우수 2개 학기 전 교과',
    er.recruitment_unit = '일반학과',
    er.apply_grade_weights = FALSE,
    er.other_weight = 1.0000,
    er.interpretation_note = '1학년 1학기부터 3학년 1학기까지 5개 학기 중 평균 석차등급이 우수한 2개 학기의 전 교과를 반영합니다. 평균 석차등급은 소수 둘째 자리 이후 절사하여 소수 첫째 자리로 환산합니다.',
    er.change_summary = '원문 45~46쪽 재검수: 학년 가중치 제거, 전 교과 반영, 보건계열 우수 3개 교과 분리',
    er.status = 'DRAFT', er.active = FALSE
WHERE u.code = 'KBOK' AND er.admission_year = 2026;

INSERT INTO evaluation_rule (
    university_id, name, admission_year, admission_type, recruitment_unit, version,
    grade1_weight, grade2_weight, grade3_weight,
    korean_weight, math_weight, english_weight, social_weight, science_weight, other_weight,
    selection_strategy, selection_count, achievement_selection_count, minimum_course_count,
    score_aggregation, achievement_conversion,
    include_third_year_second_semester, include_third_year_second_semester_for_graduates,
    include_professional_courses, apply_grade_weights, normalize_grade_weights,
    intermediate_scale, intermediate_rounding, final_scale, final_rounding, score_multiplier,
    source_document, source_pages, interpretation_note, change_summary,
    active, status, created_at, updated_at
)
SELECT er.university_id, '2026 보건계열 우수 3개 교과', er.admission_year, er.admission_type,
    '간호·치위생·작업치료·임상병리·물리치료', 1,
    er.grade1_weight, er.grade2_weight, er.grade3_weight,
    1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 0.0000,
    'TOP_N_SUBJECTS', 3, 0, 0, 'AVERAGE_GRADE_THEN_SCORE', 'DIRECT_TABLE',
    FALSE, FALSE, TRUE, FALSE, FALSE, 1, 'DOWN', 2, 'HALF_UP', 1.0000,
    er.source_document, '45-46',
    '국어·영어·수학·사회·과학 중 평균 석차등급이 우수한 3개 교과의 전 과목을 반영합니다.',
    '원문 재검수 후 보건계열 우수 3개 교과 전용 규칙 분리',
    FALSE, 'DRAFT', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule er
JOIN university u ON u.id = er.university_id AND u.code = 'KBOK'
WHERE er.admission_year = 2026 AND er.name = '2026 일반학과 우수 2개 학기 전 교과'
  AND NOT EXISTS (
      SELECT 1 FROM evaluation_rule existing WHERE existing.university_id = er.university_id
        AND existing.admission_year = 2026 AND existing.admission_type = er.admission_type
        AND existing.recruitment_unit = '간호·치위생·작업치료·임상병리·물리치료' AND existing.version = 1
  );

-- 삼육대학교: 학년별 차등 없이 전 교과 이수단위 가중 평균.
UPDATE evaluation_rule er
JOIN university u ON u.id = er.university_id
SET er.apply_grade_weights = FALSE,
    er.include_third_year_second_semester = FALSE,
    er.include_third_year_second_semester_for_graduates = FALSE,
    er.intermediate_scale = 5,
    er.final_scale = 5,
    er.status = 'DRAFT', er.active = FALSE,
    er.change_summary = '원문 53~54쪽 재검수: 학년 가중치 제거, 모집단위별 교과영역 및 출결·학교폭력 정책 보정'
WHERE u.code = 'SY' AND er.admission_year = 2027;

UPDATE evaluation_rule er
JOIN university u ON u.id = er.university_id
SET er.admission_type = '학교장추천·농어촌·서해5도',
    er.interpretation_note = '일반학과는 국어·영어·수학·탐구(사회·과학) 교과영역 전 과목을 학년 차등 없이 반영합니다. 2026년 2월 이전 졸업자도 3학년 1학기까지만 반영합니다.'
WHERE u.code = 'SY' AND er.admission_year = 2027 AND er.recruitment_unit = '일반학과(부)';

INSERT INTO evaluation_rule (
    university_id, name, admission_year, admission_type, recruitment_unit, version,
    grade1_weight, grade2_weight, grade3_weight,
    korean_weight, math_weight, english_weight, social_weight, science_weight, other_weight,
    selection_strategy, selection_count, achievement_selection_count, minimum_course_count,
    score_aggregation, achievement_conversion,
    include_third_year_second_semester, include_third_year_second_semester_for_graduates,
    include_professional_courses, apply_grade_weights, normalize_grade_weights,
    intermediate_scale, intermediate_rounding, final_scale, final_rounding, score_multiplier,
    source_document, source_pages, interpretation_note, change_summary,
    active, status, created_at, updated_at
)
SELECT er.university_id, '2027 특성화고교 전용 국영수 전 과목', er.admission_year,
    '특성화고교·특성화고졸재직자', '일반학과(부)', 1,
    er.grade1_weight, er.grade2_weight, er.grade3_weight,
    1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 0.0000,
    'ALL_COURSES', 0, 0, 0, er.score_aggregation, er.achievement_conversion,
    FALSE, FALSE, TRUE, FALSE, FALSE, 5, 'HALF_UP', 5, 'HALF_UP', 1.0000,
    er.source_document, '53-54',
    '특성화고교전형과 특성화고졸재직자전형은 국어·영어·수학 교과영역 전 과목을 반영합니다.',
    '원문 재검수 후 특성화고교 계열 전용 국영수 규칙 분리',
    FALSE, 'DRAFT', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule er
JOIN university u ON u.id = er.university_id AND u.code = 'SY'
WHERE er.admission_year = 2027 AND er.recruitment_unit = '일반학과(부)'
  AND er.admission_type = '학교장추천·농어촌·서해5도'
  AND NOT EXISTS (
      SELECT 1 FROM evaluation_rule existing WHERE existing.university_id = er.university_id
        AND existing.admission_year = 2027 AND existing.admission_type = '특성화고교·특성화고졸재직자'
        AND existing.recruitment_unit = '일반학과(부)' AND existing.version = 1
  );

-- 새 규칙의 석차등급/성취도/우선순위 부속표를 채운다.
INSERT IGNORE INTO evaluation_rule_grade_score (rule_id, grade_value, converted_score)
SELECT er.id, grades.grade_value,
    CASE
      WHEN u.code = 'TUK' THEN ELT(grades.grade_value,100,99,98,97,96,94,80,60,25)
      WHEN u.code = 'MJC' THEN ELT(grades.grade_value,100,90,80,70,60,50,40,30,20)
      WHEN u.code = 'KBOK' THEN ELT(grades.grade_value,100,87.5,75,62.5,50,37.5,25,12.5,0)
      WHEN u.code = 'SY' AND er.recruitment_unit = '약학과' THEN ELT(grades.grade_value,100,99,98,96.5,95,92,85,60,0)
      WHEN u.code = 'SY' THEN ELT(grades.grade_value,100,100,99,99,98,90,90,70,70)
    END
FROM evaluation_rule er
JOIN university u ON u.id = er.university_id
JOIN (
    SELECT 1 grade_value UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) grades
WHERE (u.code = 'TUK' AND er.admission_year = 2027)
   OR (u.code = 'MJC' AND er.admission_year = 2027)
   OR (u.code = 'KBOK' AND er.admission_year = 2026)
   OR (u.code = 'SY' AND er.admission_year = 2027);

INSERT IGNORE INTO evaluation_rule_achievement_grade (rule_id, achievement_level, converted_grade)
SELECT er.id, levels.level, ELT(levels.position, 1, 3, 5)
FROM evaluation_rule er
JOIN university u ON u.id = er.university_id
JOIN (SELECT 'A' level, 1 position UNION ALL SELECT 'B', 2 UNION ALL SELECT 'C', 3) levels
WHERE (u.code IN ('TUK','MJC','SY') AND er.admission_year = 2027)
   OR (u.code = 'KBOK' AND er.admission_year = 2026);

INSERT IGNORE INTO evaluation_rule_achievement_score (rule_id, achievement_level, converted_score)
SELECT er.id, levels.level,
    CASE WHEN u.code = 'TUK' THEN ELT(levels.position,100,99,97)
         WHEN u.code = 'KBOK' THEN ELT(levels.position,100,75,50)
         WHEN u.code = 'SY' THEN ELT(levels.position,100,99,98)
         ELSE 0 END
FROM evaluation_rule er
JOIN university u ON u.id = er.university_id
JOIN (SELECT 'A' level, 1 position UNION ALL SELECT 'B', 2 UNION ALL SELECT 'C', 3) levels
WHERE (u.code IN ('TUK','MJC','SY') AND er.admission_year = 2027)
   OR (u.code = 'KBOK' AND er.admission_year = 2026);

INSERT IGNORE INTO evaluation_rule_subject_priority (rule_id, subject_category, priority_value)
SELECT er.id, categories.category, categories.priority_value
FROM evaluation_rule er
JOIN university u ON u.id = er.university_id
JOIN (
    SELECT 'KOREAN' category, 3 priority_value UNION ALL SELECT 'MATH',2 UNION ALL SELECT 'ENGLISH',4
    UNION ALL SELECT 'SOCIAL',5 UNION ALL SELECT 'SCIENCE',1 UNION ALL SELECT 'OTHER',6
) categories
WHERE (u.code IN ('TUK','MJC','SY') AND er.admission_year = 2027)
   OR (u.code = 'KBOK' AND er.admission_year = 2026);

-- 규칙과 동일한 전형/모집단위 카탈로그를 생성한다.
INSERT IGNORE INTO admission_track (university_id, admission_year, name, active, created_at, updated_at)
SELECT DISTINCT er.university_id, er.admission_year, er.admission_type, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule er
JOIN university u ON u.id = er.university_id
WHERE (u.code IN ('TUK','MJC','SY') AND er.admission_year = 2027)
   OR (u.code = 'KBOK' AND er.admission_year = 2026);

INSERT IGNORE INTO recruitment_unit (admission_track_id, code, name, active, created_at, updated_at)
SELECT at.id, NULL, er.recruitment_unit, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule er
JOIN admission_track at ON at.university_id = er.university_id
  AND at.admission_year = er.admission_year AND at.name = er.admission_type
JOIN university u ON u.id = er.university_id
WHERE ((u.code IN ('TUK','MJC','SY') AND er.admission_year = 2027)
    OR (u.code = 'KBOK' AND er.admission_year = 2026));
