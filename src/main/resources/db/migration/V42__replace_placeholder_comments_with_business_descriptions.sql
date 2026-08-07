-- V40에서 빈 COMMENT를 채우기 위해 넣었던 "테이블명.컬럼명 컬럼" 문구를
-- AI 도우미와 운영자가 스키마의 업무 의미를 이해할 수 있는 설명으로 교체한다.

ALTER TABLE university COMMENT = '대학 코드·대학명과 서비스 사용 여부를 관리하는 대학 기준정보이다.';
ALTER TABLE admission_track COMMENT = '대학과 모집연도에 개설된 전형명 및 사용 여부를 관리하는 전형 기준정보이다.';
ALTER TABLE recruitment_unit COMMENT = '전형별 학과·학부 등 모집단위 코드와 명칭을 관리하는 모집단위 기준정보이다.';
ALTER TABLE student COMMENT = '대학·모집연도별 지원자의 수험번호, 고교, 졸업 및 학력 정보를 관리한다.';
ALTER TABLE student_application COMMENT = '지원자와 지원한 전형의 모집단위를 연결하며 원본 업로드 이력을 추적한다.';
ALTER TABLE student_transcript_course COMMENT = '지원자가 이수한 학년·학기별 과목의 등급, 성취도, 원점수, 이수단위 등 성적 원천자료를 관리한다.';
ALTER TABLE student_attendance COMMENT = '지원자의 학년별 미인정 결석·지각·조퇴·결과 횟수를 관리한다.';
ALTER TABLE student_school_violence_action COMMENT = '지원자의 학교폭력 조치호수와 조치일, 현재 적용 여부를 관리한다.';
ALTER TABLE student_ged_subject_score COMMENT = '검정고시 지원자의 과목별 취득점수를 관리한다.';
ALTER TABLE student_legacy_grade_summary COMMENT = '구 학제 성적의 학년·학기별 석차, 동석차, 재적수와 이수단위 요약을 관리한다.';
ALTER TABLE student_transcript_import COMMENT = '대학·모집연도별 성적 파일 업로드 작업의 원본 파일, 처리 건수, 상태와 오류를 관리한다.';
ALTER TABLE student_transcript_import_course COMMENT = '대용량 비동기 성적 업로드 과정에서 파싱한 과목 행을 저장하는 임시 적재 테이블이다.';
ALTER TABLE evaluation_rule COMMENT = '대학·모집연도·전형·모집단위별 학생부 반영과 점수 계산 규칙 및 버전을 관리한다.';
ALTER TABLE evaluation_rule_grade_score COMMENT = '평가 규칙에서 석차등급별로 적용할 환산점수를 관리한다.';
ALTER TABLE evaluation_rule_achievement_grade COMMENT = '평가 규칙에서 성취도별로 적용할 환산등급을 관리한다.';
ALTER TABLE evaluation_rule_achievement_score COMMENT = '평가 규칙에서 성취도별로 적용할 환산점수를 관리한다.';
ALTER TABLE evaluation_rule_legacy_achievement_grade COMMENT = '평가 규칙에서 구 성취도 표기별로 적용할 환산등급을 관리한다.';
ALTER TABLE evaluation_rule_subject_priority COMMENT = '평가 규칙에서 동점 과목 선택 시 사용할 교과군 우선순위를 관리한다.';
ALTER TABLE evaluation_rule_extraction COMMENT = '모집요강 파일에서 자동 추출한 성적 반영 규칙 후보와 검토용 진단정보를 관리한다.';
ALTER TABLE evaluation_rule_extraction_evidence COMMENT = '자동 추출한 규칙 필드별 모집요강 페이지, 근거 문구와 신뢰도를 관리한다.';
ALTER TABLE verification_run COMMENT = '지원자 성적 검증 당시 적용 규칙, 선택·제외 과목 수와 최종 환산 결과를 이력으로 저장한다.';
ALTER TABLE application_score_run COMMENT = '지원자의 교과·출결·추가점수·학교폭력 감점을 합산한 지원 전형 점수 산출 이력을 저장한다.';
ALTER TABLE university_import_profile COMMENT = '대학별 업로드 파일 형식, 컬럼 매핑과 기존 데이터 교체 정책을 관리한다.';
ALTER TABLE university_export_profile COMMENT = '대학별 결과 파일 형식과 내보낼 컬럼 정의를 관리한다.';

