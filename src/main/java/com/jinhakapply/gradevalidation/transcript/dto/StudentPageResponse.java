package com.jinhakapply.gradevalidation.transcript.dto;

import java.util.List;

public record StudentPageResponse(
    List<StudentSummaryResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
}
