/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openshift.booster.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link SecurityHeadersFilter}.
 *
 * <p>These assertions are the executable form of the policy: a later change that widens the
 * Content-Security-Policy or drops a header fails here rather than in a browser.
 */
class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void setsAContentSecurityPolicyThatAllowsOnlyTheApplicationsOwnFiles() throws Exception {
        final String policy = header("Content-Security-Policy");

        assertThat(policy)
                .contains("default-src 'none'")
                .contains("script-src 'self'")
                .contains("style-src 'self'")
                .contains("connect-src 'self'")
                .contains("form-action 'none'")
                .contains("base-uri 'none'")
                .contains("frame-ancestors 'none'");
    }

    @Test
    void neverAllowsInlineOrEvalScript() {
        assertThat(header("Content-Security-Policy"))
                .doesNotContain("unsafe-inline")
                .doesNotContain("unsafe-eval")
                .doesNotContain("*");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "X-Content-Type-Options",
            "X-Frame-Options",
            "Referrer-Policy",
            "Cross-Origin-Opener-Policy",
            "Cross-Origin-Resource-Policy",
            "Permissions-Policy",
            "Content-Security-Policy"
    })
    void setsEverySecurityHeader(final String name) throws Exception {
        assertThat(header(name)).isNotBlank();
    }

    @Test
    void doesNotSniffContentTypes() throws Exception {
        assertThat(header("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void deniesFraming() throws Exception {
        assertThat(header("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void deniesEveryDeviceCapabilityItDoesNotNeed() throws Exception {
        assertThat(header("Permissions-Policy")).contains("geolocation=()", "payment=()");
    }

    @Test
    void continuesTheChain() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/greeting"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("Content-Security-Policy")).isNotBlank();
    }

    private String header(final String name) throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/"), response, new MockFilterChain());
        return response.getHeader(name);
    }

    /**
     * Guards the constant against drifting away from the value actually sent on the wire.
     */
    @Test
    void keepsThePolicyConstantAndTheSentHeaderIdentical() throws Exception {
        assertThat(header("Content-Security-Policy"))
                .isEqualTo(SecurityHeadersFilter.CONTENT_SECURITY_POLICY)
                .contains("img-src 'self' data:");
    }
}
