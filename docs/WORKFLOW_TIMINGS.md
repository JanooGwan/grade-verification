# 성적 작업 시간 기록

백엔드를 재시작한 뒤 새 작업부터 INFO 로그에 밀리초 단위의 경과 시간을 기록한다.
기본 로그 위치는 실행 디렉터리의 `logs/grade-validation.log`이며 `LOG_FILE`로 변경할 수 있다.
로그 회전·보존 정책이 적용되므로 영구 보관을 보장하지 않는다. 수험번호, 이름, 점수,
파일 내용, SQL 전문은 기록하지 않는다. 시간은 CPU 시간이 아닌 대기 시간을 포함한 경과 시간이다.

## 명지 ZIP / 단일 CSV 저장

- `mjc_upload_copy`: `importId`, 형식, 서버의 임시 파일 복사 시간과 바이트 수. 요청이 애플리케이션에
  도착한 이후의 복사만 측정하며 브라우저 전송과 서블릿 임시 파일 수신 시간은 포함하지 않는다.
- `mjc_import_timings`: 검증, 저장 준비, 지원자·과목 저장, 완료 등 단계별 누적 시간과 완료/실패 상태.
  단계는 실제로 도달한 것만 나온다. 반복되는 과목 배치의 동일 단계는 합산한다. `total`은 전체
  백그라운드 작업이며 단계 시간과 중첩된다. `FINALIZE`에는 트랜잭션 완료 대기가 포함될 수 있다.
  기존 삼육·명지 구형 CSV/Excel 경로에는 이 상세 저장 시간 기록이 적용되지 않는다.

## 저장된 학생부 성적 검증

- `verification_stage`: 작업 단계가 바뀌는 시각을 기록한다. 개별 학생별 로그는 남기지 않는다.
- `verification_timings`: `input_load`, `previous_result_delete`, `evaluation_loop_inclusive`,
  `result_json`, `result_buffer_and_db_write`, `final_db_flush`, `finalize`, `service_body`,
  `persist_call_including_transaction` 등을 기록한다. 실패 시에도 도달한 시간만 남는다.
- `evaluationOtherMs`는 전체 검증 반복 시간에서 JSON 변환과 버퍼·DB 쓰기 시간을 뺀 추정치다.
  순수 산식뿐 아니라 규칙 조회·매칭·객체 생성 등도 포함한다. 마지막 별도 `final_db_flush`는
  반복 구간 밖이므로 빼지 않는다. 각 값은 ms 단위로 반올림 없이 절삭된다.
- `transactionAndProxyOverheadMs`는 트랜잭션 호출 전체에서 서비스 메서드 본문을 뺀 값으로,
  성공 시 커밋과 프록시 오버헤드를 함께 포함한다. 실패 시 롤백 등도 포함하며 순수 커밋 시간은 아니다.
- 삼육대 시나리오 경로는 `syu_scenario_inclusive`로 묶어 기록한다. 조회·계산·저장을 분리한 값은 없다.

## 저장 검증 결과 엑셀 내보내기

- `verification_export_stage`: 결과 DB 조회 시작과 50페이지 단위 진행, XLSX 생성 시작을 기록한다.
  멈춘 조회도 시작 시각을 확인할 수 있다.
- `verification_export_timings`: `batch_db`, `result_db`, `detail_assembly_including_rule_db`,
  `rule_db`, `applications_db`, `failure_reasons_db`, `xlsx_generation_and_write`, `export_total`을 기록한다.
  `detail_assembly_including_rule_db`에는 `rule_db`가 포함되므로 별도로 더하면 중복된다.
  `xlsx_generation_and_write`는 모든 시트 구성과 출력 스트림 쓰기를 함께 잰다.
  단계는 실제 도달한 것만 나오고 실패 시에도 도달한 시간이 남는다.
- 삼육대 특화 내보내기는 DB 조회부터 XLSX 생성까지 `syu_export_inclusive` 하나로 기록한다.
- 별도의 기존 `Saved verification export completed`는 전체 작업 시간과 결과 파일 크기를 제공한다.
  생성 후 브라우저 다운로드 시간은 위 생성 측정에 포함되지 않는다.
