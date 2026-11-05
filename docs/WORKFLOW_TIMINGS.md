# 성적 작업 시간 기록

백엔드를 재시작한 뒤 새 작업부터 INFO 로그에 밀리초 단위의 경과 시간을 기록한다.
기본 로그 위치는 실행 디렉터리의 `logs/grade-validation.log`이며 `LOG_FILE`로 변경할 수 있다.
로그 회전·보존 정책이 적용되므로 영구 보관을 보장하지 않는다. 수험번호, 이름, 점수,
파일 내용, SQL 전문은 기록하지 않는다. 시간은 CPU 시간이 아닌 대기 시간을 포함한 경과 시간이다.

## 작업 중 자원 및 트랜잭션 관찰

- 명지 ZIP·단일 CSV 업로드, 저장 성적검증 작업, 저장 결과 내보내기에서 `workflow_jvm`은 시작·종료의
  JVM 힙과 JVM 전체 GC 횟수·누적 시간 및 작업 시작 이후 증분을 기록한다. 다른 동시 작업의 GC도 포함되며
  GC 시간은 해당 작업이 멈춘 순수 시간이라고 단정할 수 없다. 비동기 표본에도 현재 누적값을 기록한다.
- 작업 중 약 30초 간격의 `workflow_resource_sample`은 서버 Windows의 D: 평균 전송 지연·대기열·유휴율·
  읽기/쓰기 속도와 시스템 가용 RAM을 기록한다. 서버 전체 값이며 특정 프로세스의 사용량은 아니다.
  조회·프로세스 시작 시간에 따라 실제 간격은 더 길 수 있고, 비 Windows나 접근 거부 시
  `workflow_resource_unavailable`을 남긴다. 서버의 D:가 MySQL 저장 장치라는 보장은 없다.
- `workflow_mysql_sample`은 별도 커넥션의 `performance_schema.data_lock_waits` 전체 건수로 특정 순간의
  InnoDB 행 잠금 대기만 보여준다. 전체 서버 관측값이며 이 작업이 대기 중임을 뜻하지 않는다. 메타데이터 잠금,
  I/O, 커밋 지연은 포함하지 않는다. performance_schema 비활성화 또는 조회 권한 부재 등은 측정 불가로
  기록하며 0으로 오인하지 않는다. 2초 SQL 제한 외에 커넥션 풀 대기 시간이 있을 수 있다. 별도 데몬
  스레드에서 실행되므로 수집 실패는 본 작업을 중단하지 않지만 별도 DB 커넥션 한 개를 잠시 사용한다.
- 작업 ID는 업로드/내보내기의 import ID, 검증은 job UUID다. 원본별 검증 상세는 기존 로그의 대학·연도와
  시간 및 sourceImportId로 연결한다. JVM 통계는 검증은 커밋 이후, 내보내기는 메서드 종료 시점까지다.
- `workflow_transaction_start/end`는 트랜잭션의 `beforeCommit`부터 `afterCompletion`까지 시간과 완료·
  롤백 상태를 남긴다. flush, Spring 동기화 및 DB 커밋이 함께 포함될 수 있어 순수 DB 커밋 시간은 아니다.
  롤백으로 beforeCommit에 이르지 못한 경우 시간은 null이다. 내보내기 읽기 전용 트랜잭션도 기록한다.
- 수집을 끄려면 `app.monitoring.workflow-resources.enabled=false`, 대상 드라이브를 변경하려면
  `app.monitoring.workflow-resources.drive=E:`를 설정한다. 자원 수집과 트랜잭션 측정은 새로 시작하는
  작업부터 적용되며 개인 정보와 원본 SQL을 기록하지 않는다.

## 명지 ZIP / 단일 CSV 저장

- `mjc_upload_copy`: `importId`, 형식, 서버의 임시 파일 복사 시간과 바이트 수. 요청이 애플리케이션에
  도착한 이후의 복사만 측정하며 브라우저 전송과 서블릿 임시 파일 수신 시간은 포함하지 않는다.
