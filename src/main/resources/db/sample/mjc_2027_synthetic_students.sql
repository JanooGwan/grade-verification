-- 명지전문대학교 2027학년도 지원자 테스트용 합성 데이터
-- 실제 학생의 이름, 학교명, 학번 등 개인정보를 포함하지 않는다.
-- MySQL 8.0 기준이며 여러 번 실행해도 MJC27S 접두사의 기존 합성 데이터만 교체한다.

START TRANSACTION;

SET @admission_year = 2027;
SET @source_file_name = 'synthetic_mjc_2027_30_students.sql';

DELETE FROM student_transcript_import
WHERE admission_year = @admission_year
  AND original_file_name = @source_file_name;

-- student 삭제 시 student_transcript_course는 ON DELETE CASCADE로 함께 삭제된다.
DELETE FROM student
WHERE admission_year = @admission_year
  AND applicant_number LIKE 'MJC27S%';

DROP TEMPORARY TABLE IF EXISTS tmp_mjc_student_number;
CREATE TEMPORARY TABLE tmp_mjc_student_number (
    n INT NOT NULL PRIMARY KEY
);

INSERT INTO tmp_mjc_student_number (n) VALUES
    (1), (2), (3), (4), (5), (6), (7), (8), (9), (10),
    (11), (12), (13), (14), (15), (16), (17), (18), (19), (20),
    (21), (22), (23), (24), (25), (26), (27), (28), (29), (30);

INSERT INTO student (
    admission_year,
    applicant_number,
    name,
    high_school_code,
    high_school_name,
    graduation_year,
    created_at,
    updated_at
)
SELECT
    @admission_year,
    CONCAT('MJC27S', LPAD(n, 3, '0')),
    CONCAT('합성지원자', LPAD(n, 3, '0')),
    CONCAT('SYN-HS-', LPAD(MOD(n - 1, 10) + 1, 2, '0')),
    CONCAT('합성고등학교', LPAD(MOD(n - 1, 10) + 1, 2, '0')),
    2027,
    NOW(6),
    NOW(6)
FROM tmp_mjc_student_number;

DROP TEMPORARY TABLE IF EXISTS tmp_mjc_course_template;
CREATE TEMPORARY TABLE tmp_mjc_course_template (
    course_index INT NOT NULL PRIMARY KEY,
    school_year INT NOT NULL,
    semester INT NOT NULL,
    subject_category VARCHAR(20) NOT NULL,
    course_name VARCHAR(100) NOT NULL,
    credits DECIMAL(6,2) NOT NULL,
    base_grade INT NOT NULL,
    career_subject BOOLEAN NOT NULL DEFAULT FALSE
);

-- 생기부에서 확인한 일반고 과목 구성을 참고한 6개 학기, 학기당 5과목 템플릿이다.
INSERT INTO tmp_mjc_course_template (
    course_index, school_year, semester, subject_category,
    course_name, credits, base_grade, career_subject
) VALUES
    (1,  1, 1, 'KOREAN',  '국어',             4, 4, FALSE),
    (2,  1, 1, 'MATH',    '수학',             4, 5, FALSE),
    (3,  1, 1, 'ENGLISH', '영어',             4, 4, FALSE),
    (4,  1, 1, 'SOCIAL',  '한국사',           3, 3, FALSE),
    (5,  1, 1, 'SCIENCE', '통합과학',         3, 5, FALSE),

    (6,  1, 2, 'KOREAN',  '국어 II',          4, 4, FALSE),
    (7,  1, 2, 'MATH',    '수학 II',          4, 5, FALSE),
    (8,  1, 2, 'ENGLISH', '영어 II',          4, 4, FALSE),
    (9,  1, 2, 'SOCIAL',  '통합사회',         3, 4, FALSE),
    (10, 1, 2, 'OTHER',   '정보',             2, 3, FALSE),

    (11, 2, 1, 'KOREAN',  '문학',             4, 4, FALSE),
    (12, 2, 1, 'MATH',    '수학 I',           4, 5, FALSE),
    (13, 2, 1, 'ENGLISH', '영어 I',           4, 4, FALSE),
    (14, 2, 1, 'SOCIAL',  '사회문화',         3, 3, FALSE),
    (15, 2, 1, 'SCIENCE', '물리학 I',         3, 5, FALSE),

    (16, 2, 2, 'KOREAN',  '독서',             4, 4, FALSE),
    (17, 2, 2, 'MATH',    '수학 II',          4, 5, FALSE),
    (18, 2, 2, 'ENGLISH', '영어 II',          4, 4, FALSE),
    (19, 2, 2, 'SOCIAL',  '생활과 윤리',      3, 3, FALSE),
    (20, 2, 2, 'SCIENCE', '화학 I',           3, 5, FALSE),

    (21, 3, 1, 'KOREAN',  '화법과 작문',      3, 4, FALSE),
    (22, 3, 1, 'MATH',    '확률과 통계',      3, 5, FALSE),
    (23, 3, 1, 'ENGLISH', '영어 독해와 작문', 3, 4, FALSE),
    (24, 3, 1, 'SOCIAL',  '세계사',           3, 3, FALSE),
    (25, 3, 1, 'SCIENCE', '생활과 과학',      2, 4, TRUE),

    (26, 3, 2, 'KOREAN',  '실용 국어',        3, 4, FALSE),
    (27, 3, 2, 'MATH',    '경제 수학',        3, 5, FALSE),
    (28, 3, 2, 'ENGLISH', '영어 회화',        3, 4, FALSE),
    (29, 3, 2, 'SOCIAL',  '사회문제 탐구',    2, 3, TRUE),
    (30, 3, 2, 'SCIENCE', '융합과학',         2, 4, TRUE);

