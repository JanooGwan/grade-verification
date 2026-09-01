-- 명지전문대학 2026학년도 수시모집요강의 학생부·검정고시 산식을
-- 전형별 학생부 배점과 모집단위 유형으로 나눈 게시 규칙으로 등록한다.
UPDATE evaluation_rule rule
JOIN university university ON university.id = rule.university_id
SET rule.active = FALSE,
    rule.status = 'RETIRED',
    rule.retired_by = 'guidebook-audit',
    rule.retire_note = '명지전문대학 2026 전형·학생부 배점별 게시 규칙으로 대체',
    rule.retired_at = CURRENT_TIMESTAMP(6),
    rule.updated_at = CURRENT_TIMESTAMP(6)
WHERE university.code = 'MJC'
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
    CONCAT('2026 ', variants.admission_type, ' ', variants.recruitment_unit, ' 학생부성적'),
    2026, variants.admission_type, variants.recruitment_unit, 2,
    30.0000, 30.0000, 40.0000,
    1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000,
    'BEST_SEMESTER_PER_GRADE', 0, 0, 0,
    'COURSE_SCORE_AVERAGE', 'Z_SCORE', 'NINE_LEVEL',
    FALSE, FALSE, TRUE, TRUE, TRUE,
    5, 'HALF_UP', 5, 'HALF_UP', variants.score_multiplier,
    '(MJC) 2026학년도 신입학 수시 모집요강.pdf', '1, 5-8, 11-28, 34-38',
    CONCAT(
        '전 과목을 이수단위로 가중평균하고 1·2학년은 각각 우수학기 30%, ',
        '3학년은 1학기 40%를 반영합니다. 성취평가제는 Z값 환산자료가 있는 과목만 반영하며 ',
        variants.score_note
    ),
    '2026 모집요강 재검수: 우수학기 30·30·40, 전 과목, Z값, 검정고시 6·4·2단위, 전형별 200·400·1000점 및 학교폭력 8·9호 제한 반영',
    NULL,
    TRUE, 'PUBLISHED', 'guidebook-audit',
    '명지전문대학 공식 2026 수시모집요강 학생부·검정고시 산식 검증', CURRENT_TIMESTAMP(6),
    'guidebook-audit', '2026학년도 전형별 정량 성적검증 규칙 게시', CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university
JOIN (
    SELECT '정원내 일반전형(실기위주)' admission_type, '실기학과' recruitment_unit,
        2.0000 score_multiplier, '학생부 200점으로 환산합니다.' score_note
    UNION ALL SELECT '정원내 일반전형(면접위주)', '항공서비스과',
        4.0000, '학생부 400점으로 환산하고 면접 600점은 별도 평가합니다.'
    UNION ALL SELECT '정원내 특별전형(일반고)', '전체 모집단위',
        10.0000, '학생부 1,000점으로 환산합니다.'
    UNION ALL SELECT '정원내 특별전형(특성화고)', '전체 모집단위',
        10.0000, '학생부 또는 검정고시 성적을 1,000점으로 환산합니다.'
    UNION ALL SELECT '정원내 특별전형(대학자체기준)', '항공서비스과',
        4.0000, '학생부 또는 검정고시 성적을 400점으로 환산하고 면접 600점은 별도 평가합니다.'
    UNION ALL SELECT '정원내 특별전형(협약을통한연계교육)', '전체 모집단위',
        10.0000, '학생부 1,000점으로 환산합니다.'
    UNION ALL SELECT '정원외 특별전형(농어촌학생)', '일반학과',
        10.0000, '학생부 1,000점으로 환산합니다.'
    UNION ALL SELECT '정원외 특별전형(농어촌학생)', '실기학과',
        2.0000, '학생부 200점으로 환산하고 실기 800점은 별도 평가합니다.'
    UNION ALL SELECT '정원외 특별전형(농어촌학생)', '항공서비스과',
        4.0000, '학생부 400점으로 환산하고 면접 600점은 별도 평가합니다.'
    UNION ALL SELECT '정원외 특별전형(기회균형)', '일반학과',
        10.0000, '학생부 또는 검정고시 성적을 1,000점으로 환산합니다.'
    UNION ALL SELECT '정원외 특별전형(기회균형)', '실기학과',
        2.0000, '학생부 또는 검정고시 성적을 200점으로 환산하고 실기 800점은 별도 평가합니다.'
) variants
WHERE university.code = 'MJC'
  AND NOT EXISTS (
      SELECT 1
      FROM evaluation_rule existing
      WHERE existing.university_id = university.id
        AND existing.admission_year = 2026
        AND existing.admission_type = variants.admission_type
        AND existing.recruitment_unit = variants.recruitment_unit
        AND existing.version = 2
  );

INSERT IGNORE INTO evaluation_rule_grade_score (rule_id, grade_value, converted_score)
SELECT rule.id, grades.grade_value, grades.converted_score
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'MJC'
JOIN (
    SELECT 1 grade_value, 100.0000 converted_score
    UNION ALL SELECT 2, 90.0000
    UNION ALL SELECT 3, 80.0000
    UNION ALL SELECT 4, 70.0000
    UNION ALL SELECT 5, 60.0000
    UNION ALL SELECT 6, 50.0000
    UNION ALL SELECT 7, 40.0000
    UNION ALL SELECT 8, 30.0000
    UNION ALL SELECT 9, 20.0000
) grades
WHERE rule.admission_year = 2026
  AND rule.version = 2;

INSERT IGNORE INTO evaluation_rule_subject_priority (rule_id, subject_category, priority_value)
SELECT rule.id, priorities.subject_category, priorities.priority_value
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'MJC'
JOIN (
    SELECT 'KOREAN' subject_category, 1 priority_value
    UNION ALL SELECT 'MATH', 2
    UNION ALL SELECT 'ENGLISH', 3
    UNION ALL SELECT 'SOCIAL', 4
    UNION ALL SELECT 'SCIENCE', 5
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
JOIN university university ON university.id = rule.university_id AND university.code = 'MJC'
WHERE rule.admission_year = 2026
  AND rule.version = 2
  AND rule.status = 'PUBLISHED';

INSERT IGNORE INTO recruitment_unit (
    admission_track_id, code, name, active, created_at, updated_at
)
SELECT track.id, NULL, rule.recruitment_unit,
    TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id AND university.code = 'MJC'
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
  AND track.university_id IN (SELECT id FROM university WHERE code = 'MJC');

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
  AND track.university_id IN (SELECT id FROM university WHERE code = 'MJC');
