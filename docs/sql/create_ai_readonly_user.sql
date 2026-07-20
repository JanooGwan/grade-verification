-- 운영 환경에 맞게 사용자명, 접속 호스트, 비밀번호, 스키마명을 변경한 뒤
-- 관리자 계정으로 1회 실행합니다. 애플리케이션 코드에 실제 비밀번호를 기록하지 마세요.
CREATE USER IF NOT EXISTS 'grade_ai_reader'@'localhost'
    IDENTIFIED BY 'CHANGE_TO_A_LONG_RANDOM_PASSWORD';

REVOKE ALL PRIVILEGES, GRANT OPTION
    FROM 'grade_ai_reader'@'localhost';

GRANT SELECT
    ON grade_validation.*
    TO 'grade_ai_reader'@'localhost';

SHOW GRANTS FOR 'grade_ai_reader'@'localhost';
