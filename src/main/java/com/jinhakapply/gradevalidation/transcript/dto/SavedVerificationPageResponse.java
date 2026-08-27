package com.jinhakapply.gradevalidation.transcript.dto;

import java.util.List;

public record SavedVerificationPageResponse(
    List<SavedVerificationResultRow> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {}
