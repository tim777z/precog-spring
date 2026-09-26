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
package io.openshift.booster.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link ApiExceptionHandler}.
 *
 * <p>The security-relevant property under test is that no handler ever copies an exception
 * message, type or stack trace into the response body.
 */
class ApiExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final ApiExceptionHandler handler = new ApiExceptionHandler(
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void mapsAnUnusableNameToBadRequest() {
        final ResponseEntity<ApiError> response =
                handler.handleInvalidName(new InvalidGreetingNameException("name must not be blank"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .extracting(ApiError::code, ApiError::message, ApiError::path, ApiError::timestamp)
                .containsExactly("invalid_name", "name must not be blank", "/api/greeting", NOW);
    }

    @Test
    void mapsAFrameworkRejectionToBadRequestWithoutEchoingIt() {
        final ResponseEntity<ApiError> response =
                handler.handleBadRequest(new IllegalStateException("com.example.Secret: token=hunter2"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .extracting(ApiError::code, ApiError::message)
                .containsExactly("bad_request", "The request was malformed.");
        assertThat(response.getBody().message()).doesNotContain("hunter2");
    }

    @Test
    void mapsAnUnexpectedFailureToFiveHundredWithoutLeakingDetail() {
        final ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("connection to jdbc:postgresql://db:5432 failed"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .extracting(ApiError::code, ApiError::message)
                .containsExactly("server_error", "The request could not be completed.");
        assertThat(response.getBody().message()).doesNotContain("jdbc", "db:5432");
    }

    @Test
    void keepsTheStatusOfAFrameworkErrorResponse() {
        // A 404 must stay a 404: a blanket fallback handler that promoted every unknown
        // exception to 500 would make routine routing misses look like an outage.
        final ResponseEntity<ApiError> response = handler.handleUnexpected(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No static resource api/nope"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).extracting(ApiError::code).isEqualTo("request_rejected");
        assertThat(response.getBody().message()).doesNotContain("No static resource");
    }

    @Test
    void keepsAServerErrorStatusFromAFrameworkErrorResponse() {
        final ResponseEntity<ApiError> response = handler.handleUnexpected(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "pool exhausted"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().message()).doesNotContain("pool exhausted");
    }

    private static HttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/greeting");
    }
}
