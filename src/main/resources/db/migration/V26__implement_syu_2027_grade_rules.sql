-- 삼육대학교 2027 수시모집요강 51~52쪽 기준으로 전형·모집단위별 교과 규칙을 분리한다.
UPDATE evaluation_rule rule
JOIN university university ON university.id = rule.university_id
SET rule.active = FALSE,
    rule.status = 'RETIRED',
    rule.retired_by = 'guidebook-audit',
    rule.retire_note = '삼육대학교 2027 전형·모집단위별 규칙 분리로 대체',
    rule.retired_at = CURRENT_TIMESTAMP(6),
    rule.updated_at = CURRENT_TIMESTAMP(6)
WHERE university.code = 'SY'
  AND rule.admission_year = 2027
  AND rule.status = 'PUBLISHED';

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
    CONCAT('2027 ', variants.admission_type, ' ', variants.recruitment_unit, ' 교과성적'),
    2027, variants.admission_type, variants.recruitment_unit, 2,
    1.0000, 1.0000, 1.0000,
    1.0000, 1.0000, 1.0000,
    CASE WHEN variants.subject_policy = 'SPECIALIZED' THEN 0.0000 ELSE 1.0000 END,
    CASE WHEN variants.subject_policy = 'SPECIALIZED' THEN 0.0000 ELSE 1.0000 END,
    0.0000,
    variants.selection_strategy, variants.selection_count, 0, 0,
    'COURSE_SCORE_AVERAGE', 'DIRECT_TABLE', 'NINE_LEVEL',
    FALSE, FALSE, TRUE, FALSE, FALSE,
    10, 'HALF_UP', 4, 'DOWN', variants.score_multiplier,
    '2027학년도 삼육대학교 수시모집요강', '51-52',
    variants.interpretation_note,
    '모집요강 재검수: 학년 가중치 제거, 교과영역 전 과목 이수단위 가중평균, 전형별 배점과 절사 적용',
    NULL,
    TRUE, 'PUBLISHED', 'guidebook-audit',
    '삼육대학교 2027 수시모집요강 51~52쪽 학생부 반영방법 및 전형별 배점 재검수',
    CURRENT_TIMESTAMP(6), 'guidebook-audit',
    '전형·모집단위별 반영교과, 환산표, 배율, 출결 범위를 검증한 규칙',
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university
JOIN (
    SELECT '학교장추천' admission_type, '일반학과(부)' recruitment_unit,
        'ALL_COURSES' selection_strategy, 0 selection_count, 10.0000 score_multiplier,
        'GENERAL' subject_policy,
        '국어·영어·수학·탐구 교과영역 전 과목을 이수단위로 가중 평균하여 1,000점 만점으로 환산합니다.' interpretation_note
    UNION ALL SELECT '학교장추천', '약학과', 'ALL_COURSES', 0, 10.0000, 'GENERAL',
        '국어·영어·수학·탐구 교과영역 전 과목에 약학과 전용 석차등급 배점표를 적용하여 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '학교장추천', '아트앤디자인학과', 'TOP_N_SUBJECTS', 2, 2.0000, 'GENERAL',
        '국어·영어·수학·탐구 중 성적이 높은 2개 교과영역 전 과목을 반영하여 학생부교과 200점을 산출합니다.'
    UNION ALL SELECT '학교장추천', '체육학과', 'TOP_N_SUBJECTS', 2, 4.0000, 'GENERAL',
        '국어·영어·수학·탐구 중 성적이 높은 2개 교과영역 전 과목을 반영하여 학생부교과 400점을 산출합니다.'
    UNION ALL SELECT '농어촌', '일반학과(부)', 'ALL_COURSES', 0, 10.0000, 'GENERAL',
        '국어·영어·수학·탐구 교과영역 전 과목을 이수단위로 가중 평균하여 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '농어촌', '약학과', 'ALL_COURSES', 0, 10.0000, 'GENERAL',
        '국어·영어·수학·탐구 교과영역 전 과목에 약학과 전용 석차등급 배점표를 적용하여 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '농어촌', '아트앤디자인학과', 'TOP_N_SUBJECTS', 2, 2.0000, 'GENERAL',
        '국어·영어·수학·탐구 중 성적이 높은 2개 교과영역 전 과목을 반영하여 학생부교과 200점을 산출합니다.'
    UNION ALL SELECT '농어촌', '체육학과', 'TOP_N_SUBJECTS', 2, 4.0000, 'GENERAL',
        '국어·영어·수학·탐구 중 성적이 높은 2개 교과영역 전 과목을 반영하여 학생부교과 400점을 산출합니다.'
    UNION ALL SELECT '서해5도', '일반학과(부)', 'ALL_COURSES', 0, 10.0000, 'GENERAL',
        '국어·영어·수학·탐구 교과영역 전 과목을 이수단위로 가중 평균하여 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '특성화고교', '일반학과(부)', 'ALL_COURSES', 0, 10.0000, 'SPECIALIZED',
        '국어·영어·수학 교과영역 전 과목을 이수단위로 가중 평균하여 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '특성화고졸재직자', '일반학과(부)', 'ALL_COURSES', 0, 10.0000, 'SPECIALIZED',
        '국어·영어·수학 교과영역 전 과목을 이수단위로 가중 평균하여 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '예체능인재', '체육학과', 'TOP_N_SUBJECTS', 2, 3.6000, 'GENERAL',
        '상위 2개 교과영역 전 과목의 교과점수 360점과 출결점수 40점을 합산하여 학생부 400점을 산출합니다.'
) variants
WHERE university.code = 'SY'
  AND NOT EXISTS (
      SELECT 1
      FROM evaluation_rule existing
      WHERE existing.university_id = university.id
        AND existing.admission_year = 2027
        AND existing.admission_type = variants.admission_type
        AND existing.recruitment_unit = variants.recruitment_unit
        AND existing.version = 2
  );

