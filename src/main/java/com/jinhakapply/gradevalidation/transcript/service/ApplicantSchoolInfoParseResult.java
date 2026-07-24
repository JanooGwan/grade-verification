package com.jinhakapply.gradevalidation.transcript.service;

import java.util.List;
import java.util.Map;

record ApplicantSchoolInfoParseResult(
    List<ApplicantSchoolInfoRow> rows,
    Map<String, ApplicantSchoolInfoRow> byApplicantNumber
) {
    static ApplicantSchoolInfoParseResult empty() {
        return new ApplicantSchoolInfoParseResult(List.of(), Map.of());
    }
}
