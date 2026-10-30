package com.jinhakapply.gradevalidation.operation.dto;

/** A curated record derived from source-control history, not a database audit record. */
public record RuleChangeEntry(String commit, String changedAt, String university, String summary, String reason) {}