CREATE TEMPORARY TABLE semantic_column_comment (
    column_name VARCHAR(64) NOT NULL PRIMARY KEY,
    comment_text VARCHAR(1024) NOT NULL
);

INSERT INTO semantic_column_comment (column_name, comment_text) VALUES
    ('id', '각 테이블의 레코드를 내부적으로 식별하는 자동 증가 기본키이다.'),
    ('university_id', '이 데이터가 속한 대학을 가리키는 university.id 외래키이다.'),
    ('admission_track_id', '이 모집단위가 속한 전형을 가리키는 admission_track.id 외래키이다.'),
    ('recruitment_unit_id', '지원자가 지원한 모집단위를 가리키는 recruitment_unit.id 외래키이다.'),
    ('student_id', '이 데이터의 대상 지원자를 가리키는 student.id 외래키이다.'),
    ('application_id', '점수 산출 대상 지원 관계를 가리키는 student_application.id 외래키이다.'),
    ('rule_id', '성적 계산에 사용하거나 값을 정의하는 evaluation_rule.id 외래키이다.'),
    ('rule_version', '점수 산출 당시 사용한 평가 규칙 버전 번호로, 이후 규칙 변경과 무관하게 계산 근거를 추적하는 값이다.'),
    ('extraction_id', '이 데이터의 근거가 된 모집요강 규칙 추출 작업을 가리키는 evaluation_rule_extraction.id 외래키이다.'),
    ('draft_rule_id', '추출 결과로 생성한 검토 전 평가 규칙을 가리키는 evaluation_rule.id 외래키이다.'),
    ('import_id', '임시 적재 과목 행이 속한 업로드 작업을 가리키는 student_transcript_import.id 외래키이다.'),
    ('source_import_id', '현재 데이터 행을 생성한 성적 업로드 작업을 가리키는 student_transcript_import.id 외래키이며 재업로드 교체 범위 추적에 사용한다.'),
    ('admission_year', '해당 데이터가 적용되는 입학 모집연도이다.'),
    ('admission_type', '평가 규칙이 적용되는 전형명 또는 전형 구분값이다.'),
    ('recruitment_unit', '평가 규칙이 적용되는 학과·학부 등 모집단위명 또는 모집단위 구분값이다.'),
    ('applicant_number', '대학이 부여한 수험번호로, 동일 대학·모집연도 안에서 지원자를 구분하는 업무 식별값이다.'),
    ('active', '현재 업무에서 사용할 수 있는 데이터인지 나타내는 값이다. 1은 사용, 0은 미사용을 의미한다.'),
    ('created_at', '이 레코드가 최초 생성된 날짜와 시각이다.'),
    ('updated_at', '이 레코드가 마지막으로 변경된 날짜와 시각이다.'),
    ('version', '동일 대학·모집연도·전형·모집단위 규칙의 변경 차수를 나타내는 버전 번호이다.'),
    ('grade1_weight', '성적 계산에서 1학년 성적에 적용하는 가중 비율이다.'),
    ('grade2_weight', '성적 계산에서 2학년 성적에 적용하는 가중 비율이다.'),
    ('grade3_weight', '성적 계산에서 3학년 성적에 적용하는 가중 비율이다.'),
    ('korean_weight', '성적 계산에서 국어 교과군에 적용하는 가중 비율이다.'),
    ('math_weight', '성적 계산에서 수학 교과군에 적용하는 가중 비율이다.'),
    ('english_weight', '성적 계산에서 영어 교과군에 적용하는 가중 비율이다.'),
    ('social_weight', '성적 계산에서 사회 교과군에 적용하는 가중 비율이다.'),
    ('science_weight', '성적 계산에서 과학 교과군에 적용하는 가중 비율이다.'),
    ('other_weight', '성적 계산에서 국어·수학·영어·사회·과학 외 교과군에 적용하는 가중 비율이다.'),
    ('reviewer', '평가 규칙의 내용과 근거를 검토한 담당자 식별값이다.'),
    ('review_note', '평가 규칙 검토 과정에서 기록한 확인사항과 보완 의견이다.'),
    ('reviewed_at', '평가 규칙 검토를 완료한 날짜와 시각이다.'),
    ('published_by', '평가 규칙을 실제 성적 계산에 사용하도록 게시한 담당자 식별값이다.'),
    ('publication_note', '평가 규칙 게시 시 기록한 승인 근거 또는 운영 참고사항이다.'),
    ('published_at', '평가 규칙을 실제 사용 상태로 게시한 날짜와 시각이다.'),
    ('retired_by', '평가 규칙의 사용을 종료한 담당자 식별값이다.'),
    ('retire_note', '평가 규칙 사용 종료 사유와 대체 규칙 등의 참고사항이다.'),
    ('retired_at', '평가 규칙의 사용을 종료한 날짜와 시각이다.'),
    ('selection_strategy', '반영 과목을 고르는 방식이다. 전체 과목, 상위 N개 등 계산기가 해석하는 전략 코드를 저장한다.'),
    ('selection_policy', '교과별 선택 수, 학기 범위, 동점 처리 등 복합 과목 선택 조건을 표현한 JSON 정책이다.'),
    ('selection_count', '선택 전략이 상위 N개 방식일 때 반영할 과목 수이며 0은 별도 개수 제한이 없음을 의미한다.'),
    ('achievement_selection_count', '진로선택 등 성취도 과목 중 별도로 반영할 과목 수이며 0은 별도 선택을 하지 않음을 의미한다.'),
    ('minimum_course_count', '지원자격 또는 계산 가능 여부를 판단할 때 요구하는 최소 반영 가능 과목 수이다.'),
    ('score_aggregation', '선택한 과목 점수를 평균·합계·이수단위 가중평균 중 어떤 방식으로 집계할지 나타내는 전략 코드이다.'),
    ('achievement_conversion', '성취도 과목을 환산등급 또는 환산점수로 변환하는 방법을 나타내는 전략 코드이다.'),
    ('input_grade_scale', '규칙이 입력 성적으로 기대하는 등급 체계 코드이다. 예: NINE_LEVEL, FIVE_LEVEL.'),
    ('include_third_year_second_semester', '재학생 성적 계산에 3학년 2학기 과목을 포함할지 나타내는 값이다.'),
    ('include_third_year_second_semester_for_graduates', '졸업생 성적 계산에 3학년 2학기 과목을 포함할지 나타내는 값이다.'),
    ('include_professional_courses', '전문교과 과목을 반영 대상에 포함할지 나타내는 값이다.'),
    ('apply_grade_weights', '학년별 가중치를 성적 계산에 실제 적용할지 나타내는 값이다.'),
    ('normalize_grade_weights', '선택 과목에 존재하는 학년 가중치 합이 1이 되도록 재정규화할지 나타내는 값이다.'),
    ('intermediate_scale', '중간 계산 결과를 보관할 소수점 이하 자릿수이다.'),
    ('intermediate_rounding', '중간 계산 결과에 적용하는 반올림·절사 방식 코드이다.'),
    ('final_scale', '최종 환산점수를 표시하고 저장할 소수점 이하 자릿수이다.'),
    ('final_rounding', '최종 환산점수에 적용하는 반올림·절사 방식 코드이다.'),
    ('score_multiplier', '집계된 기본점수에 곱해 전형 배점으로 변환하는 배수이다.'),
    ('source_document', '평가 규칙의 근거가 된 모집요강 또는 공식 문서의 명칭이다.'),
    ('source_pages', '평가 규칙 또는 추출값을 확인할 수 있는 근거 문서의 페이지 번호 범위이다.'),
    ('interpretation_note', '모집요강 문구를 시스템 계산 규칙으로 해석한 방법과 예외사항을 기록한다.'),
    ('change_summary', '이 버전에서 이전 규칙과 달라진 내용을 요약한다.'),
    ('achievement_level', '학생부에 기록된 성취도 단계 값이다. 일반적으로 A, B, C 등을 사용한다.'),
    ('converted_grade', '원본 성취도 또는 구 성취도를 평가 규칙에 따라 변환한 환산등급이다.'),
    ('converted_score', '원본 등급 또는 성취도를 평가 규칙에 따라 변환한 환산점수이다.'),
    ('original_file_name', '업로드 당시 사용자가 선택한 원본 파일명으로, 작업 출처를 확인하는 데 사용한다.'),
    ('file_sha256', '업로드 파일 내용으로 계산한 SHA-256 해시값으로 동일 파일 식별과 이력 추적에 사용한다.'),
    ('page_count', '업로드한 모집요강 PDF의 전체 페이지 수이다.'),
    ('text_page_count', '모집요강 PDF에서 기계 판독 가능한 텍스트를 추출한 페이지 수이다.'),
    ('grade_weights_csv', '모집요강에서 추출한 학년별 가중치 후보를 CSV 형태로 보관한 값이다.'),
    ('grade_scores_csv', '모집요강에서 추출한 석차등급별 환산점수 후보를 CSV 형태로 보관한 값이다.'),
    ('achievement_scores_csv', '모집요강에서 추출한 성취도별 환산점수 후보를 CSV 형태로 보관한 값이다.'),
    ('subject_categories_csv', '모집요강에서 추출한 반영 교과군 후보를 CSV 형태로 보관한 값이다.'),
    ('rounding_mode', '모집요강에서 추출한 점수 반올림 또는 절사 방식 후보 코드이다.'),
    ('overall_confidence', '추출 작업 전체 결과의 신뢰도이며 0에서 1 사이 값으로 표현한다.'),
    ('missing_fields', '자동 추출에서 값을 찾지 못한 필드 목록으로 수동 검토 대상을 나타낸다.'),
    ('warnings', '모집요강 자동 추출 과정에서 발견한 모호한 문구와 검토 필요사항이다.'),
    ('field_key', '근거 문구가 설명하는 평가 규칙 필드를 식별하는 키이다.'),
    ('page_number', '해당 근거 문구가 위치한 모집요강 PDF 페이지 번호이다.'),
    ('excerpt', '평가 규칙 값을 뒷받침하는 모집요강 원문 발췌문이다.'),
    ('confidence', '개별 근거 문구와 추출값의 신뢰도이며 0에서 1 사이 값으로 표현한다.'),
    ('grade_value', '과목에 기록된 석차등급 또는 등급 환산표의 입력 등급 값이다.'),
    ('legacy_achievement', '구 교육과정에서 사용한 수·우·미·양·가 등의 성취도 표기이다.'),
    ('subject_category', '과목이 속한 교과군 코드이다. 예: KOREAN, MATH, ENGLISH, SOCIAL, SCIENCE, OTHER.'),
    ('priority_value', '과목 점수가 같을 때 먼저 선택할 교과군 순번이며 값이 작을수록 우선한다.'),
    ('high_school_code', '지원자의 출신 고등학교를 식별하는 학교 코드이다.'),
    ('high_school_name', '지원자의 출신 고등학교 명칭이다.'),
    ('graduation_year', '지원자가 고등학교를 졸업했거나 졸업할 예정인 연도이다.'),
    ('high_school_type', '성적 반영 예외를 결정하기 위해 시스템이 분류한 고교 유형 코드이다. 예: GENERAL, SPECIALIZED.'),
    ('applicant_high_school_category_code', '지원자 추가정보 파일에서 읽은 고교구분 원본 코드로, 대학별 고교 유형 판정과 성적검증에 사용한다.'),
    ('graduation_status', '지원 시점의 졸업 상태 코드이다. 예: EXPECTED_GRADUATE, GRADUATE.'),
    ('ged_average_score', '검정고시 지원자의 전체 과목 평균 취득점수이며 0점에서 100점 범위로 사용한다.'),
    ('school_year', '성적·출결 자료가 해당하는 고등학교 학년으로 1, 2, 3 중 하나이다.'),
    ('semester', '성적 자료가 해당하는 학기이며 1 또는 2이다. 연간 요약자료에서는 NULL일 수 있다.'),
    ('semester_key', '학기 NULL을 0으로 치환해 연간·학기별 요약 데이터의 중복을 방지하는 내부 생성값이다.'),
    ('course_name', '학생부 또는 업로드 파일에 기록된 과목의 원본 명칭이다.'),
    ('course_name_normalized', '공백과 구두점 등을 제거해 사회·문화와 사회문화처럼 표기만 다른 동일 과목을 비교하는 정규화 명칭이다.'),
    ('grade_scale', '해당 과목 등급값이 사용하는 등급 체계 코드이다. 예: NINE_LEVEL, FIVE_LEVEL, LEGACY_ACHIEVEMENT.'),
    ('achievement', '해당 과목에 기록된 A·B·C 등의 성취도 값이다.'),
    ('raw_score', '학생이 해당 과목에서 취득한 원점수이다.'),
    ('mean_score', '해당 과목 수강집단의 평균 원점수이다.'),
    ('standard_deviation', '해당 과목 수강집단 원점수의 표준편차이다.'),
    ('student_count', '해당 과목을 함께 수강한 학생 수이다.'),
    ('rank_position', '해당 과목 또는 성적 요약 범위에서 지원자의 석차이다.'),
    ('tied_rank_count', '해당 석차에 함께 위치한 동석차 학생 수이다.'),
    ('credits', '해당 과목 또는 성적 요약 범위의 이수단위 수이다.'),
    ('career_subject', '해당 과목이 진로선택 과목인지 나타내는 값이다.'),
    ('professional_course', '해당 과목이 전문교과 과목인지 나타내는 값이다.'),
    ('source_file_name', '이 과목 성적을 읽어온 원본 파일명이다.'),
    ('source_row_number', '원본 업로드 파일에서 이 과목 데이터가 위치했던 행 번호이다.'),
    ('import_mode', '오류 발생 시 전체 취소 또는 정상 행만 저장 등 업로드 처리 정책 코드이다.'),
    ('temporary_file_path', '비동기 업로드 처리 동안 원본 파일을 임시 보관하는 서버 내부 경로이며 처리 완료 후 정리 대상이다.'),
    ('source_format', '업로드 파서 또는 가져오기 프로필을 선택하는 대학별 원본 파일 형식 코드이다.'),
    ('total_rows', '업로드 파일에서 처리 대상으로 인식한 전체 데이터 행 수이다.'),
    ('imported_rows', '검증을 통과해 DB에 반영한 데이터 행 수이다.'),
    ('failed_rows', '형식 또는 값 오류로 DB에 반영하지 못한 데이터 행 수이다.'),
    ('error_message', '업로드 실패 또는 중단 원인을 운영자가 확인할 수 있도록 기록한 오류 요약이다.'),
    ('unexcused_absence_days', '해당 학년의 미인정 결석 일수이다.'),
    ('unexcused_tardy_count', '해당 학년의 미인정 지각 횟수이다.'),
    ('unexcused_early_leave_count', '해당 학년의 미인정 조퇴 횟수이다.'),
    ('unexcused_class_absence_count', '해당 학년의 미인정 결과 횟수이다.'),
    ('action_number', '학교폭력예방법에 따른 조치호수 번호이다.'),
    ('action_date', '해당 학교폭력 조치가 결정되거나 처분된 날짜이다.'),
    ('note', '학교폭력 조치 적용과 관련해 운영자가 기록한 참고사항이다.'),
    ('subject_type', '검정고시 과목의 필수·선택 등 과목 구분 코드이다.'),
    ('subject_name', '검정고시 성적표에 기록된 과목명이다.'),
    ('score', '검정고시 해당 과목의 취득점수이며 0점에서 100점 범위이다.'),
    ('summary_type', '구 학제 성적 요약의 범위가 학기별인지 학년별인지 구분하는 코드이다.'),
    ('cohort_size', '석차 산출의 기준이 된 해당 학년 또는 학기 재적 학생 수이다.'),
    ('academic_base_score', '학년·교과 가중치와 전형 배점을 적용하기 전 학생부 교과 기준점수이다.'),
    ('academic_score', '평가 규칙의 가중치와 배점을 적용해 산출한 학생부 교과점수이다.'),
    ('attendance_score', '출결 원천자료를 평가 규칙에 따라 환산한 출결점수이다.'),
    ('additional_score', '검정고시 등 교과·출결 외 정량 요소에서 산출한 추가점수이다.'),
    ('school_violence_deduction', '학교폭력 조치사항에 따라 총점에서 차감하는 점수이다.'),
    ('quantitative_subtotal', '교과점수, 출결점수와 추가점수를 합산한 학교폭력 감점 전 소계이다.'),
    ('score_after_deduction', '정량점수 소계에서 학교폭력 감점을 차감한 점수이다.'),
    ('final_score', '해당 성적 검증 또는 지원 전형 점수 산출의 최종 환산점수이다.'),
    ('average_grade', '성적 검증에서 최종 반영한 과목들의 가중 평균등급이다.'),
    ('included_course_count', '성적 검증 규칙에 따라 최종 점수 계산에 포함한 과목 수이다.'),
    ('excluded_course_count', '성적은 존재하지만 반영 교과·학기·과목 수 등의 규칙으로 계산에서 제외한 과목 수이다.'),
    ('export_format', '결과 다운로드 기능에서 내보내기 프로필을 선택하는 파일 형식 코드이다.'),
    ('schema_version', '대학별 가져오기 또는 내보내기 형식 정의의 버전 문자열이다.'),
    ('replaces_previous_data', '동일 대학·모집연도·파일 범위의 기존 데이터를 새 업로드 내용으로 완전히 교체할지 나타내는 값이다.'),
    ('column_mapping', '업로드 파일의 원본 헤더를 시스템 표준 필드로 연결하는 JSON 매핑 정의이다.'),
    ('column_definition', '결과 파일에 표시할 컬럼, 순서, 헤더와 값 변환 방식을 정의한 JSON 문서이다.');

