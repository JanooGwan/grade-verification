package com.jinhakapply.gradevalidation.operation.config;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import com.jinhakapply.gradevalidation.operation.service.OperationalMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private final OperationalMetrics metrics;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
            .filter(value -> value.matches("[A-Za-z0-9._-]{1,80}"))
            .orElseGet(() -> UUID.randomUUID().toString());
        long started = System.nanoTime();
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = (System.nanoTime() - started) / 1_000_000;
            String safePath = resolvedPath(request);
            metrics.record(request.getMethod(), safePath, response.getStatus(), durationMillis);
            log.info("http_request method={} path={} status={} durationMs={} requestId={}",
                request.getMethod(), safePath, response.getStatus(), durationMillis, requestId);
            MDC.clear();
        }
    }

    static String resolvedPath(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String value && !value.isBlank()) return value;
        return privacySafePath(request.getRequestURI());
    }

    static String privacySafePath(String path) {
        if (path == null) return "";
        return path
            .replaceAll("(?<=/students/)\\d+(?=/(?:courses|applications|common-data)(?:/|$))", "{studentId}")
            .replaceAll("(?<=/students/)\\d+/[^/]+", "{admissionYear}/{applicantNumber}")
            .replaceAll("(?<=/students/)\\d+", "{studentId}")
            .replaceAll("(?<=/applications/)\\d+", "{applicationId}")
            .replaceAll("(?<=/courses/)\\d+", "{courseId}");
    }
}
