-- 2026학년도 한신대학교 수시모집 검증 기준을 적용한다.
-- 우수 과목은 최대 12과목까지 반영하되, 12과목 미만이라는 이유만으로 지원자격 미달 처리하지 않는다.
UPDATE evaluation_rule rule
JOIN university university ON university.id = rule.university_id
SET rule.minimum_course_count = 0,
    rule.interpretation_note = CASE
        WHEN rule.interpretation_note IS NULL THEN NULL
        ELSE REPLACE(
            rule.interpretation_note,
            ' 지원자격을 위한 반영 가능 과목은 최소 12개입니다.',
            ''
        )
    END,
    rule.change_summary = '2026학년도 수시모집 검증 기준: 최소 12과목 지원자격 조건 제거',
    rule.updated_at = CURRENT_TIMESTAMP(6)
WHERE university.code = 'HS'
  AND rule.admission_year = 2027
  AND rule.status = 'PUBLISHED';