- `mjc_import_timings`: 검증, 저장 준비, 지원자·과목 저장, 완료 등 단계별 누적 시간과 완료/실패 상태.
  단계는 실제로 도달한 것만 나온다. 반복되는 과목 배치의 동일 단계는 합산한다. `total`은 전체
  백그라운드 작업이며 단계 시간과 중첩된다. `FINALIZE`에는 트랜잭션 완료 대기가 포함될 수 있다.
  기존 삼육·명지 구형 CSV/Excel 경로에는 이 상세 저장 시간 기록이 적용되지 않는다.
- `mjc_import_detail_start/end`: 명지 가져오기에서 기존 학생 조회와 가져오기 스냅샷·검증·지원·과목
  삭제, 원본에서 사라진 학생 삭제 각각의 시작과 종료를 즉시 기록한다. `rows`는 직접 영향 행 수이며 연쇄 삭제 행을
  포함하지 않을 수 있다. 실패 시 예외 전문이나 SQL은 기록하지 않는다. 종료 전에는 시작 로그로
  현재 오래 걸리는 작업을 찾을 수 있다. 모든 건수는 트랜잭션 커밋 전 잠정 값이다.
- `mjc_import_batch_start`와 `mjc_import_batch_write_start/end`: 과목 배치 번호·행 수와 읽기 간격,
  현재 과목 및 이력 스냅샷 저장 각각의 경과 시간을 기록한다. `bindMs`는 JDBC 매개변수 준비의
  누적 시간이고 `elapsedMs`에 포함된다. 나머지는 JDBC 드라이버·DB 실행·디스크·대기 등이 함께
  포함되므로 순수 SQL 실행 시간이나 디스크 지연으로 단정할 수 없다. 읽기 간격도 CSV 파싱 등의
  경과 시간이며 순수 디스크 읽기 시간이 아니다. 배치 종료는 트랜잭션 커밋을 뜻하지 않는다.
  시작 로그만 있으면 해당 작업이 진행 중이거나 중단된 상태다. 최신 실행의 전체 완료 여부는 기존
  `mjc_import_timings` 및 가져오기 상태로 확인한다. 해당 상세 로그는 재시작 후 시작한 작업에만 적용된다.

## 저장된 학생부 성적 검증

- `verification_stage`: 작업 단계가 바뀌는 시각을 기록한다. 개별 학생별 로그는 남기지 않는다.
- `verification_timings`: `input_load`, `previous_result_delete`, `evaluation_loop_inclusive`,
  `result_json`, `result_buffer_and_db_write`, `final_db_flush`, `finalize`, `service_body`,
  `persist_call_including_transaction` 등을 기록한다. 실패 시에도 도달한 시간만 남는다.
- `evaluationOtherMs`는 전체 검증 반복 시간에서 JSON 변환과 버퍼·DB 쓰기 시간을 뺀 추정치다.
  순수 산식뿐 아니라 규칙 조회·매칭·객체 생성 등도 포함한다. 마지막 별도 `final_db_flush`는
  반복 구간 밖이므로 빼지 않는다. 각 값은 ms 단위로 반올림 없이 절삭된다.
- `rules_load`는 게시 규칙 조회, `course_grouping`은 과목의 지원자별 묶기,
  `grade_calculation`은 일반 성적 계산 호출, `ged_calculation`은 명지 검정고시 계산,
  `intermediate_calculation`은 원자료 중간 계산의 누적 경과 시간이다. 해당 경로를 사용하지 않으면
  항목이 나타나지 않는다. 모두 `evaluation_loop_inclusive` 및 `evaluationOtherMs`에 포함되므로
  더하면 중복된다. 계산 시간은 CPU 시간만이 아닌 대기와 GC도 포함한다.
