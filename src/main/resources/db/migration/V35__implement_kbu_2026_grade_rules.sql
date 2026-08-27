-- 경복대학교 2026학년도 수시·정시 모집요강 42~45쪽을 기준으로
-- 학생부 반영 전형을 모집시기·모집단위 유형별 게시 규칙으로 분리한다.
UPDATE evaluation_rule rule
JOIN university university ON university.id = rule.university_id
SET rule.active = FALSE,
    rule.status = 'RETIRED',
    rule.retired_by = 'guidebook-audit',
    rule.retire_note = '경복대학교 2026 전형·모집단위별 규칙 분리로 대체',
    rule.retired_at = CURRENT_TIMESTAMP(6),
    rule.updated_at = CURRENT_TIMESTAMP(6)
WHERE university.code = 'KBOK'
  AND rule.admission_year = 2026
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
    CONCAT('2026 ', tracks.admission_type, ' ', units.recruitment_unit, ' 교과성적'),
    2026, tracks.admission_type, units.recruitment_unit, 2,
    1.0000, 1.0000, 1.0000,
    1.0000, 1.0000, 1.0000, 1.0000, 1.0000,
    CASE WHEN units.unit_type = 'HEALTH' THEN 0.0000 ELSE 1.0000 END,
    CASE WHEN units.unit_type = 'HEALTH' THEN 'TOP_N_SUBJECTS' ELSE 'TOP_N_SEMESTERS' END,
    CASE WHEN units.unit_type = 'HEALTH' THEN 3 ELSE 2 END,
    0, 0,
    'AVERAGE_GRADE_THEN_SCORE', 'Z_SCORE', 'NINE_LEVEL',
    FALSE, FALSE, TRUE, FALSE, FALSE,
    1, 'DOWN', 2, 'HALF_UP', units.score_multiplier,
    '2026학년도 경복대학교 수시 및 정시 모집요강', '42-45',
    CASE
        WHEN units.unit_type = 'HEALTH' THEN
            '1학년 1학기부터 3학년 1학기까지 국어·영어·수학·사회·과학 중 평균등급이 우수한 3개 교과의 전 과목을 이수단위로 가중평균합니다. 동률이면 이수단위가 많은 교과, 과학·수학·국어·영어·사회 순으로 선택합니다.'
        ELSE
            '1학년 1학기부터 3학년 1학기까지 5개 학기 중 평균등급이 우수한 2개 학기의 전 교과를 이수단위로 가중평균합니다. 동률이면 이수단위가 많은 학기, 3-1·2-2·2-1·1-2·1-1 순으로 선택합니다.'
    END,
    '2026 모집요강 재검수: 우수 학기/교과 선택, 이수단위 동률 기준, 평균등급 소수 첫째 자리 절사, 진로·전문교과 환산 반영',
    NULL,
    TRUE, 'PUBLISHED', 'guidebook-audit',
    '경복대학교 공식 2026 수시·정시 모집요강 42~45쪽 학생부 반영방법 검증',
    CURRENT_TIMESTAMP(6), 'guidebook-audit',
    '학생부 반영 전형의 모집단위별 선택 기준·등급 환산표·반영비율을 검증한 규칙',
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university
CROSS JOIN (
    SELECT '수시 일반' admission_type, 'ALL' unit_scope
    UNION ALL SELECT '수시 일반고', 'ALL'
    UNION ALL SELECT '수시 특성화고', 'ALL'
    UNION ALL SELECT '수시 특기자', 'ALL'
    UNION ALL SELECT '수시 대학자체', 'ALL'
    UNION ALL SELECT '수시 고른기회1', 'ALL'
    UNION ALL SELECT '수시 고른기회2', 'ALL'
    UNION ALL SELECT '수시 기회균형', 'HEALTH_ONLY'
    UNION ALL SELECT '정시 일반(학생부)', 'GENERAL_PRACTICAL'
    UNION ALL SELECT '정시 고른기회2', 'GENERAL_ONLY'
) tracks
CROSS JOIN (
    SELECT 'GENERAL' unit_type, '일반학과' recruitment_unit, 1.0000 score_multiplier
    UNION ALL SELECT 'HEALTH', '간호·치위생·작업치료·임상병리·물리치료', 1.0000
    UNION ALL SELECT 'INTERVIEW', '항공서비스과·준오헤어디자인과', 0.4000
    UNION ALL SELECT 'PRACTICAL', '실용음악과·공연예술과', 0.2000
) units
WHERE university.code = 'KBOK'
  AND (
      tracks.unit_scope = 'ALL'
      OR (tracks.unit_scope = 'HEALTH_ONLY' AND units.unit_type = 'HEALTH')
      OR (tracks.unit_scope = 'GENERAL_ONLY' AND units.unit_type = 'GENERAL')
      OR (tracks.unit_scope = 'GENERAL_PRACTICAL' AND units.unit_type IN ('GENERAL', 'PRACTICAL'))
  )
  AND NOT EXISTS (
      SELECT 1
      FROM evaluation_rule existing
      WHERE existing.university_id = university.id
        AND existing.admission_year = 2026
        AND existing.admission_type = tracks.admission_type
        AND existing.recruitment_unit = units.recruitment_unit
        AND existing.version = 2
  );

