-- AI 도우미에는 지원자 원본 행을 공개하지 않고, 개인 식별 컬럼이 없는 집계 결과만 제공한다.

CREATE OR REPLACE ALGORITHM = TEMPTABLE SQL SECURITY DEFINER VIEW ai_applicant_statistics AS
SELECT university.code AS university_code,
       university.name AS university_name,
       student.admission_year,
       COUNT(*) AS applicant_count
FROM student
JOIN university ON university.id = student.university_id
GROUP BY university.code, university.name, student.admission_year;

CREATE OR REPLACE ALGORITHM = TEMPTABLE SQL SECURITY DEFINER VIEW ai_applicant_course_count_statistics AS
SELECT university.code AS university_code,
       university.name AS university_name,
       course_count.admission_year,
       course_count.course_count,
       COUNT(*) AS applicant_count
FROM (
    SELECT student.id AS student_id,
           student.university_id,
           student.admission_year,
           COUNT(course.id) AS course_count
    FROM student
    LEFT JOIN student_transcript_course course ON course.student_id = student.id
    GROUP BY student.id, student.university_id, student.admission_year
) course_count
JOIN university ON university.id = course_count.university_id
GROUP BY university.code, university.name, course_count.admission_year, course_count.course_count;

CREATE OR REPLACE ALGORITHM = TEMPTABLE SQL SECURITY DEFINER VIEW ai_application_statistics AS
SELECT university.code AS university_code,
       university.name AS university_name,
       admission_track.admission_year,
       admission_track.name AS admission_track_name,
       recruitment_unit.code AS recruitment_unit_code,
       recruitment_unit.name AS recruitment_unit_name,
       COUNT(DISTINCT student_application.student_id) AS applicant_count
FROM student_application
JOIN recruitment_unit ON recruitment_unit.id = student_application.recruitment_unit_id
JOIN admission_track ON admission_track.id = recruitment_unit.admission_track_id
JOIN university ON university.id = admission_track.university_id
GROUP BY university.code, university.name, admission_track.admission_year,
         admission_track.name, recruitment_unit.code, recruitment_unit.name;

CREATE OR REPLACE ALGORITHM = TEMPTABLE SQL SECURITY DEFINER VIEW ai_course_enrollment_statistics AS
SELECT university.code AS university_code,
       university.name AS university_name,
       student.admission_year,
       course.subject_category,
       course.course_name_normalized,
       COUNT(DISTINCT course.student_id) AS applicant_count,
       COUNT(*) AS course_record_count,
       AVG(course.credits) AS average_credits,
       AVG(course.grade_value) AS average_grade_value
FROM student_transcript_course course
JOIN student ON student.id = course.student_id
JOIN university ON university.id = student.university_id
GROUP BY university.code, university.name, student.admission_year,
         course.subject_category, course.course_name_normalized;
