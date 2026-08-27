-- 한신대학교 2027 수시 모집요강 36~38쪽 기준으로 전형별 교과 배율과 반영 범위를 분리한다.
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
    source.final_scale, source.final_rounding, variants.score_multiplier,
    source.source_document, '36-38', variants.interpretation_note,
    '2027 수시 모집요강 재검수: 전형별 교과 배율과 특성화고교졸업자 전 과목 규칙 분리', NULL,
    TRUE, 'PUBLISHED', 'guidebook-audit',
    '한신대학교 2027 수시 모집요강 36~38쪽 전형요소 및 학생부 반영방법 재검수',
    CURRENT_TIMESTAMP(6), 'guidebook-audit',
    '전형별 교과 배율 및 반영 범위 검증 후 게시', CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule source
JOIN university university ON university.id = source.university_id AND university.code = 'HS'
JOIN (
    SELECT '학생부우수자' admission_type, 10.0000 score_multiplier, FALSE all_courses,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.' interpretation_note
    UNION ALL SELECT '학교장추천', 10.0000, FALSE,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '사회배려자', 10.0000, FALSE,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '고른기회', 10.0000, FALSE,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '기회균형선발', 10.0000, FALSE,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '농어촌학생', 10.0000, FALSE,
        '석차등급 우수 12과목의 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    UNION ALL SELECT '특성화고교졸업자', 10.0000, TRUE,
        '고교 재학 중 석차등급이 있는 전 교과목을 반영하며 성취평가제 과목은 제외합니다.'
    UNION ALL SELECT '참인재', 5.4000, FALSE,
        '학생부 600점 중 교과 540점을 산출합니다. 출결 60점과 면접 400점은 별도 합산합니다.'
    UNION ALL SELECT '논술', 2.0000, FALSE,
        '논술전형 환산표를 적용한 학생부교과 200점을 산출하고 논술고사 800점을 별도 합산합니다.'
    UNION ALL SELECT '체육실기', 4.5000, FALSE,
        '학생부교과 450점을 산출하고 체육실기 550점을 별도 합산합니다.'
) variants
WHERE source.admission_year = 2027
  AND source.admission_type = '학생부교과'
  AND source.status = 'PUBLISHED'
  AND source.id = (
      SELECT MIN(candidate.id)
      FROM evaluation_rule candidate
      WHERE candidate.university_id = source.university_id
        AND candidate.admission_year = source.admission_year
        AND candidate.admission_type = '학생부교과'
        AND candidate.status = 'PUBLISHED'
  )
  AND NOT EXISTS (
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
FROM evaluation_rule target
JOIN university university ON university.id = target.university_id AND university.code = 'HS'
JOIN evaluation_rule source
  ON source.university_id = target.university_id
 AND source.admission_year = target.admission_year
 AND source.admission_type = '학생부교과'
 AND source.status = 'PUBLISHED'
JOIN evaluation_rule_grade_score score ON score.rule_id = source.id
WHERE target.admission_year = 2027
  AND target.version = 1
  AND target.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  )
  AND source.id = (
      SELECT MIN(candidate.id)
      FROM evaluation_rule candidate
      WHERE candidate.university_id = source.university_id
        AND candidate.admission_year = source.admission_year
        AND candidate.admission_type = '학생부교과'
        AND candidate.status = 'PUBLISHED'
  );

UPDATE evaluation_rule_grade_score score
JOIN evaluation_rule rule ON rule.id = score.rule_id
JOIN university university ON university.id = rule.university_id AND university.code = 'HS'
SET score.converted_score = CASE score.grade_value
    WHEN 8 THEN 90.0000
    WHEN 9 THEN 85.0000
    ELSE score.converted_score
END
WHERE rule.admission_year = 2027
  AND rule.admission_type = '논술'
  AND score.grade_value IN (8, 9);

INSERT IGNORE INTO evaluation_rule_achievement_grade (rule_id, achievement_level, converted_grade)
SELECT target.id, conversion.achievement_level, conversion.converted_grade
FROM evaluation_rule target
JOIN university university ON university.id = target.university_id AND university.code = 'HS'
JOIN evaluation_rule source
  ON source.university_id = target.university_id
 AND source.admission_year = target.admission_year
 AND source.admission_type = '학생부교과'
 AND source.status = 'PUBLISHED'
JOIN evaluation_rule_achievement_grade conversion ON conversion.rule_id = source.id
WHERE target.admission_year = 2027
  AND target.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  )
  AND source.id = (
      SELECT MIN(candidate.id)
      FROM evaluation_rule candidate
      WHERE candidate.university_id = source.university_id
        AND candidate.admission_year = source.admission_year
        AND candidate.admission_type = '학생부교과'
        AND candidate.status = 'PUBLISHED'
  );

INSERT IGNORE INTO evaluation_rule_achievement_score (rule_id, achievement_level, converted_score)
SELECT target.id, conversion.achievement_level, conversion.converted_score
FROM evaluation_rule target
JOIN university university ON university.id = target.university_id AND university.code = 'HS'
JOIN evaluation_rule source
  ON source.university_id = target.university_id
 AND source.admission_year = target.admission_year
 AND source.admission_type = '학생부교과'
 AND source.status = 'PUBLISHED'
JOIN evaluation_rule_achievement_score conversion ON conversion.rule_id = source.id
WHERE target.admission_year = 2027
  AND target.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  )
  AND source.id = (
      SELECT MIN(candidate.id)
      FROM evaluation_rule candidate
      WHERE candidate.university_id = source.university_id
        AND candidate.admission_year = source.admission_year
        AND candidate.admission_type = '학생부교과'
        AND candidate.status = 'PUBLISHED'
  );

INSERT IGNORE INTO evaluation_rule_legacy_achievement_grade (rule_id, legacy_achievement, converted_grade)
SELECT target.id, conversion.legacy_achievement, conversion.converted_grade
FROM evaluation_rule target
JOIN university university ON university.id = target.university_id AND university.code = 'HS'
JOIN evaluation_rule source
  ON source.university_id = target.university_id
 AND source.admission_year = target.admission_year
 AND source.admission_type = '학생부교과'
 AND source.status = 'PUBLISHED'
JOIN evaluation_rule_legacy_achievement_grade conversion ON conversion.rule_id = source.id
WHERE target.admission_year = 2027
  AND target.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  )
  AND source.id = (
      SELECT MIN(candidate.id)
      FROM evaluation_rule candidate
      WHERE candidate.university_id = source.university_id
        AND candidate.admission_year = source.admission_year
        AND candidate.admission_type = '학생부교과'
        AND candidate.status = 'PUBLISHED'
  );

INSERT IGNORE INTO evaluation_rule_subject_priority (rule_id, subject_category, priority_value)
SELECT target.id, priority.subject_category, priority.priority_value
FROM evaluation_rule target
JOIN university university ON university.id = target.university_id AND university.code = 'HS'
JOIN evaluation_rule source
  ON source.university_id = target.university_id
 AND source.admission_year = target.admission_year
 AND source.admission_type = '학생부교과'
 AND source.status = 'PUBLISHED'
JOIN evaluation_rule_subject_priority priority ON priority.rule_id = source.id
WHERE target.admission_year = 2027
  AND target.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  )
  AND source.id = (
      SELECT MIN(candidate.id)
      FROM evaluation_rule candidate
      WHERE candidate.university_id = source.university_id
        AND candidate.admission_year = source.admission_year
        AND candidate.admission_type = '학생부교과'
        AND candidate.status = 'PUBLISHED'
  );