INSERT IGNORE INTO evaluation_rule_grade_score (rule_id, grade_value, converted_score)
SELECT rule.id, grades.grade_value, grades.converted_score
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'KBOK'
JOIN (
    SELECT 1 grade_value, 100.0000 converted_score
    UNION ALL SELECT 2, 87.5000
    UNION ALL SELECT 3, 75.0000
    UNION ALL SELECT 4, 62.5000
    UNION ALL SELECT 5, 50.0000
    UNION ALL SELECT 6, 37.5000
    UNION ALL SELECT 7, 25.0000
    UNION ALL SELECT 8, 12.5000
    UNION ALL SELECT 9, 0.0000
) grades
WHERE rule.admission_year = 2026
  AND rule.version = 2;

INSERT IGNORE INTO evaluation_rule_achievement_grade (rule_id, achievement_level, converted_grade)
SELECT rule.id, levels.achievement_level, levels.converted_grade
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'KBOK'
JOIN (
    SELECT 'A' achievement_level, 1.00 converted_grade
    UNION ALL SELECT 'B', 3.00
    UNION ALL SELECT 'C', 5.00
) levels
WHERE rule.admission_year = 2026
  AND rule.version = 2;

INSERT IGNORE INTO evaluation_rule_achievement_score (rule_id, achievement_level, converted_score)
SELECT rule.id, levels.achievement_level, levels.converted_score
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'KBOK'
JOIN (
    SELECT 'A' achievement_level, 100.0000 converted_score
    UNION ALL SELECT 'B', 75.0000
    UNION ALL SELECT 'C', 50.0000
) levels
WHERE rule.admission_year = 2026
  AND rule.version = 2;

INSERT IGNORE INTO evaluation_rule_legacy_achievement_grade (rule_id, legacy_achievement, converted_grade)
SELECT rule.id, levels.legacy_achievement, levels.converted_grade
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'KBOK'
JOIN (
    SELECT 'SU' legacy_achievement, 1.00 converted_grade
    UNION ALL SELECT 'WOO', 3.00
    UNION ALL SELECT 'MI', 5.00
    UNION ALL SELECT 'YANG', 7.00
    UNION ALL SELECT 'GA', 9.00
) levels
WHERE rule.admission_year = 2026
  AND rule.version = 2;

INSERT IGNORE INTO evaluation_rule_subject_priority (rule_id, subject_category, priority_value)
SELECT rule.id, priorities.subject_category, priorities.priority_value
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'KBOK'
JOIN (
    SELECT 'SCIENCE' subject_category, 1 priority_value
    UNION ALL SELECT 'MATH', 2
    UNION ALL SELECT 'KOREAN', 3
    UNION ALL SELECT 'ENGLISH', 4
    UNION ALL SELECT 'SOCIAL', 5
    UNION ALL SELECT 'OTHER', 6
) priorities
WHERE rule.admission_year = 2026
  AND rule.version = 2;

INSERT IGNORE INTO admission_track (
    university_id, admission_year, name, active, created_at, updated_at
)
SELECT DISTINCT rule.university_id, rule.admission_year, rule.admission_type,
    TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'KBOK'
WHERE rule.admission_year = 2026
  AND rule.version = 2
  AND rule.status = 'PUBLISHED';

INSERT IGNORE INTO recruitment_unit (
    admission_track_id, code, name, active, created_at, updated_at
)
SELECT track.id, NULL, rule.recruitment_unit,
    TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'KBOK'
JOIN admission_track track
  ON track.university_id = rule.university_id
 AND track.admission_year = rule.admission_year
 AND track.name = rule.admission_type
WHERE rule.admission_year = 2026
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
WHERE track.admission_year = 2026
  AND track.university_id IN (SELECT id FROM university WHERE code = 'KBOK');

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
WHERE track.admission_year = 2026
  AND track.university_id IN (SELECT id FROM university WHERE code = 'KBOK');
