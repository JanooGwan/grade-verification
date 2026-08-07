-- 운영 환경에 맞게 사용자명, 접속 호스트, 스키마명을 변경한 뒤 관리자 계정으로 1회 실행합니다.
-- MySQL이 생성해 반환하는 임의 비밀번호를 비밀 저장소에 보관하고 AI_DB_PASSWORD로 주입하세요.
CREATE USER IF NOT EXISTS 'grade_ai_reader'@'localhost'
    IDENTIFIED BY RANDOM PASSWORD;

REVOKE ALL PRIVILEGES, GRANT OPTION
    FROM 'grade_ai_reader'@'localhost';

GRANT SELECT ON grade_validation.university TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.admission_track TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.recruitment_unit TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.evaluation_rule TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.evaluation_rule_grade_score TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.evaluation_rule_achievement_grade TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.evaluation_rule_achievement_score TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.evaluation_rule_subject_priority TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.evaluation_rule_extraction TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.evaluation_rule_extraction_evidence TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.ai_applicant_statistics TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.ai_applicant_course_count_statistics TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.ai_application_statistics TO 'grade_ai_reader'@'localhost';
GRANT SELECT ON grade_validation.ai_course_enrollment_statistics TO 'grade_ai_reader'@'localhost';

SHOW GRANTS FOR 'grade_ai_reader'@'localhost';
