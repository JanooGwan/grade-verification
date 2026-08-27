-- V28에서 임시로 2026 기준을 2027 규칙에 덮어쓴 상태를 바로잡고,
-- 한신대학교 교과성적 규칙을 모집연도별로 분리한다.
--
-- 2026: 우수 과목은 최대 12과목까지 반영하되 최소 과목 수에 따른 지원자격 제한 없음
-- 2027: 반영 가능 과목이 12과목 미만이면 지원자격 미달

UPDATE evaluation_rule rule
JOIN university university ON university.id = rule.university_id
SET rule.minimum_course_count = 12,
    rule.source_document = '(수시)2027학년도 한신대 수시 모집요강.pdf',
    rule.source_pages = '36-38',
    rule.interpretation_note = CASE
        WHEN rule.admission_type = '특성화고교졸업자'
            THEN '고교 재학 중 석차등급이 있는 전 과목을 반영하며 성취평가제 과목은 제외합니다. 반영 가능 과목이 12과목 미만이면 지원자격 미달입니다.'
        ELSE '국어·영어·수학·사회(한국사 포함)·과학 교과 중 석차등급 우수 과목을 최대 12과목 반영합니다. 반영 가능 과목이 12과목 미만이면 지원자격 미달입니다.'
    END,
    rule.change_summary = '2027학년도 기준 복구: 반영 가능 과목 12과목 미만 지원자격 미달',
    rule.updated_at = CURRENT_TIMESTAMP(6)
WHERE university.code = 'HS'
  AND rule.admission_year = 2027
  AND rule.status = 'PUBLISHED'
  AND rule.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  );

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
SELECT source.university_id,
    CONCAT('2026 ', source.admission_type, ' 교과성적'),
    2026, source.admission_type, source.recruitment_unit, 1,
    source.grade1_weight, source.grade2_weight, source.grade3_weight,
    source.korean_weight, source.math_weight, source.english_weight,
    source.social_weight, source.science_weight, source.other_weight,
    source.selection_strategy, source.selection_count, source.achievement_selection_count, 0,
    source.score_aggregation, source.achievement_conversion, source.input_grade_scale,
    source.include_third_year_second_semester, source.include_third_year_second_semester_for_graduates,
    source.include_professional_courses, source.apply_grade_weights, source.normalize_grade_weights,
    source.intermediate_scale, source.intermediate_rounding,
    source.final_scale, source.final_rounding, source.score_multiplier,
    '한신대_2026 수시모집요강.pdf', '36-38',
    CASE
        WHEN source.admission_type = '특성화고교졸업자'
            THEN '고교 재학 중 석차등급이 있는 전 과목을 반영하며 성취평가제 과목은 제외합니다. 반영 과목 수에 따른 별도 지원자격 제한은 적용하지 않습니다.'
        ELSE '국어·영어·수학·사회(한국사 포함)·과학 교과 중 석차등급 우수 과목을 최대 12과목 반영합니다. 반영 과목 수에 따른 별도 지원자격 제한은 적용하지 않습니다.'
    END,
    '2026학년도 모집요강 기준 별도 규칙 생성: 최소 12과목 지원자격 조건 없음',
    NULL, TRUE, 'PUBLISHED', 'guidebook-audit',
    '한신대학교 2026 수시 모집요강 36~38쪽 교과성적 반영방법 재검수',
    CURRENT_TIMESTAMP(6), 'guidebook-audit',
    '2026·2027 모집연도별 규칙 분리 후 게시', CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule source
JOIN university university ON university.id = source.university_id
WHERE university.code = 'HS'
  AND source.admission_year = 2027
  AND source.status = 'PUBLISHED'
  AND source.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM evaluation_rule existing
      WHERE existing.university_id = source.university_id
        AND existing.admission_year = 2026
        AND existing.admission_type = source.admission_type
        AND existing.recruitment_unit = source.recruitment_unit
        AND existing.version = 1
  );