CREATE TEMPORARY TABLE semantic_column_override (
    table_name VARCHAR(64) NOT NULL,
    column_name VARCHAR(64) NOT NULL,
    comment_text VARCHAR(1024) NOT NULL,
    PRIMARY KEY (table_name, column_name)
);

INSERT INTO semantic_column_override (table_name, column_name, comment_text) VALUES
    ('university', 'code', '대학을 시스템과 외부 연동에서 안정적으로 식별하는 중복 불가 영문 코드이다.'),
    ('university', 'name', '화면과 결과 파일에 표시하는 대학의 공식 명칭이다.'),
    ('admission_track', 'name', '해당 대학·모집연도에 운영하는 전형명이다. 예: 학생부우수자, 학교장추천.'),
    ('recruitment_unit', 'code', '대학이 부여한 학과·학부 등 모집단위 코드이며 없는 경우 NULL이다.'),
    ('recruitment_unit', 'name', '지원자가 지원하는 학과·학부 등 모집단위 명칭이다.'),
    ('student', 'name', '지원자의 성명이다. 개인 식별정보이므로 통계 조회에서는 직접 노출하지 않는다.'),
    ('student', 'education_background', '지원자의 학력 유형 코드이다. 국내고, 검정고시, 외국고 등 성적 계산 경로를 선택한다.'),
    ('evaluation_rule', 'name', '운영자와 검증 결과에서 평가 규칙을 구분해 표시하는 규칙 명칭이다.'),
    ('evaluation_rule', 'status', '평가 규칙의 생명주기 상태이다. DRAFT는 검토 전, REVIEWED는 검토 완료, PUBLISHED는 사용 중, RETIRED는 종료를 의미한다.'),
    ('evaluation_rule_extraction', 'status', '모집요강 규칙 추출 작업의 진행 또는 완료·실패 상태 코드이다.'),
    ('student_transcript_import', 'status', '성적 업로드 작업의 대기·처리 중·완료·실패 상태 코드이다.'),
    ('application_score_run', 'status', '지원 전형 점수 산출의 성공·검토 필요·실패 상태 코드이다.'),
    ('application_score_run', 'education_background', '점수 산출 당시 적용한 지원자의 학력 유형을 보존한 값이다.'),
    ('application_score_run', 'result_json', '교과·출결·추가점수·감점의 계산 단계, 경고와 근거를 보존한 JSON 결과이다.'),
    ('verification_run', 'result_json', '선택·제외 과목, 적용 규칙, 계산 단계와 경고를 보존한 성적 검증 JSON 결과이다.'),
    ('university_import_profile', 'name', '운영 화면에서 대학별 업로드 형식을 구분해 표시하는 프로필 명칭이다.'),
    ('university_export_profile', 'name', '운영 화면에서 대학별 결과 형식을 구분해 표시하는 프로필 명칭이다.');

