CREATE INDEX idx_transcript_course_source_student_row
    ON student_transcript_course (source_file_name, student_id, source_row_number);