INSERT IGNORE INTO evaluation_rule_grade_score (rule_id, grade_value, converted_score)
SELECT rule.id, grades.grade_value,
    CASE
        WHEN rule.recruitment_unit = '약학과' THEN
            ELT(grades.grade_value, 100, 99, 98, 96.5, 95, 92, 85, 60, 0)
        ELSE
            ELT(grades.grade_value, 100, 100, 99, 99, 98, 90, 90, 70, 70)
    END
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'SY'
JOIN (
    SELECT 1 grade_value UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) grades
WHERE rule.admission_year = 2027
  AND rule.version = 2;

INSERT IGNORE INTO evaluation_rule_achievement_grade (rule_id, achievement_level, converted_grade)
SELECT rule.id, levels.achievement_level, levels.converted_grade
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'SY'
JOIN (
    SELECT 'A' achievement_level, 1.00 converted_grade
    UNION ALL SELECT 'B', 3.00
    UNION ALL SELECT 'C', 5.00
) levels
WHERE rule.admission_year = 2027
  AND rule.version = 2;

INSERT IGNORE INTO evaluation_rule_achievement_score (rule_id, achievement_level, converted_score)
SELECT rule.id, levels.achievement_level, levels.converted_score
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'SY'
JOIN (
    SELECT 'A' achievement_level, 100.0000 converted_score
    UNION ALL SELECT 'B', 99.0000
    UNION ALL SELECT 'C', 98.0000
) levels
WHERE rule.admission_year = 2027
  AND rule.version = 2;

INSERT IGNORE INTO evaluation_rule_legacy_achievement_grade (rule_id, legacy_achievement, converted_grade)
SELECT rule.id, levels.legacy_achievement, levels.converted_grade
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'SY'
JOIN (
    SELECT 'SU' legacy_achievement, 1.00 converted_grade
    UNION ALL SELECT 'WOO', 3.00
    UNION ALL SELECT 'MI', 5.00
    UNION ALL SELECT 'YANG', 7.00
    UNION ALL SELECT 'GA', 9.00
) levels
WHERE rule.admission_year = 2027
  AND rule.version = 2;

INSERT IGNORE INTO evaluation_rule_subject_priority (rule_id, subject_category, priority_value)
SELECT rule.id, priorities.subject_category, priorities.priority_value
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'SY'
JOIN (
    SELECT 'KOREAN' subject_category, 1 priority_value
    UNION ALL SELECT 'MATH', 2
    UNION ALL SELECT 'ENGLISH', 3
    UNION ALL SELECT 'SOCIAL', 4
    UNION ALL SELECT 'SCIENCE', 4
    UNION ALL SELECT 'OTHER', 5
) priorities
WHERE rule.admission_year = 2027
  AND rule.version = 2;

INSERT IGNORE INTO admission_track (
    university_id, admission_year, name, active, created_at, updated_at
)
SELECT DISTINCT rule.university_id, rule.admission_year, rule.admission_type,
    TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'SY'
WHERE rule.admission_year = 2027
  AND rule.version = 2
  AND rule.status = 'PUBLISHED';

INSERT IGNORE INTO recruitment_unit (
    admission_track_id, code, name, active, created_at, updated_at
)
SELECT track.id, NULL, rule.recruitment_unit,
    TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'SY'
JOIN admission_track track
  ON track.university_id = rule.university_id
 AND track.admission_year = rule.admission_year
 AND track.name = rule.admission_type
WHERE rule.admission_year = 2027
  AND rule.version = 2
  AND rule.status = 'PUBLISHED';

UPDATE recruitment_unit unit
JOIN admission_track track ON track.id = unit.admission_track_id
LEFT JOIN (
    SELECT DISTINCT university_id, admission_year, admission_type, recruitment_unit
    FROM evaluation_rule
    WHERE status = 'PUBLISHED'
) published
  ON published.university_id = track.university_id
 AND published.admission_year = track.admission_year
 AND published.admission_type = track.name
 AND published.recruitment_unit = unit.name
SET unit.active = (published.university_id IS NOT NULL),
    unit.updated_at = CURRENT_TIMESTAMP(6)
WHERE track.admission_year = 2027
  AND track.university_id IN (SELECT id FROM university WHERE code = 'SY');

UPDATE admission_track track
LEFT JOIN (
    SELECT DISTINCT university_id, admission_year, admission_type
    FROM evaluation_rule
    WHERE status = 'PUBLISHED'
) published
  ON published.university_id = track.university_id
 AND published.admission_year = track.admission_year
 AND published.admission_type = track.name
SET track.active = (published.university_id IS NOT NULL),
    track.updated_at = CURRENT_TIMESTAMP(6)
WHERE track.admission_year = 2027
  AND track.university_id IN (SELECT id FROM university WHERE code = 'SY');
