# 구교육과정·검정고시 데이터 모델

Flyway `V20`에서 추가한 공통 학업성적 모델이다. 지원자 원천 데이터와 대학별 환산 규칙을 분리해 같은 원천 성적을 여러 대학 계산에 재사용한다.

## 관계도

```mermaid
erDiagram
    STUDENT ||--o{ STUDENT_TRANSCRIPT_COURSE : "과목별 성적 보유"
    STUDENT ||--o{ STUDENT_GED_SUBJECT_SCORE : "검정고시 원점수 보유"
    STUDENT ||--o{ STUDENT_LEGACY_GRADE_SUMMARY : "구교육과정 석차 요약 보유"
    EVALUATION_RULE ||--o{ EVALUATION_RULE_LEGACY_ACHIEVEMENT_GRADE : "대학별 평어 환산 정의"

    STUDENT_TRANSCRIPT_COURSE {
        BIGINT id PK "과목 성적 식별자"
        BIGINT student_id FK "지원자 식별자"
        INT school_year "학년"
        INT semester "학기"
        VARCHAR subject_category "교과 구분"
        VARCHAR course_name "과목명"
        INT grade_value "현행 석차등급"
        VARCHAR grade_scale "NINE_LEVEL, FIVE_LEVEL, LEGACY"
        INT student_count "재적수"
        INT rank_position "석차"
        INT tied_rank_count "동석차 인원"
        VARCHAR legacy_achievement "수·우·미·양·가 코드"
        DECIMAL credits "이수단위"
    }

    STUDENT_GED_SUBJECT_SCORE {
        BIGINT id PK "검정고시 과목 점수 식별자"
        BIGINT student_id FK "지원자 식별자"
        VARCHAR subject_type "국어·영어·수학·한국사·사회·과학·선택"
        VARCHAR subject_name "실제 과목명"
        DECIMAL score "과목별 원점수"
    }

    STUDENT_LEGACY_GRADE_SUMMARY {
        BIGINT id PK "구교육과정 요약 식별자"
        BIGINT student_id FK "지원자 식별자"
        VARCHAR summary_type "SEMESTER 또는 YEAR"
        INT school_year "학년"
        INT semester "학기; 학년 요약은 NULL"
        INT semester_key "NULL을 0으로 정규화한 유니크 키"
        INT rank_position "계열 또는 학년 석차"
        INT tied_rank_count "동석차 인원"
        INT cohort_size "재적수"
        DECIMAL credits "계산 가중치용 이수단위"
    }

    EVALUATION_RULE {
        BIGINT id PK "평가 규칙 식별자"
        VARCHAR input_grade_scale "규칙이 받는 기본 등급제"
    }

    EVALUATION_RULE_LEGACY_ACHIEVEMENT_GRADE {
        BIGINT rule_id PK,FK "평가 규칙 식별자"
        VARCHAR legacy_achievement PK "SU, WOO, MI, YANG, GA"
        DECIMAL converted_grade "대학별 환산 석차등급"
    }
```

## 설계 원칙

- 석차백분율은 원천 데이터에 저장하지 않고 `석차`, `동석차 인원`, `재적수`로 매번 계산해 중간값과 산식 검증이 가능하게 한다.
- 검정고시는 평균만 저장하지 않고 과목별 원점수를 저장한다. 대학별 과목 선택과 단위 가중치는 계산 정책이 담당한다.
- 과목 단위 자료가 없는 오래된 교육과정은 학기 또는 학년 석차를 `student_legacy_grade_summary`에 저장한다.
- 수·우·미·양·가 환산표는 지원자 데이터가 아니라 `evaluation_rule` 하위 테이블에 두어 대학과 규칙 버전별로 변경할 수 있다.
- 5등급제 입력은 모델에서 구분한다. 5등급제 산식이 확정된 규칙만 `input_grade_scale=FIVE_LEVEL`로 게시해야 한다.
