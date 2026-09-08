-- 서로 다른 원천 과목이 같은 학생·학년·학기·정규화 과목명을 가질 수 있다.
-- 파일 행을 원천 레코드 식별자로 사용해 같은 이름의 과목도 각각 보존한다.
ALTER TABLE student_transcript_course
    DROP INDEX uk_transcript_course_normalized,
    ADD INDEX idx_transcript_course_normalized
        (student_id, school_year, semester, course_name_normalized),
    ADD CONSTRAINT uk_transcript_course_source_row
        UNIQUE (student_id, source_file_name, source_row_number);
