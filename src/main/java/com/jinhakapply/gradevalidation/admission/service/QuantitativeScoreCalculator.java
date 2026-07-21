package com.jinhakapply.gradevalidation.admission.service;

import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreResult;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;

public interface QuantitativeScoreCalculator {
    boolean supports(EvaluationRule rule);

    ApplicationScoreResult calculate(
        EvaluationRule rule,
        String admissionTrackName,
        GradeVerificationResponse gradeVerification,
        CalculateApplicationScoreRequest request,
        StudentCommonEvaluationSnapshot commonData
    );
}
