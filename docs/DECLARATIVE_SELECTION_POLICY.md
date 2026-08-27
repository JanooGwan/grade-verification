# 선언형 과목 선택 정책

대학별 과목 선택 방식을 학교 이름에 종속된 Java 분기로 추가하지 않고, 버전이 있는 정책 데이터로 조합하기 위한 기능이다. 기존 `selection_strategy`는 이전 규칙과의 호환을 위해 유지되며, `selection_policy`가 설정된 규칙은 선언형 정책을 우선 사용한다.

## 처리 순서

1. 기존 규칙의 학년·학기·등급 환산 조건으로 계산 가능한 후보 과목을 만든다.
2. `filter`로 교과군과 일반·진로·전문교과 포함 여부를 제한한다.
3. `stages`를 앞에서부터 실행해 과목 또는 그룹을 선택한다.
4. 기존 환산표, 학년·교과 가중치, 집계 및 반올림 정책으로 점수를 계산한다.

정책은 초안 상태의 규칙에만 `PATCH /api/evaluations/rules/{ruleId}/selection-policy`로 설정할 수 있다. 검수·게시된 규칙은 직접 수정하지 않고 새 규칙 버전을 만들어야 한다.

## 예시

국어·영어·수학·사회·과학에서 환산점수가 높은 12과목을 선택하는 정책이다.

```json
{
  "policy": {
    "schemaVersion": 1,
    "filter": {
      "subjects": ["KOREAN", "ENGLISH", "MATH", "SOCIAL", "SCIENCE"],
      "includeGeneralCourses": true,
      "includeCareerCourses": true,
      "includeProfessionalCourses": false
    },
    "stages": [
      {
        "type": "TOP_COURSES",
        "partitionBy": "NONE",
        "groupBy": "NONE",
        "metric": "CONVERTED_SCORE",
        "direction": "DESC",
        "limit": 12
      }
    ]
  }
}
```

학년별로 두 학기 중 평균 등급이 좋은 한 학기를 고르는 경우에는 다음 단계만 교체한다.

```json
{
  "type": "TOP_GROUPS",
  "partitionBy": "SCHOOL_YEAR",
  "groupBy": "SEMESTER",
  "metric": "EFFECTIVE_GRADE",
  "direction": "ASC",
  "limit": 1
}
```

지원하는 그룹 기준은 `NONE`, `SCHOOL_YEAR`, `SEMESTER`, `SCHOOL_YEAR_AND_SEMESTER`, `SUBJECT`, `COURSE_TYPE`이다. 선택 기준은 `EFFECTIVE_GRADE`, `CONVERTED_SCORE`, `CREDITS`이며 여러 단계를 순서대로 연결할 수 있다.

출결, 학교폭력 감점, 검정고시 비교내신처럼 과목 선택 이후의 독립 점수 구성요소는 이 정책의 범위가 아니다. 해당 영역은 별도의 선언형 구성요소로 확장하거나, 모집요강상 예외가 큰 경우 기존 Java 계산기 확장 포인트를 사용한다.
