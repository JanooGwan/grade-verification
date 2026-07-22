package com.jinhakapply.gradevalidation.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminApiKeyFilterTest {

    @Test
    void protectsEveryApiWhenEnabledAndAcceptsMatchingKey() throws Exception {
        AdminApiKeyFilter filter = new AdminApiKeyFilter(true, "test-admin-key");
        MockHttpServletRequest deniedRequest = new MockHttpServletRequest("GET", "/api/transcripts/students");
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();

        filter.doFilter(deniedRequest, deniedResponse, new MockFilterChain());

        assertThat(deniedResponse.getStatus()).isEqualTo(401);
        assertThat(deniedResponse.getContentAsString()).doesNotContain("test-admin-key");

        MockHttpServletRequest allowedRequest = new MockHttpServletRequest("GET", "/api/operations/dashboard");
        allowedRequest.addHeader(AdminApiKeyFilter.ADMIN_KEY_HEADER, "test-admin-key");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(allowedRequest, allowedResponse, chain);

        assertThat(chain.getRequest()).isSameAs(allowedRequest);
    }

    @Test
    void failsClosedWhenEnabledWithoutConfiguredKey() throws Exception {
        AdminApiKeyFilter filter = new AdminApiKeyFilter(true, "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/universities"), response,
            new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
    }
}
