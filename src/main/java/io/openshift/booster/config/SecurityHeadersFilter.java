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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the response security headers that protect the bundled UI and the JSON API.
 *
 * <p>{@code static/index.html} is served from the same origin as the API, so any markup
 * injection that reached a response body would otherwise execute with the application's
 * origin. The Content-Security-Policy below is written to contain that: {@code default-src
 * 'none'} denies everything and only the application's own files are re-adopted.
 *
 * <p>Because neither {@code 'unsafe-inline'} nor a nonce is used, the page deliberately
 * carries no inline script and no inline style -- they live in {@code static/app.js} and
 * {@code static/app.css}. Adopting a weaker policy just to permit inline code would give up
 * most of the protection.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    /**
     * Strict, nonce-free policy. {@code connect-src 'self'} still allows the page to call
     * {@code /api/greeting}; {@code form-action 'none'} and {@code base-uri 'none'} remove
     * two injection aids, and {@code frame-ancestors 'none'} blocks clickjacking.
     */
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; "
            + "script-src 'self'; "
            + "style-src 'self'; "
            + "img-src 'self' data:; "
            + "connect-src 'self'; "
            + "form-action 'none'; "
            + "base-uri 'none'; "
            + "frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {

        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        // Stops a browser second-guessing a declared content type, which is what turns an
        // attacker-supplied file into script.
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Legacy clickjacking defence for clients that predate CSP frame-ancestors.
        response.setHeader("X-Frame-Options", "DENY");
        // Do not leak internal URLs to third parties through the Referer header.
        response.setHeader("Referrer-Policy", "no-referrer");
        // Do not hand this window a handle on the opener (reverse tabnabbing).
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        // This service needs no device capabilities; deny them all.
        response.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=(), payment=()");

        filterChain.doFilter(request, response);
    }
}