INSERT IGNORE INTO evaluation_rule_grade_score (rule_id, grade_value, converted_score)
SELECT target.id, value.grade_value, value.converted_score
FROM evaluation_rule source
JOIN university university ON university.id = source.university_id
JOIN evaluation_rule target
  ON target.university_id = source.university_id
 AND target.admission_year = 2026
 AND target.admission_type = source.admission_type
 AND target.recruitment_unit = source.recruitment_unit
 AND target.version = 1
JOIN evaluation_rule_grade_score value ON value.rule_id = source.id
WHERE university.code = 'HS'
  AND source.admission_year = 2027
  AND source.status = 'PUBLISHED'
  AND source.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  );

INSERT IGNORE INTO evaluation_rule_achievement_grade (rule_id, achievement_level, converted_grade)
SELECT target.id, value.achievement_level, value.converted_grade
FROM evaluation_rule source
JOIN university university ON university.id = source.university_id
JOIN evaluation_rule target
  ON target.university_id = source.university_id
 AND target.admission_year = 2026
 AND target.admission_type = source.admission_type
 AND target.recruitment_unit = source.recruitment_unit
 AND target.version = 1
JOIN evaluation_rule_achievement_grade value ON value.rule_id = source.id
WHERE university.code = 'HS'
  AND source.admission_year = 2027
  AND source.status = 'PUBLISHED'
  AND source.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  );

INSERT IGNORE INTO evaluation_rule_achievement_score (rule_id, achievement_level, converted_score)
SELECT target.id, value.achievement_level, value.converted_score
FROM evaluation_rule source
JOIN university university ON university.id = source.university_id
JOIN evaluation_rule target
  ON target.university_id = source.university_id
 AND target.admission_year = 2026
 AND target.admission_type = source.admission_type
 AND target.recruitment_unit = source.recruitment_unit
 AND target.version = 1
JOIN evaluation_rule_achievement_score value ON value.rule_id = source.id
WHERE university.code = 'HS'
  AND source.admission_year = 2027
  AND source.status = 'PUBLISHED'
  AND source.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  );

INSERT IGNORE INTO evaluation_rule_legacy_achievement_grade (
    rule_id, legacy_achievement, converted_grade
)
SELECT target.id, value.legacy_achievement, value.converted_grade
FROM evaluation_rule source
JOIN university university ON university.id = source.university_id
JOIN evaluation_rule target
  ON target.university_id = source.university_id
 AND target.admission_year = 2026
 AND target.admission_type = source.admission_type
 AND target.recruitment_unit = source.recruitment_unit
 AND target.version = 1
JOIN evaluation_rule_legacy_achievement_grade value ON value.rule_id = source.id
WHERE university.code = 'HS'
  AND source.admission_year = 2027
  AND source.status = 'PUBLISHED'
  AND source.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  );

INSERT IGNORE INTO evaluation_rule_subject_priority (
    rule_id, subject_category, priority_value
)
SELECT target.id, value.subject_category, value.priority_value
FROM evaluation_rule source
JOIN university university ON university.id = source.university_id
JOIN evaluation_rule target
  ON target.university_id = source.university_id
 AND target.admission_year = 2026
 AND target.admission_type = source.admission_type
 AND target.recruitment_unit = source.recruitment_unit
 AND target.version = 1
JOIN evaluation_rule_subject_priority value ON value.rule_id = source.id
WHERE university.code = 'HS'
  AND source.admission_year = 2027
  AND source.status = 'PUBLISHED'
  AND source.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  );

INSERT IGNORE INTO admission_track (
    university_id, admission_year, name, active, created_at, updated_at
)
SELECT DISTINCT rule.university_id, 2026, rule.admission_type,
    TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id
WHERE university.code = 'HS'
  AND rule.admission_year = 2026
  AND rule.status = 'PUBLISHED';

INSERT IGNORE INTO recruitment_unit (
    admission_track_id, code, name, active, created_at, updated_at
)
SELECT track.id, NULL, rule.recruitment_unit,
    TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id
JOIN admission_track track
  ON track.university_id = rule.university_id
 AND track.admission_year = 2026
 AND track.name = rule.admission_type
WHERE university.code = 'HS'
  AND rule.admission_year = 2026
  AND rule.status = 'PUBLISHED';
