-- 비교내신의 2월 경계를 위해 원본 졸업일을 보존한다.
-- 연도만 있는 기존 기록의 졸업월은 추정하지 않는다.
ALTER TABLE student ADD COLUMN graduation_date DATE NULL COMMENT '실제 또는 예정 졸업일';
