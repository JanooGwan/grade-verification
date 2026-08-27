-- V27의 복구 경로가 모든 전형을 1,000점 배수로 생성한 경우에도
-- 모집연도·전형별 학생부 정량 배점을 동일하게 유지한다.
UPDATE evaluation_rule rule
JOIN university university ON university.id = rule.university_id
SET rule.score_multiplier = CASE
        WHEN rule.admission_type = '참인재' THEN 5.4000
        WHEN rule.admission_type = '논술' THEN 2.0000
        WHEN rule.admission_type = '체육실기' AND rule.admission_year = 2026 THEN 6.0000
        WHEN rule.admission_type = '체육실기' THEN 4.5000
        ELSE 10.0000
    END,
    rule.updated_at = CURRENT_TIMESTAMP(6)
WHERE university.code = 'HS'
  AND rule.admission_year IN (2026, 2027)
  AND rule.version = 1
  AND rule.status = 'PUBLISHED'
  AND rule.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  );
