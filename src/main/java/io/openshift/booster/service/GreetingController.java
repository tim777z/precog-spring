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

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only greeting endpoint.
 *
 * <p>The bean-validation constraints on {@code name} reference the same limits the service
 * enforces. They exist so an over-long or hostile value is answered with a structured
 * {@code 400} before it reaches business logic; {@link GreetingService} remains the
 * authoritative check for callers that bypass this controller.
 */
@RestController
@Validated
public class GreetingController {

    private static final Logger LOG = LoggerFactory.getLogger(GreetingController.class);

    private final GreetingService service;

    public GreetingController(GreetingService service) {
        this.service = service;
    }

    /**
     * Greets a caller-supplied name.
     *
     * <p>Mapped with {@code @GetMapping} rather than bare {@code @RequestMapping}: the
     * endpoint is side-effect free, so it should not answer {@code POST}, {@code PUT} or
     * {@code DELETE}. That keeps it unreachable from a cross-origin form or fetch without
     * a preflight, and stops a mutating verb from being a no-op that only looks successful.
     *
     * @param name the name to greet; defaults to {@code World}
     * @return the greeting
     */
    @GetMapping(path = "/api/greeting", produces = MediaType.APPLICATION_JSON_VALUE)
    public Greeting greeting(
            @RequestParam(name = "name", defaultValue = "World")
            @Size(min = 1, max = GreetingService.MAX_NAME_LENGTH,
                    message = "name must be between 1 and " + GreetingService.MAX_NAME_LENGTH + " characters")
            @Pattern(regexp = GreetingService.NAME_REGEX, message = "name contains unsupported characters")
            String name) {

        final Greeting greeting = service.greet(name);
        // Lengths only: the name and the message are caller data and do not belong in logs.
        LOG.info("GET /api/greeting -> 200 (name {} characters, response {} characters)",
                name.length(), greeting.content().length());
        return greeting;
    }
}
