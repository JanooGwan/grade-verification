package com.jinhakapply.gradevalidation.global.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminApiKeyFilter extends OncePerRequestFilter {
    static final String ADMIN_KEY_HEADER = "X-Admin-Key";
    private final boolean enabled;
    private final String configuredKey;

    public AdminApiKeyFilter(
        @Value("${app.security.admin-api.enabled:false}") boolean enabled,
        @Value("${app.security.admin-api.key:}") String configuredKey
    ) {
        this.enabled = enabled;
        this.configuredKey = configuredKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        if (configuredKey.isBlank()) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "관리자 API 인증이 활성화되었지만 키가 설정되지 않았습니다.");
            return;
        }
        String supplied = request.getHeader(ADMIN_KEY_HEADER);
        if (supplied == null || !MessageDigest.isEqual(
            configuredKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "관리자 API 인증에 실패했습니다.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"ADMIN_AUTH_REQUIRED\",\"message\":\"" + message
            + "\",\"fieldErrors\":{}}");
    }
}
