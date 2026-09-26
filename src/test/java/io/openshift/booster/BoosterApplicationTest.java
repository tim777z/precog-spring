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
package io.openshift.booster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end smoke test over a real HTTP port.
 *
 * <p>This replaces {@code BoosterApplicationTest} as it used to be written: that class
 * depended on {@code com.jayway.restassured} (an abandoned library) and on a
 * {@code ${local.server.port}} placeholder that only the retired booster parent POM
 * supplied, so it could not run on a clean checkout. The companion
 * {@code OpenShiftIT} required a live OpenShift cluster and the same abandoned stack; the
 * ConfigMap behaviour it used to verify is now covered by
 * {@code GreetingControllerTest} plus the unit tests, none of which need a cluster.
 *
 * <p>What only a full context can prove is exercised here: that the shipped
 * {@code application.yml} yields a startable application, that actuator is actually wired
 * up, and that the security headers reach the wire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BoosterApplicationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("the packaged configuration starts and serves a greeting")
    void servesTheGreetingEndpoint() {
        final ResponseEntity<String> response = rest.getForEntity("/api/greeting", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"content\":\"Hello, World!\"");
    }

    @Test
    void servesTheGreetingEndpointWithAName() {
        final ResponseEntity<String> response =
                rest.getForEntity("/api/greeting?name=John", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"content\":\"Hello, John!\"");
    }

    @Test
    @DisplayName("actuator health is reachable and reports UP")
    void reportsHealthy() {
        final ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("health details are not disclosed to unauthenticated callers")
    void doesNotDiscloseHealthDetails() {
        final ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        // Component-level detail names databases, disk paths and downstream hosts, which is
        // reconnaissance rather than information a liveness probe needs.
        assertThat(response.getBody()).doesNotContain("\"components\"", "\"details\"");
    }

    @Test
    @DisplayName("the liveness probe path required by Kubernetes is exposed")
    void exposesLivenessProbe() {
        assertThat(rest.getForEntity("/actuator/health/liveness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void sendsSecurityHeadersOnEveryResponse() {
        final ResponseEntity<String> response = rest.getForEntity("/api/greeting", String.class);

        assertThat(response.getHeaders().getFirst("Content-Security-Policy"))
                .contains("default-src 'none'");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    @DisplayName("the bundled UI is served with the hardening the policy depends on")
    void servesTheUiWithoutInlineScriptOrStyle() {
        final ResponseEntity<String> response = rest.getForEntity("/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The strict policy forbids 'unsafe-inline', so the page must externalise both.
        assertThat(response.getBody())
                .contains("app.js")
                .contains("app.css")
                .doesNotContain("<script>");
    }
}
