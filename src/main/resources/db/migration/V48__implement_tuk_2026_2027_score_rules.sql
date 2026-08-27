-- 한국공학대학교 2026·2027학년도 수시모집요강 32~34쪽의 학생부·논술 비교내신 산식을
-- 연도, 전형, 계열별 게시 규칙으로 등록한다. 두 연도의 교과 산식은 같고 논술 비교내신
-- 적용 졸업연도만 2026은 2024년, 2027은 2025년을 경계로 한다.
INSERT INTO evaluation_rule (
    university_id, name, admission_year, admission_type, recruitment_unit, version,
    grade1_weight, grade2_weight, grade3_weight,
    korean_weight, math_weight, english_weight, social_weight, science_weight, other_weight,
    selection_strategy, selection_count, achievement_selection_count, minimum_course_count,
    score_aggregation, achievement_conversion, input_grade_scale,
    include_third_year_second_semester, include_third_year_second_semester_for_graduates,
    include_professional_courses, apply_grade_weights, normalize_grade_weights,
    intermediate_scale, intermediate_rounding, final_scale, final_rounding, score_multiplier,
    source_document, source_pages, interpretation_note, change_summary, extraction_id,
    active, status, reviewer, review_note, reviewed_at,
    published_by, publication_note, published_at, created_at, updated_at
)
SELECT university.id,
    CONCAT(variants.admission_year, ' ', variants.admission_type, ' ', variants.recruitment_unit, ' 교과성적'),
    variants.admission_year, variants.admission_type, variants.recruitment_unit, 1,
    1.0000, 1.0000, 1.0000,
    1.0000, 1.0000, 1.0000,
    CASE WHEN variants.subject_policy IN ('BUSINESS', 'ALL') THEN 1.0000 ELSE 0.0000 END,
    CASE WHEN variants.subject_policy IN ('ENGINEERING', 'BUSINESS', 'ALL') THEN 1.0000 ELSE 0.0000 END,
    CASE WHEN variants.subject_policy = 'ALL' THEN 1.0000 ELSE 0.0000 END,
    variants.selection_strategy,
    CASE WHEN variants.subject_policy = 'ALL' THEN 0 ELSE 4 END,
    CASE WHEN variants.subject_policy = 'ALL' THEN 0 ELSE 2 END,
    0,
    'COURSE_SCORE_AVERAGE',
    CASE WHEN variants.subject_policy = 'ALL' THEN 'EXCLUDE' ELSE 'DIRECT_TABLE' END,
    'NINE_LEVEL',
    FALSE, TRUE, TRUE, FALSE, FALSE,
    4, 'HALF_UP', 4, 'HALF_UP', variants.score_multiplier,
    CASE variants.admission_year
        WHEN 2026 THEN '한국공학대학교[경기][본교]2026 수시모집.pdf'
        ELSE '(수시)2027학년도 한국공학대 수시 모집요강.pdf'
    END,
    '34-36', variants.interpretation_note,
    '한국공학대학교 2026·2027 모집요강의 교과별 상위과목, 진로선택 단위수, 검정고시, 논술 비교내신 및 배점 반영',
    NULL,
    TRUE, 'PUBLISHED', 'guidebook-audit',
    CONCAT(variants.admission_year, '학년도 한국공학대학교 수시모집요강 32~34쪽 검증'), CURRENT_TIMESTAMP(6),
    'guidebook-audit', '연도·전형·계열별 정량 산출 규칙 게시', CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university
