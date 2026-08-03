-- 초안 규칙만 존재하는 전형·모집단위가 지원 화면에 노출되지 않도록
-- 현재 게시 규칙을 기준으로 카탈로그 활성 상태를 다시 맞춘다.
UPDATE recruitment_unit unit
JOIN admission_track track ON track.id = unit.admission_track_id
LEFT JOIN evaluation_rule rule
  ON rule.university_id = track.university_id
 AND rule.admission_year = track.admission_year
 AND rule.admission_type = track.name
 AND rule.recruitment_unit = unit.name
 AND rule.status = 'PUBLISHED'
SET unit.active = (rule.id IS NOT NULL),
    unit.updated_at = CURRENT_TIMESTAMP(6)
WHERE (track.admission_year = 2027 AND track.university_id IN (
        SELECT id FROM university WHERE code IN ('TUK', 'MJC', 'SY')
    ))
   OR (track.admission_year = 2026 AND track.university_id IN (
        SELECT id FROM university WHERE code = 'KBOK'
    ));

UPDATE admission_track track
LEFT JOIN evaluation_rule rule
  ON rule.university_id = track.university_id
 AND rule.admission_year = track.admission_year
 AND rule.admission_type = track.name
 AND rule.status = 'PUBLISHED'
SET track.active = (rule.id IS NOT NULL),
    track.updated_at = CURRENT_TIMESTAMP(6)
WHERE (track.admission_year = 2027 AND track.university_id IN (
        SELECT id FROM university WHERE code IN ('TUK', 'MJC', 'SY')
    ))
   OR (track.admission_year = 2026 AND track.university_id IN (
        SELECT id FROM university WHERE code = 'KBOK'
    ));