DELIMITER $$

CREATE PROCEDURE apply_semantic_application_comments()
BEGIN
    DECLARE finished BOOLEAN DEFAULT FALSE;
    DECLARE table_name_value VARCHAR(64);
    DECLARE column_name_value VARCHAR(64);
    DECLARE column_type_value LONGTEXT;
    DECLARE nullable_value VARCHAR(3);
    DECLARE default_value LONGTEXT;
    DECLARE extra_value LONGTEXT;
    DECLARE charset_value VARCHAR(64);
    DECLARE collation_value VARCHAR(64);
    DECLARE generated_expression_value LONGTEXT;
    DECLARE comment_value VARCHAR(1024);
    DECLARE error_message_value VARCHAR(128);
    DECLARE column_cursor CURSOR FOR
        SELECT source.table_name, source.column_name, source.column_type, source.is_nullable,
               source.column_default, source.extra, source.character_set_name, source.collation_name,
               source.generation_expression, COALESCE(overrides.comment_text, common.comment_text)
        FROM information_schema.columns source
        LEFT JOIN semantic_column_override overrides
          ON overrides.table_name = source.table_name
         AND overrides.column_name = source.column_name
        LEFT JOIN semantic_column_comment common
          ON common.column_name = source.column_name
        WHERE source.table_schema = DATABASE()
          AND source.table_name <> 'flyway_schema_history'
        ORDER BY source.table_name, source.ordinal_position;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = TRUE;

    OPEN column_cursor;
    comment_loop: LOOP
        FETCH column_cursor INTO table_name_value, column_name_value, column_type_value,
            nullable_value, default_value, extra_value, charset_value, collation_value,
            generated_expression_value, comment_value;
        IF finished THEN
            LEAVE comment_loop;
        END IF;

        IF comment_value IS NULL OR comment_value = '' THEN
            SET error_message_value = CONCAT('Missing semantic comment: ', table_name_value, '.', column_name_value);
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message_value;
        END IF;

        IF generated_expression_value IS NOT NULL AND generated_expression_value <> '' THEN
            SET @column_definition = CONCAT(
                '`', REPLACE(column_name_value, '`', '``'), '` ', column_type_value,
                ' GENERATED ALWAYS AS (', generated_expression_value, ') ',
                IF(LOWER(extra_value) LIKE '%stored%', 'STORED', 'VIRTUAL')
            );
        ELSE
            SET @column_definition = CONCAT(
                '`', REPLACE(column_name_value, '`', '``'), '` ', column_type_value,
                IF(charset_value IS NULL, '', CONCAT(' CHARACTER SET ', charset_value)),
                IF(collation_value IS NULL, '', CONCAT(' COLLATE ', collation_value)),
                IF(nullable_value = 'NO', ' NOT NULL', ' NULL'),
                CASE
                    WHEN default_value IS NULL THEN ''
                    WHEN UPPER(default_value) LIKE 'CURRENT_TIMESTAMP%' THEN CONCAT(' DEFAULT ', default_value)
                    ELSE CONCAT(' DEFAULT ', QUOTE(default_value))
                END,
                IF(LOWER(extra_value) LIKE '%auto_increment%', ' AUTO_INCREMENT', ''),
                IF(LOWER(extra_value) LIKE '%on update current_timestamp%',
                    CONCAT(' ON UPDATE ', SUBSTRING(extra_value, LOCATE('on update ', LOWER(extra_value)) + 10)),
                    ''
                )
            );
        END IF;

        SET @statement = CONCAT(
            'ALTER TABLE `', REPLACE(table_name_value, '`', '``'), '` MODIFY COLUMN ',
            @column_definition, ' COMMENT ', QUOTE(comment_value)
        );
        PREPARE apply_column_comment_statement FROM @statement;
        EXECUTE apply_column_comment_statement;
        DEALLOCATE PREPARE apply_column_comment_statement;
    END LOOP;
    CLOSE column_cursor;
END$$

DELIMITER ;

CALL apply_semantic_application_comments();
DROP PROCEDURE apply_semantic_application_comments;
DROP TEMPORARY TABLE semantic_column_override;
DROP TEMPORARY TABLE semantic_column_comment;
