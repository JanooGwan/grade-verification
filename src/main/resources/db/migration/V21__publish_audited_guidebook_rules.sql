-- 모집요강 원문 페이지와 골든 테스트를 재검수한 정량 규칙만 게시한다.
UPDATE evaluation_rule er
JOIN university u ON u.id = er.university_id
SET er.status = 'PUBLISHED',
    er.active = TRUE,
    er.reviewer = 'guidebook-audit',
    er.review_note = CASE
        WHEN u.name LIKE '%한국공학%' THEN '2027 수시 모집요강 32~33쪽 석차백분율·검정고시·비교내신 재검수'
        WHEN u.name LIKE '%명지전문%' THEN '2027 수시 모집요강 35~38쪽 졸업연도·2년제고교·검정고시 가중평균 재검수'
        WHEN u.name LIKE '%경복%' THEN '2026 모집요강 44~45쪽 졸업연도별 산식·절사 재검수'
        WHEN u.name LIKE '%삼육%' THEN '2027 수시 모집요강 53~54쪽 학생부 반영방법 재검수'
        ELSE er.review_note
    END,
    er.reviewed_at = CURRENT_TIMESTAMP(6),
    er.published_by = 'guidebook-audit',
    er.publication_note = '모집요강 원문 예제 및 경계값 자동 테스트 통과 후 게시',
    er.published_at = CURRENT_TIMESTAMP(6),
    er.updated_at = CURRENT_TIMESTAMP(6)
WHERE er.status = 'DRAFT'
  AND ((er.admission_year = 2027 AND (
        u.name LIKE '%한국공학%' OR u.name LIKE '%명지전문%' OR u.name LIKE '%삼육%'
      )) OR (er.admission_year = 2026 AND u.name LIKE '%경복%'));
