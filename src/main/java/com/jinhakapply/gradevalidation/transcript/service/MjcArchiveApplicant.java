package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;
import java.util.List;

/** Source metadata retained separately from calculated grades. Never logged. */
record MjcArchiveApplicant(
    String sourceApplicantId, String examNumber, int part, String academicType,
    String studentRecordId, String gedRecordId, String issue,
    String acceptedDate, List<GedSubject> gedSubjects
) {
    record GedSubject(String id, String name, BigDecimal score) {}
}
