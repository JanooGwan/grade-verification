ALTER TABLE student_transcript_course
    ADD COLUMN vocational_training_semester BOOLEAN NOT NULL DEFAULT FALSE
    AFTER professional_course;

ALTER TABLE student_transcript_course
    MODIFY COLUMN vocational_training_semester BOOLEAN NOT NULL DEFAULT FALSE
    COMMENT '직업과정 위탁생 파일에서 해당 과목의 학기가 위탁 이수 학기로 확인되었는지 나타낸다.';
