-- V21에서 한신대학교 원본 규칙이 게시되지 않아 V24의 전형별 분리가 실행되지 않은 환경을 보정한다.
CREATE TEMPORARY TABLE tmp_hanshin_2027_source (
    source_rule_id BIGINT NOT NULL PRIMARY KEY,
    university_id BIGINT NOT NULL,
    admission_year INT NOT NULL,
    recruitment_unit VARCHAR(255) NOT NULL
);

INSERT INTO tmp_hanshin_2027_source (
    source_rule_id, university_id, admission_year, recruitment_unit
)
SELECT source.id, source.university_id, source.admission_year, source.recruitment_unit
FROM evaluation_rule source
JOIN university university ON university.id = source.university_id
WHERE university.code = 'HS'
  AND source.admission_year = 2027
  AND source.admission_type = '학생부교과'
  AND source.id = (
      SELECT MIN(candidate.id)
      FROM evaluation_rule candidate
      WHERE candidate.university_id = source.university_id
        AND candidate.admission_year = source.admission_year
        AND candidate.admission_type = '학생부교과'
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
    CONCAT('2027 ', variants.admission_type, ' 교과성적'),
    source.admission_year, variants.admission_type, source.recruitment_unit, 1,
    source.grade1_weight, source.grade2_weight, source.grade3_weight,
    CASE WHEN variants.all_courses THEN 1.0000 ELSE source.korean_weight END,
    CASE WHEN variants.all_courses THEN 1.0000 ELSE source.math_weight END,
    CASE WHEN variants.all_courses THEN 1.0000 ELSE source.english_weight END,
    CASE WHEN variants.all_courses THEN 1.0000 ELSE source.social_weight END,
    CASE WHEN variants.all_courses THEN 1.0000 ELSE source.science_weight END,
    CASE WHEN variants.all_courses THEN 1.0000 ELSE source.other_weight END,
    CASE WHEN variants.all_courses THEN 'ALL_COURSES' ELSE source.selection_strategy END,
    CASE WHEN variants.all_courses THEN 0 ELSE source.selection_count END,
    0, source.minimum_course_count,
    source.score_aggregation, 'EXCLUDE', source.input_grade_scale,
    source.include_third_year_second_semester, source.include_third_year_second_semester_for_graduates,
    variants.all_courses, FALSE, FALSE,
    source.intermediate_scale, source.intermediate_rounding,
    source.final_scale, source.final_rounding, 10.0000,
    source.source_document, '36-38', variants.interpretation_note,
    '2027 수시 모집요강 재검수: 전형별 규칙 분리 누락 보정 및 교과성적 1,000점 정규화', NULL,
    TRUE, 'PUBLISHED', 'guidebook-audit',
    '한신대학교 2027 수시 모집요강 36~38쪽 전형요소 및 학생부 반영방법 재검수',
    CURRENT_TIMESTAMP(6), 'guidebook-audit',
    '전형별 반영 범위 검증 후 게시', CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tmp_hanshin_2027_source selected
JOIN evaluation_rule source ON source.id = selected.source_rule_id
JOIN (
    SELECT '학생부우수자' admission_type, FALSE all_courses,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.' interpretation_note
    UNION ALL SELECT '학교장추천', FALSE,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '사회배려자', FALSE,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '고른기회', FALSE,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '기회균형선발', FALSE,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '농어촌학생', FALSE,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '특성화고교졸업자', TRUE,
        '고교 재학 중 석차등급이 있는 전 교과목을 반영하며 성취평가제 과목은 제외합니다.'
    UNION ALL SELECT '참인재', FALSE,
        '교과성적을 1,000점 만점으로 검증하며 출결과 면접 점수는 별도 합산합니다.'
    UNION ALL SELECT '논술', FALSE,
        '논술전형 환산표로 교과성적을 1,000점 만점으로 검증하며 논술고사 점수는 별도 합산합니다.'
    UNION ALL SELECT '체육실기', FALSE,
        '교과성적을 1,000점 만점으로 검증하며 체육실기 점수는 별도 합산합니다.'
) variants
WHERE NOT EXISTS (
    SELECT 1
    FROM evaluation_rule existing
    WHERE existing.university_id = source.university_id
      AND existing.admission_year = source.admission_year
      AND existing.admission_type = variants.admission_type
      AND existing.recruitment_unit = source.recruitment_unit
      AND existing.version = 1
);

INSERT IGNORE INTO evaluation_rule_grade_score (rule_id, grade_value, converted_score)
SELECT target.id, score.grade_value, score.converted_score
FROM tmp_hanshin_2027_source selected
JOIN evaluation_rule_grade_score score ON score.rule_id = selected.source_rule_id
JOIN evaluation_rule target
  ON target.university_id = selected.university_id
 AND target.admission_year = selected.admission_year
 AND target.recruitment_unit = selected.recruitment_unit
WHERE target.admission_type IN (
    '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
    '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
);

UPDATE evaluation_rule_grade_score score
JOIN evaluation_rule rule ON rule.id = score.rule_id
JOIN university university ON university.id = rule.university_id
SET score.converted_score = CASE score.grade_value
    WHEN 8 THEN 90.0000
    WHEN 9 THEN 85.0000
    ELSE score.converted_score
END
WHERE university.code = 'HS'
  AND rule.admission_year = 2027
  AND rule.admission_type = '논술'
  AND score.grade_value IN (8, 9);

INSERT IGNORE INTO evaluation_rule_achievement_grade (rule_id, achievement_level, converted_grade)
SELECT target.id, conversion.achievement_level, conversion.converted_grade
FROM tmp_hanshin_2027_source selected
JOIN evaluation_rule_achievement_grade conversion ON conversion.rule_id = selected.source_rule_id
JOIN evaluation_rule target
  ON target.university_id = selected.university_id
 AND target.admission_year = selected.admission_year
 AND target.recruitment_unit = selected.recruitment_unit
WHERE target.admission_type IN (
    '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
    '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
);

INSERT IGNORE INTO evaluation_rule_achievement_score (rule_id, achievement_level, converted_score)
SELECT target.id, conversion.achievement_level, conversion.converted_score
FROM tmp_hanshin_2027_source selected
JOIN evaluation_rule_achievement_score conversion ON conversion.rule_id = selected.source_rule_id
JOIN evaluation_rule target
  ON target.university_id = selected.university_id
 AND target.admission_year = selected.admission_year
 AND target.recruitment_unit = selected.recruitment_unit
WHERE target.admission_type IN (
    '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
    '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
);

INSERT IGNORE INTO evaluation_rule_legacy_achievement_grade (rule_id, legacy_achievement, converted_grade)
SELECT target.id, conversion.legacy_achievement, conversion.converted_grade
FROM tmp_hanshin_2027_source selected
JOIN evaluation_rule_legacy_achievement_grade conversion ON conversion.rule_id = selected.source_rule_id
JOIN evaluation_rule target
  ON target.university_id = selected.university_id
 AND target.admission_year = selected.admission_year
 AND target.recruitment_unit = selected.recruitment_unit
WHERE target.admission_type IN (
    '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
    '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
);

INSERT IGNORE INTO evaluation_rule_subject_priority (rule_id, subject_category, priority_value)
SELECT target.id, priority.subject_category, priority.priority_value
FROM tmp_hanshin_2027_source selected
JOIN evaluation_rule_subject_priority priority ON priority.rule_id = selected.source_rule_id
JOIN evaluation_rule target
  ON target.university_id = selected.university_id
 AND target.admission_year = selected.admission_year
 AND target.recruitment_unit = selected.recruitment_unit
WHERE target.admission_type IN (
    '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
    '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
);

UPDATE evaluation_rule source
JOIN tmp_hanshin_2027_source selected ON selected.source_rule_id = source.id
SET source.active = FALSE,
    source.status = 'RETIRED',
    source.retired_by = 'guidebook-audit',
    source.retire_note = '2027 전형별 규칙 분리로 대체',
    source.retired_at = CURRENT_TIMESTAMP(6),
    source.updated_at = CURRENT_TIMESTAMP(6);

INSERT IGNORE INTO admission_track (
    university_id, admission_year, name, active, created_at, updated_at
)
SELECT DISTINCT rule.university_id, rule.admission_year, rule.admission_type,
    TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule rule
JOIN university university ON university.id = rule.university_id
WHERE university.code = 'HS'
  AND rule.admission_year = 2027
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
 AND track.admission_year = rule.admission_year
 AND track.name = rule.admission_type
WHERE university.code = 'HS'
  AND rule.admission_year = 2027
  AND rule.status = 'PUBLISHED';

DROP TEMPORARY TABLE tmp_hanshin_2027_source;