- `transactionAndProxyOverheadMs`는 트랜잭션 호출 전체에서 서비스 메서드 본문을 뺀 값으로,
  성공 시 커밋과 프록시 오버헤드를 함께 포함한다. 실패 시 롤백 등도 포함하며 순수 커밋 시간은 아니다.
- 기존 요약의 삼육대 시나리오 경로는 `syu_scenario_inclusive`로 묶인다. 아래 상세 로그에서
  결과 삭제와 배치 저장을 별도로 볼 수 있지만 시나리오 입력 조회와 계산은 분리되지 않는다.
- `workflow_detail_start/end`: 지원·과목·학교·원자료 조회, 기존 결과 삭제, 전체 검증 및 저장,
  마지막 버퍼 저장, 미산출 사유 저장의 시작과 종료를 즉시 기록한다. 삼육 시나리오의 기존 결과
  삭제·검증 및 저장·저장 배치도 별도로 기록한다. `sourceImportId`로 기존 로그와 연결한다.
- `verification_batch_start/end`: 결과 저장 묶음의 번호, 행 수, 추정 바이트와 JDBC 호출 경과 시간을
  기록한다. 조회·드라이버·DB·대기 시간이 포함되며 완료 건수는 커밋 전 잠정 값이다.
- `verification_batch_budget`은 결과 묶음의 추정 바이트 상한이다. 서버의 `max_allowed_packet`을
  조회해 그 1/4과 4MiB 중 작은 값을 사용한다. 조회 실패 또는 유효하지 않은 값은 512KiB로 대체한다.
  상한은 실제 전송 바이트가 아닌 UTF-8 변환과 SQL 문자열 이스케이프를 고려한 보수적 추정치다.
  한 행만으로 상한을 넘는 경우 이전과 같이 단독 저장을 시도하며, DB 패킷 제한을 초과하면 실패한다.

## 저장 검증 결과 엑셀 내보내기

- `verification_export_stage`: 결과 DB 조회 시작과 50페이지 단위 진행, XLSX 생성 시작을 기록한다.
  멈춘 조회도 시작 시각을 확인할 수 있다.
- `verification_export_timings`: `batch_db`, `result_db`, `detail_assembly_including_rule_db`,
  `rule_db`, `applications_db`, `failure_reasons_db`, `xlsx_generation_and_write`, `export_total`을 기록한다.
  `detail_assembly_including_rule_db`에는 `rule_db`가 포함되므로 별도로 더하면 중복된다.
  `xlsx_generation_and_write`는 모든 시트 구성과 출력 스트림 쓰기를 함께 잰다.
  단계는 실제 도달한 것만 나오고 실패 시에도 도달한 시간이 남는다.
- 기존 요약의 삼육대 특화 내보내기는 DB 조회부터 XLSX 생성까지 `syu_export_inclusive`로 묶인다.
- `verification_export_page_start/end`: 결과 조회 페이지별 시작, 행 수, DB 조회와 결과 조립 시간을
  기록한다. 조립 시간에는 규칙 조회와 JSON 해석이 포함된다.
- `workflow_detail_start/end`: 결과 메타데이터·지원정보·실패 사유 조회, 각 Excel 시트 작성,
  최종 파일 쓰기의 시작·종료를 기록한다. 삼육대는 결과 스트리밍 조회와 행 작성이 같은 구간이며
  `syu_export_rows`로 5,000행마다 진행을 표시하고 최종 파일 쓰기는 따로 기록한다.
  단계 시작만 있고 종료가 없으면 진행 중이거나 프로세스 중단 상태일 수 있다. 파일 쓰기 완료는
  브라우저 다운로드 완료를 의미하지 않는다. 상세 로그에는 입력값이나 SQL 전문을 남기지 않는다.
- 별도의 기존 `Saved verification export completed`는 전체 작업 시간과 결과 파일 크기를 제공한다.
  생성 후 브라우저 다운로드 시간은 위 생성 측정에 포함되지 않는다.
