package com.jinhakapply.gradevalidation.transcript.service;

import java.util.Map;
import java.util.Set;

record VocationalTrainingParseResult(
    Map<String, Set<VocationalTrainingSemester>> semestersByApplicant
) {
    static VocationalTrainingParseResult empty() {
        return new VocationalTrainingParseResult(Map.of());
    }

    int applicantCount() {
        return semestersByApplicant.size();
    }

    int semesterCount() {
        return semestersByApplicant.values().stream().mapToInt(Set::size).sum();
    }

    Set<VocationalTrainingSemester> semesters(String applicantNumber) {
        return semestersByApplicant.getOrDefault(applicantNumber, Set.of());
    }
}
