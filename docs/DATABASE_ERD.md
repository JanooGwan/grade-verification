# Grade Validation Database ERD

이 문서는 Flyway 마이그레이션 `V1`~`V11`을 기준으로 작성한 현재 MySQL 스키마의 ERD이다.

```mermaid
erDiagram
    UNIVERSITY {
        BIGINT id PK
        VARCHAR code UK
        VARCHAR name
        BOOLEAN active
        DATETIME created_at
        DATETIME updated_at
    }

    EVALUATION_RULE {
        BIGINT id PK
        BIGINT university_id FK
        BIGINT extraction_id FK "nullable"
        VARCHAR name
        INT admission_year
        VARCHAR admission_type
        VARCHAR recruitment_unit
        INT version
        DECIMAL grade1_weight
        DECIMAL grade2_weight
        DECIMAL grade3_weight
        DECIMAL korean_weight
        DECIMAL math_weight
        DECIMAL english_weight
        DECIMAL social_weight
        DECIMAL science_weight
        DECIMAL other_weight
        VARCHAR selection_strategy
        INT selection_count
        INT achievement_selection_count
        VARCHAR score_aggregation
        VARCHAR achievement_conversion
        BOOLEAN include_third_year_second_semester
        BOOLEAN include_third_year_second_semester_for_graduates
        BOOLEAN include_professional_courses
        BOOLEAN normalize_grade_weights
        INT intermediate_scale
        VARCHAR intermediate_rounding
        INT final_scale
        VARCHAR final_rounding
        DECIMAL score_multiplier
        BOOLEAN active
        VARCHAR status
        VARCHAR reviewer "nullable"
        VARCHAR review_note "nullable"
        DATETIME reviewed_at "nullable"
        VARCHAR published_by "nullable"
        VARCHAR publication_note "nullable"
        DATETIME published_at "nullable"
        VARCHAR retired_by "nullable"
        VARCHAR retire_note "nullable"
        DATETIME retired_at "nullable"
        VARCHAR source_document "nullable"
        VARCHAR source_pages "nullable"
        VARCHAR interpretation_note "nullable"
        VARCHAR change_summary "nullable"
        DATETIME created_at
        DATETIME updated_at
    }

    EVALUATION_RULE_GRADE_SCORE {
        BIGINT rule_id PK,FK
        INT grade_value PK
        DECIMAL converted_score
    }

    EVALUATION_RULE_ACHIEVEMENT_GRADE {
        BIGINT rule_id PK,FK
        VARCHAR achievement_level PK
        DECIMAL converted_grade
    }

    EVALUATION_RULE_ACHIEVEMENT_SCORE {
        BIGINT rule_id PK,FK
        VARCHAR achievement_level PK
        DECIMAL converted_score
    }

    EVALUATION_RULE_SUBJECT_PRIORITY {
        BIGINT rule_id PK,FK
        VARCHAR subject_category PK
        INT priority_value
    }

    EVALUATION_RULE_EXTRACTION {
        BIGINT id PK
        BIGINT university_id FK
        BIGINT draft_rule_id FK "nullable"
        INT admission_year
        VARCHAR original_file_name
        CHAR file_sha256
        INT page_count
        INT text_page_count
        VARCHAR status
        VARCHAR selection_strategy "nullable"
        INT selection_count "nullable"
        VARCHAR grade_weights_csv "nullable"
        VARCHAR grade_scores_csv "nullable"
        VARCHAR achievement_scores_csv "nullable"
        VARCHAR subject_categories_csv "nullable"
        BOOLEAN include_third_year_second_semester "nullable"
        VARCHAR rounding_mode "nullable"
        VARCHAR source_pages "nullable"
        DECIMAL overall_confidence
        VARCHAR missing_fields "nullable"
        VARCHAR warnings "nullable"
        DATETIME created_at
        DATETIME updated_at
    }

    EVALUATION_RULE_EXTRACTION_EVIDENCE {
        BIGINT id PK
        BIGINT extraction_id FK
        VARCHAR field_key
        INT page_number
        VARCHAR excerpt
        DECIMAL confidence
    }

    STUDENT {
        BIGINT id PK
        INT admission_year UK
        VARCHAR applicant_number UK
        VARCHAR name
        VARCHAR high_school_code "nullable"
        VARCHAR high_school_name "nullable"
        INT graduation_year "nullable"
        DATETIME created_at
        DATETIME updated_at
    }

    STUDENT_TRANSCRIPT_COURSE {
        BIGINT id PK
        BIGINT student_id FK
        INT school_year
        INT semester
        VARCHAR subject_category
        VARCHAR course_name
        INT grade_value "nullable"
        VARCHAR achievement "nullable"
        DECIMAL raw_score "nullable"
        DECIMAL mean_score "nullable"
        DECIMAL standard_deviation "nullable"
        INT student_count "nullable"
        DECIMAL credits
        BOOLEAN career_subject
        BOOLEAN professional_course
        VARCHAR source_file_name
        INT source_row_number
        DATETIME created_at
        DATETIME updated_at
    }

    STUDENT_TRANSCRIPT_IMPORT {
        BIGINT id PK
        INT admission_year
        VARCHAR original_file_name
        VARCHAR import_mode
        CHAR file_sha256 "nullable"
        INT total_rows
        INT imported_rows
        INT failed_rows
        VARCHAR status
        DATETIME created_at
    }

    ADMISSION_TRACK {
        BIGINT id PK
        BIGINT university_id FK
        INT admission_year
        VARCHAR name
        BOOLEAN active
        DATETIME created_at
        DATETIME updated_at
    }

    RECRUITMENT_UNIT {
        BIGINT id PK
        BIGINT admission_track_id FK
        VARCHAR code "nullable"
        VARCHAR name
        BOOLEAN active
        DATETIME created_at
        DATETIME updated_at
    }

    STUDENT_APPLICATION {
        BIGINT id PK
        BIGINT student_id FK
        BIGINT recruitment_unit_id FK
        DATETIME created_at
        DATETIME updated_at
    }

    VERIFICATION_RUN {
        BIGINT id PK
        BIGINT student_id FK
        BIGINT application_id FK "nullable"
        BIGINT rule_id FK
        INT rule_version
        DECIMAL final_score
        DECIMAL average_grade
        INT included_course_count
        INT excluded_course_count
        LONGTEXT result_json
        DATETIME created_at
    }

    UNIVERSITY ||--o{ EVALUATION_RULE : "defines"
    UNIVERSITY ||--o{ EVALUATION_RULE_EXTRACTION : "extracts rules for"
    UNIVERSITY ||--o{ ADMISSION_TRACK : "offers"

    EVALUATION_RULE ||--o{ EVALUATION_RULE_GRADE_SCORE : "maps grades"
    EVALUATION_RULE ||--o{ EVALUATION_RULE_ACHIEVEMENT_GRADE : "maps achievement grades"
    EVALUATION_RULE ||--o{ EVALUATION_RULE_ACHIEVEMENT_SCORE : "maps achievement scores"
    EVALUATION_RULE ||--o{ EVALUATION_RULE_SUBJECT_PRIORITY : "prioritizes subjects"
    EVALUATION_RULE_EXTRACTION ||--o{ EVALUATION_RULE_EXTRACTION_EVIDENCE : "has evidence"
    EVALUATION_RULE o|--o{ EVALUATION_RULE_EXTRACTION : "draft_rule_id"
    EVALUATION_RULE_EXTRACTION o|--o{ EVALUATION_RULE : "extraction_id"

    STUDENT ||--o{ STUDENT_TRANSCRIPT_COURSE : "has"
    STUDENT ||--o{ STUDENT_APPLICATION : "submits"
    STUDENT ||--o{ VERIFICATION_RUN : "is evaluated in"

    ADMISSION_TRACK ||--o{ RECRUITMENT_UNIT : "contains"
    RECRUITMENT_UNIT ||--o{ STUDENT_APPLICATION : "receives"
    STUDENT_APPLICATION o|--o{ VERIFICATION_RUN : "groups"
    EVALUATION_RULE ||--o{ VERIFICATION_RUN : "applied by"
```

## 핵심 제약조건

- `evaluation_rule`은 `(university_id, admission_year, admission_type, recruitment_unit, version)` 조합으로 버전을 유일하게 관리한다.
- `evaluation_rule_extraction`은 `(university_id, admission_year, file_sha256)` 조합으로 같은 모집요강 파일의 중복 추출을 방지한다.
- `student`는 `(admission_year, applicant_number)` 조합이 유일하다.
- `student_transcript_course`는 학생·학년·학기·교과군·과목명 조합이 유일하다.
- `student_application`은 학생과 모집단위 조합이 유일하다.
- `student_transcript_import`는 다른 테이블과 외래 키로 연결되지 않은 독립적인 가져오기 작업 이력이다.

## 삭제 규칙

- 학생 삭제 시 학생부 과목, 지원 정보, 검증 실행 이력이 함께 삭제된다.
- 평가 규칙 삭제 시 등급/성취도 환산표와 교과 우선순위가 함께 삭제된다.
- 규칙 추출 이력 삭제 시 추출 근거가 함께 삭제된다.
- 지원 정보 삭제 시 검증 실행 이력은 유지되고 `application_id`만 `NULL`로 변경된다.