JOIN (
    SELECT 2026 admission_year, '논술(논술우수자)' admission_type, '공학계열' recruitment_unit,
        'ENGINEERING' subject_policy, 'CORE_SCIENCE_TOP_N' selection_strategy, 1.0000 score_multiplier,
        '국어·영어·수학·과학 교과별 석차등급 상위 4과목과 진로선택 최대 2과목을 반영합니다. 학생부가 없거나 2024년 2월 이전 졸업자는 논술 400점 구간표로 학생부 100점을 환산하고 논술점수와 합산합니다.' interpretation_note
    UNION ALL SELECT 2026, '논술(논술우수자)', '경영학부',
        'BUSINESS', 'CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N', 1.0000,
        '국어·영어·수학과 사회·과학 중 총 이수단위가 많은 교과를 반영합니다. 동률이면 사회를 선택하며, 학생부가 없거나 2024년 2월 이전 졸업자는 논술점수로 비교내신을 산출합니다.'
    UNION ALL SELECT 2026, '학생부교과(교과우수자)', '공학계열',
        'ENGINEERING', 'CORE_SCIENCE_TOP_N', 5.0000,
        '공학계열 교과별 일반과목 상위 4개와 진로선택 최대 2개를 이수단위 가중평균하여 500점 만점으로 환산합니다.'
    UNION ALL SELECT 2026, '학생부교과(교과우수자)', '경영학부',
        'BUSINESS', 'CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N', 5.0000,
        '경영학부는 국어·영어·수학과 사회·과학 중 총 이수단위가 많은 교과를 반영하고 동률이면 사회를 선택하여 500점 만점으로 환산합니다.'
    UNION ALL SELECT 2026, '학생부교과(지역균형)', '공학계열',
        'ENGINEERING', 'CORE_SCIENCE_TOP_N', 5.0000,
        '공학계열 교과 규칙으로 500점 만점 점수를 산출하며 학교폭력 조치사항이 있으면 지원할 수 없습니다.'
    UNION ALL SELECT 2026, '학생부교과(지역균형)', '경영학부',
        'BUSINESS', 'CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N', 5.0000,
        '경영학부 교과 규칙으로 500점 만점 점수를 산출하며 학교폭력 조치사항이 있으면 지원할 수 없습니다.'
    UNION ALL SELECT 2026, '학생부교과(특성화고교졸업자)', '전체 모집단위',
        'ALL', 'ALL_COURSES', 5.0000,
        '석차등급이 있는 전 교과목을 반영하고 성취평가제 과목은 제외하여 500점 만점으로 환산합니다.'
    UNION ALL SELECT 2027, '논술(논술우수자)', '공학계열',
        'ENGINEERING', 'CORE_SCIENCE_TOP_N', 1.0000,
        '국어·영어·수학·과학 교과별 석차등급 상위 4과목과 진로선택 최대 2과목을 반영합니다. 학생부가 없거나 2025년 2월 이전 졸업자는 논술 400점 구간표로 학생부 100점을 환산하고 논술점수와 합산합니다.'
    UNION ALL SELECT 2027, '논술(논술우수자)', '경영학부',
        'BUSINESS', 'CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N', 1.0000,
        '국어·영어·수학과 사회·과학 중 총 이수단위가 많은 교과를 반영합니다. 동률이면 사회를 선택하며, 학생부가 없거나 2025년 2월 이전 졸업자는 논술점수로 비교내신을 산출합니다.'
    UNION ALL SELECT 2027, '학생부교과(교과우수자)', '공학계열',
        'ENGINEERING', 'CORE_SCIENCE_TOP_N', 5.0000,
        '공학계열 교과별 일반과목 상위 4개와 진로선택 최대 2개를 이수단위 가중평균하여 500점 만점으로 환산합니다.'
    UNION ALL SELECT 2027, '학생부교과(교과우수자)', '경영학부',
        'BUSINESS', 'CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N', 5.0000,
        '경영학부는 국어·영어·수학과 사회·과학 중 총 이수단위가 많은 교과를 반영하고 동률이면 사회를 선택하여 500점 만점으로 환산합니다.'
    UNION ALL SELECT 2027, '학생부교과(지역균형)', '공학계열',
        'ENGINEERING', 'CORE_SCIENCE_TOP_N', 5.0000,
        '공학계열 교과 규칙으로 500점 만점 점수를 산출하며 학교폭력 조치사항이 있으면 지원할 수 없습니다.'
    UNION ALL SELECT 2027, '학생부교과(지역균형)', '경영학부',
        'BUSINESS', 'CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N', 5.0000,
        '경영학부 교과 규칙으로 500점 만점 점수를 산출하며 학교폭력 조치사항이 있으면 지원할 수 없습니다.'
    UNION ALL SELECT 2027, '학생부교과(특성화고교졸업자)', '전체 모집단위',
        'ALL', 'ALL_COURSES', 5.0000,
        '석차등급이 있는 전 교과목을 반영하고 성취평가제 과목은 제외하여 500점 만점으로 환산합니다.'
) variants
WHERE university.code = 'TUK'
  AND NOT EXISTS (
      SELECT 1 FROM evaluation_rule existing
      WHERE existing.university_id = university.id
        AND existing.admission_year = variants.admission_year
        AND existing.admission_type = variants.admission_type
        AND existing.recruitment_unit = variants.recruitment_unit
        AND existing.version = 1
  );

INSERT IGNORE INTO evaluation_rule_grade_score (rule_id, grade_value, converted_score)
SELECT rule.id, grades.grade_value, grades.converted_score
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'TUK'
JOIN (
    SELECT 1 grade_value, 100.0000 converted_score
    UNION ALL SELECT 2, 99.0000
    UNION ALL SELECT 3, 98.0000
    UNION ALL SELECT 4, 97.0000
    UNION ALL SELECT 5, 96.0000
    UNION ALL SELECT 6, 94.0000
    UNION ALL SELECT 7, 80.0000
    UNION ALL SELECT 8, 60.0000
    UNION ALL SELECT 9, 25.0000
) grades
WHERE rule.admission_year IN (2026, 2027);

