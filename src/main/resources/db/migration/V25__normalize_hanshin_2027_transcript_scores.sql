-- 학생부교과 검증 결과는 전형별 실전형 반영 비율과 분리하여 1,000점 만점으로 통일한다.
UPDATE evaluation_rule rule
JOIN university university ON university.id = rule.university_id
SET rule.score_multiplier = 10.0000,
    rule.interpretation_note = CASE
        WHEN rule.admission_type = '특성화고교졸업자'
            THEN '고교 재학 중 석차등급이 있는 전 교과목을 반영하며 성취평가제 과목은 제외하고, 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
        ELSE '전형별 학생부 반영 비율과 분리하여 평균 교과성적점수를 1,000점 만점으로 환산합니다.'
    END,
    rule.change_summary = '교과성적 검증 결과를 전형 공통 1,000점 만점으로 정규화',
    rule.updated_at = CURRENT_TIMESTAMP(6)
WHERE university.code = 'HS'
  AND rule.admission_year = 2027
  AND rule.status = 'PUBLISHED'
  AND rule.admission_type IN (
      '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
      '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
  );
