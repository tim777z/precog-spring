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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates exceptions into the {@link ApiError} shape.
 *
 * <p>Without this the default error page is a {@code text/html} body that varies by
 * exception type, and the default {@code server.error.include-stacktrace} in the versions
 * this service previously shipped echoed the exception and stack trace to the caller. A
 * stack trace is free reconnaissance: it names framework versions, internal class names and
 * file paths, all of which turn a probing request into a targeted exploit.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String GENERIC_SERVER_ERROR = "The request could not be completed.";

    /**
     * Used for a 4xx/5xx code that is not in the {@link HttpStatus} registry, so no standard
     * phrase exists to look up.
     */
    private static final String GENERIC_REJECTION = "The request was rejected.";

    private final Clock clock;

    public ApiExceptionHandler() {
        this(Clock.systemUTC());
    }

    ApiExceptionHandler(final Clock clock) {
        this.clock = clock;
    }

    /** A name the caller supplied is not acceptable. */
    @ExceptionHandler(InvalidGreetingNameException.class)
    public ResponseEntity<ApiError> handleInvalidName(
            final InvalidGreetingNameException ex, final HttpServletRequest request) {
        LOG.debug("Rejected greeting name on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "invalid_name", ex.getMessage(), request);
    }

    /**
     * Requests the framework itself rejects: a constraint violation, an unparseable body, a
     * missing parameter or a parameter of the wrong type.
     */
    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(
            final Exception ex, final HttpServletRequest request) {
        LOG.debug("Rejected request {} {}: {}", request.getMethod(), request.getRequestURI(), ex.toString());
        return respond(HttpStatus.BAD_REQUEST, "bad_request", "The request was malformed.", request);
    }

    /**
     * Everything else.
     *
     * <p>Framework exceptions that already know their own status (404, 405, 415 and friends
     * all implement {@link ErrorResponse}) keep it, so this fallback cannot accidentally
     * turn a missing route into a server error. Anything else becomes a 500 with a fixed
     * sentence; the exception itself is logged with its stack trace and never returned.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            final Exception ex, final HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            final HttpStatusCode status = errorResponse.getStatusCode();
            if (status.is5xxServerError()) {
                LOG.error("Request {} {} failed", request.getMethod(), request.getRequestURI(), ex);
                return respond(status, "server_error", GENERIC_SERVER_ERROR, request);
            }
            LOG.debug("Request {} {} rejected: {}", request.getMethod(), request.getRequestURI(), ex.toString());
            return respond(status, "request_rejected", reasonPhrase(status), request);
        }

        LOG.error("Request {} {} failed with an unhandled exception", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", GENERIC_SERVER_ERROR, request);
    }

    private ResponseEntity<ApiError> respond(
            final HttpStatusCode status,
            final String code,
            final String message,
            final HttpServletRequest request) {
        final ApiError body =
                new ApiError(code, message, request.getRequestURI(), Instant.now(clock));
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Maps a status code to a human-readable phrase.
     *
     * <p>{@link HttpStatusCode} is an interface and deliberately carries no reason phrase --
     * only {@link HttpStatus}, the enum of registered codes, has one. A code outside that
     * registry has no phrase at all, so the lookup is by value and falls back to a constant.
     *
     * <p>Deriving the phrase from the registry rather than from the exception keeps the
     * response body free of caller-influenced text: the only strings that can reach the client
     * this way are the fixed phrases declared in {@link HttpStatus}. An unknown code cannot be
     * echoed back either, which is what stops a custom {@code HttpStatusCode} implementation
     * from smuggling a string from the request into the error body.
     */
    private static String reasonPhrase(final HttpStatusCode status) {
        final HttpStatus registered = HttpStatus.resolve(status.value());
        return registered == null ? GENERIC_REJECTION : registered.getReasonPhrase();
    }
}