INSERT IGNORE INTO evaluation_rule_achievement_grade (rule_id, achievement_level, converted_grade)
SELECT rule.id, levels.achievement_level, levels.converted_grade
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'TUK'
JOIN (
    SELECT 'A' achievement_level, 1.00 converted_grade
    UNION ALL SELECT 'B', 2.00
    UNION ALL SELECT 'C', 4.00
) levels
WHERE rule.admission_year IN (2026, 2027);

-- V17에서 생성된 2027 규칙의 진로선택 유효등급도 원문 A=1, B=2, C=4에 맞춘다.
UPDATE evaluation_rule_achievement_grade achievement
JOIN evaluation_rule rule ON rule.id = achievement.rule_id
JOIN university university ON university.id = rule.university_id
SET achievement.converted_grade = CASE achievement.achievement_level
        WHEN 'A' THEN 1.00 WHEN 'B' THEN 2.00 WHEN 'C' THEN 4.00
    END
WHERE university.code = 'TUK'
  AND rule.admission_year IN (2026, 2027)
  AND achievement.achievement_level IN ('A', 'B', 'C');

INSERT IGNORE INTO evaluation_rule_achievement_score (rule_id, achievement_level, converted_score)
SELECT rule.id, levels.achievement_level, levels.converted_score
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'TUK'
JOIN (
    SELECT 'A' achievement_level, 100.0000 converted_score
    UNION ALL SELECT 'B', 99.0000
    UNION ALL SELECT 'C', 97.0000
) levels
WHERE rule.admission_year IN (2026, 2027);

INSERT IGNORE INTO evaluation_rule_legacy_achievement_grade (rule_id, legacy_achievement, converted_grade)
SELECT rule.id, levels.legacy_achievement, levels.converted_grade
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'TUK'
JOIN (
    SELECT 'SU' legacy_achievement, 1.00 converted_grade
    UNION ALL SELECT 'WOO', 3.00
    UNION ALL SELECT 'MI', 5.00
    UNION ALL SELECT 'YANG', 7.00
    UNION ALL SELECT 'GA', 9.00
) levels
WHERE rule.admission_year IN (2026, 2027);

INSERT IGNORE INTO evaluation_rule_subject_priority (rule_id, subject_category, priority_value)
SELECT rule.id, priorities.subject_category, priorities.priority_value
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'TUK'
JOIN (
    SELECT 'SOCIAL' subject_category, 1 priority_value
    UNION ALL SELECT 'SCIENCE', 2
    UNION ALL SELECT 'KOREAN', 3
    UNION ALL SELECT 'MATH', 4
    UNION ALL SELECT 'ENGLISH', 5
    UNION ALL SELECT 'OTHER', 6
) priorities
WHERE rule.admission_year IN (2026, 2027);

UPDATE evaluation_rule_subject_priority priority
JOIN evaluation_rule rule ON rule.id = priority.rule_id
JOIN university university ON university.id = rule.university_id
SET priority.priority_value = CASE priority.subject_category
        WHEN 'SOCIAL' THEN 1 WHEN 'SCIENCE' THEN 2 ELSE priority.priority_value
    END
WHERE university.code = 'TUK'
  AND rule.admission_year IN (2026, 2027)
  AND priority.subject_category IN ('SOCIAL', 'SCIENCE');

INSERT IGNORE INTO admission_track (university_id, admission_year, name, active, created_at, updated_at)
SELECT DISTINCT rule.university_id, rule.admission_year, rule.admission_type,
    TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'TUK'
WHERE rule.admission_year IN (2026, 2027) AND rule.status = 'PUBLISHED';

INSERT IGNORE INTO recruitment_unit (admission_track_id, code, name, active, created_at, updated_at)
SELECT track.id, NULL, rule.recruitment_unit, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'TUK'
JOIN admission_track track ON track.university_id = rule.university_id
    AND track.admission_year = rule.admission_year
    AND track.name = rule.admission_type
WHERE rule.admission_year IN (2026, 2027) AND rule.status = 'PUBLISHED';

UPDATE recruitment_unit unit
JOIN admission_track track ON track.id = unit.admission_track_id
JOIN university university ON university.id = track.university_id
JOIN evaluation_rule rule ON rule.university_id = track.university_id
    AND rule.admission_year = track.admission_year
    AND rule.admission_type = track.name
    AND rule.recruitment_unit = unit.name
    AND rule.status = 'PUBLISHED'
SET unit.active = TRUE, unit.updated_at = CURRENT_TIMESTAMP(6)
WHERE university.code = 'TUK' AND track.admission_year IN (2026, 2027);

UPDATE admission_track track
JOIN university university ON university.id = track.university_id
JOIN evaluation_rule rule ON rule.university_id = track.university_id
    AND rule.admission_year = track.admission_year
    AND rule.admission_type = track.name
    AND rule.status = 'PUBLISHED'
SET track.active = TRUE, track.updated_at = CURRENT_TIMESTAMP(6)
WHERE university.code = 'TUK' AND track.admission_year IN (2026, 2027);
