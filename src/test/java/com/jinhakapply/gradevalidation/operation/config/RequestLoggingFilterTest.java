package com.jinhakapply.gradevalidation.operation.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingFilterTest {

    @Test
    void masksApplicantAndEntityIdentifiersBeforeLogging() {
        assertThat(RequestLoggingFilter.privacySafePath("/api/transcripts/students/2027/A-001"))
            .isEqualTo("/api/transcripts/students/{admissionYear}/{applicantNumber}");
        assertThat(RequestLoggingFilter.privacySafePath("/api/transcripts/students/123/courses/456"))
            .isEqualTo("/api/transcripts/students/{studentId}/courses/{courseId}");
        assertThat(RequestLoggingFilter.privacySafePath("/api/applications/789/scores"))
            .isEqualTo("/api/applications/{applicationId}/scores");
    }

    @Test
    void prefersTheActualHandlerPatternWhenSpringResolvedOne() {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET", "/api/admissions/students/123/verifications/456"
        );
        request.setAttribute(
            HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
            "/api/admissions/students/{studentId}/verifications/{runId}"
        );

        assertThat(RequestLoggingFilter.resolvedPath(request))
            .isEqualTo("/api/admissions/students/{studentId}/verifications/{runId}");
    }
}