-- 지원자별 성적 수준을 1~9등급 범위에 분산한다.
-- 1~6번은 상위권, 25~30번은 하위권이며 과목별 편차도 추가한다.
INSERT INTO student_transcript_course (
    student_id,
    school_year,
    semester,
    subject_category,
    course_name,
    grade_value,
    achievement,
    raw_score,
    mean_score,
    standard_deviation,
    student_count,
    credits,
    career_subject,
    professional_course,
    source_file_name,
    source_row_number,
    created_at,
    updated_at
)
SELECT
    g.student_id,
    g.school_year,
    g.semester,
    g.subject_category,
    g.course_name,
    CASE WHEN g.career_subject THEN NULL ELSE g.calculated_grade END,
    CASE
        WHEN NOT g.career_subject THEN NULL
        WHEN g.student_number <= 9 THEN 'A'
        WHEN g.student_number <= 21 THEN 'B'
        ELSE 'C'
    END,
    CASE
        WHEN g.career_subject THEN
            CASE
                WHEN g.student_number <= 9 THEN 92.0000
                WHEN g.student_number <= 21 THEN 84.0000
                ELSE 76.0000
            END
        ELSE ROUND(96 - g.calculated_grade * 6.5
            + MOD(g.student_number * 3 + g.course_index, 7), 4)
    END,
    ROUND(64 + MOD(g.course_index * 5 + g.student_number, 10), 4),
    ROUND(12 + MOD(g.course_index + g.student_number, 8) / 2, 4),
    120 + MOD(g.student_number * 11 + g.course_index * 7, 181),
    g.credits,
    g.career_subject,
    FALSE,
    @source_file_name,
    g.student_number * 100 + g.course_index,
    NOW(6),
    NOW(6)
FROM (
    SELECT
        s.id AS student_id,
        n.n AS student_number,
        t.course_index,
        t.school_year,
        t.semester,
        t.subject_category,
        t.course_name,
        t.credits,
        t.career_subject,
        GREATEST(1, LEAST(9,
            t.base_grade
            + CASE
                WHEN n.n <= 6 THEN -2
                WHEN n.n <= 12 THEN -1
                WHEN n.n <= 20 THEN 0
                WHEN n.n <= 26 THEN 1
                ELSE 2
              END
            + CASE MOD(n.n + t.course_index, 5)
                WHEN 0 THEN -1
                WHEN 4 THEN 1
                ELSE 0
              END
        )) AS calculated_grade
    FROM tmp_mjc_student_number n
    JOIN student s
      ON s.admission_year = @admission_year
     AND s.applicant_number = CONCAT('MJC27S', LPAD(n.n, 3, '0'))
    CROSS JOIN tmp_mjc_course_template t
) g;

SET @synthetic_row_count = (
    SELECT COUNT(*)
    FROM student_transcript_course c
    JOIN student s ON s.id = c.student_id
    WHERE s.admission_year = @admission_year
      AND s.applicant_number LIKE 'MJC27S%'
);

INSERT INTO student_transcript_import (
    admission_year,
    original_file_name,
    total_rows,
    imported_rows,
    failed_rows,
    status,
    created_at
) VALUES (
    @admission_year,
    @source_file_name,
    @synthetic_row_count,
    @synthetic_row_count,
    0,
    'COMPLETED',
    NOW(6)
);

DROP TEMPORARY TABLE IF EXISTS tmp_mjc_course_template;
DROP TEMPORARY TABLE IF EXISTS tmp_mjc_student_number;

COMMIT;

-- 실행 결과 확인
SELECT COUNT(*) AS synthetic_student_count
FROM student
WHERE admission_year = @admission_year
  AND applicant_number LIKE 'MJC27S%';

SELECT COUNT(*) AS synthetic_course_count
FROM student_transcript_course c
JOIN student s ON s.id = c.student_id
WHERE s.admission_year = @admission_year
  AND s.applicant_number LIKE 'MJC27S%';
