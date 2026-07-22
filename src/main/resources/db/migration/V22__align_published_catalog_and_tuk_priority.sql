-- 한국공학대 경영계열 사회/과학 동률은 모집요강 해설대로 사회를 우선한다.
UPDATE evaluation_rule_subject_priority priority
JOIN evaluation_rule er ON er.id = priority.rule_id
JOIN university u ON u.id = er.university_id
SET priority.priority_value = CASE priority.subject_category
    WHEN 'SOCIAL' THEN 1
    WHEN 'SCIENCE' THEN 2
    ELSE priority.priority_value
END
WHERE u.code = 'TUK'
  AND er.admission_year = 2027
  AND er.selection_strategy = 'CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N'
  AND priority.subject_category IN ('SOCIAL', 'SCIENCE');

-- 카탈로그 노출 여부는 초안이 아니라 게시된 규칙의 존재 여부만으로 재동기화한다.
UPDATE recruitment_unit ru
JOIN admission_track at ON at.id = ru.admission_track_id
LEFT JOIN (
    SELECT DISTINCT university_id, admission_year, admission_type, recruitment_unit
    FROM evaluation_rule
    WHERE status = 'PUBLISHED'
) published ON published.university_id = at.university_id
    AND published.admission_year = at.admission_year
    AND published.admission_type = at.name
    AND published.recruitment_unit = ru.name
SET ru.active = (published.university_id IS NOT NULL),
    ru.updated_at = CURRENT_TIMESTAMP(6)
WHERE (at.admission_year = 2027 AND at.university_id IN (
    SELECT id FROM university WHERE code IN ('TUK', 'MJC', 'SY')
)) OR (at.admission_year = 2026 AND at.university_id IN (
    SELECT id FROM university WHERE code = 'KBOK'
));

UPDATE admission_track at
LEFT JOIN (
    SELECT DISTINCT university_id, admission_year, admission_type
    FROM evaluation_rule
    WHERE status = 'PUBLISHED'
) published ON published.university_id = at.university_id
    AND published.admission_year = at.admission_year
    AND published.admission_type = at.name
SET at.active = (published.university_id IS NOT NULL),
    at.updated_at = CURRENT_TIMESTAMP(6)
WHERE (at.admission_year = 2027 AND at.university_id IN (
    SELECT id FROM university WHERE code IN ('TUK', 'MJC', 'SY')
)) OR (at.admission_year = 2026 AND at.university_id IN (
    SELECT id FROM university WHERE code = 'KBOK'
));
