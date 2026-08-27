ALTER TABLE student
    ADD COLUMN high_school_type VARCHAR(40) NOT NULL DEFAULT 'GENERAL'
    AFTER education_background;

UPDATE evaluation_rule er
JOIN university u ON u.id = er.university_id
SET er.achievement_selection_count = 0,
    er.interpretation_note = '학년별 가중치 없이 석차등급이 있는 상위 12과목을 이수단위로 가중평균합니다. 지원자격을 위한 반영 가능 과목은 최소 12개입니다. 졸업예정자는 3학년 1학기까지, 졸업자는 3학년 2학기까지 반영하며 진로선택과목과 전문교과는 제외합니다. 특성화고·종합고 전문계열·학력인정 평생교육시설 출신자는 3개 학년 보통교과 전 과목을, 정원외 특성화고교졸업자전형은 고교 재학 중 이수한 전 과목을 반영합니다.',
    er.change_summary = '2027학년도 모집요강 36~38쪽 재검증: 진로선택 개수 제거, 최소 12과목 지원자격 명확화, 학교 유형별 반영 범위 예외 추가'
WHERE u.name LIKE '%한신%'
  AND er.admission_year = 2027;
