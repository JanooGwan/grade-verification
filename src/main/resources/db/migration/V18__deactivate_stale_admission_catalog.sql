-- 규칙 분리 이전의 통합 전형/모집단위는 지원 화면에서 선택되지 않도록 비활성화한다.
UPDATE recruitment_unit ru
JOIN admission_track at ON at.id = ru.admission_track_id
LEFT JOIN evaluation_rule er ON er.university_id = at.university_id
  AND er.admission_year = at.admission_year
  AND er.admission_type = at.name
  AND er.recruitment_unit = ru.name
  AND er.status <> 'RETIRED'
SET ru.active = FALSE, ru.updated_at = CURRENT_TIMESTAMP(6)
WHERE er.id IS NULL
  AND ((at.admission_year = 2027 AND at.university_id IN (
      SELECT id FROM university WHERE code IN ('TUK','MJC','SY')
  )) OR (at.admission_year = 2026 AND at.university_id IN (
      SELECT id FROM university WHERE code = 'KBOK'
  )));

UPDATE admission_track at
LEFT JOIN evaluation_rule er ON er.university_id = at.university_id
  AND er.admission_year = at.admission_year
  AND er.admission_type = at.name
  AND er.status <> 'RETIRED'
SET at.active = FALSE, at.updated_at = CURRENT_TIMESTAMP(6)
WHERE er.id IS NULL
  AND ((at.admission_year = 2027 AND at.university_id IN (
      SELECT id FROM university WHERE code IN ('TUK','MJC','SY')
  )) OR (at.admission_year = 2026 AND at.university_id IN (
      SELECT id FROM university WHERE code = 'KBOK'
  )));
