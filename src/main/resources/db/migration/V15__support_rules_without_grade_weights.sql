ALTER TABLE evaluation_rule
    ADD COLUMN apply_grade_weights BOOLEAN NOT NULL DEFAULT TRUE
    AFTER include_professional_courses;

ALTER TABLE evaluation_rule_extraction
    ADD COLUMN apply_grade_weights BOOLEAN NULL
    AFTER grade_weights_csv;

UPDATE evaluation_rule er
JOIN university u ON u.id = er.university_id
SET er.apply_grade_weights = FALSE,
    er.minimum_course_count = 12,
    er.achievement_conversion = 'EXCLUDE',
    er.include_third_year_second_semester = FALSE,
    er.include_third_year_second_semester_for_graduates = TRUE,
    er.include_professional_courses = FALSE,
    er.normalize_grade_weights = FALSE,
    er.interpretation_note = '학년별 가중치 없이 석차등급이 있는 상위 12과목을 이수단위로 가중 평균합니다. 졸업예정자는 3학년 1학기까지, 졸업자는 3학년 2학기까지 반영하며 진로선택과목과 전문교과는 제외합니다.',
    er.change_summary = '2027학년도 모집요강 36~38쪽 기준 학년 가중치·반영 학기·최소 과목·진로선택 제외 규칙 보정'
WHERE u.name LIKE '%한신%'
  AND er.admission_year = 2027;

UPDATE evaluation_rule_extraction ere
JOIN university u ON u.id = ere.university_id
SET ere.apply_grade_weights = FALSE
WHERE u.name LIKE '%한신%'
  AND ere.admission_year = 2027;
