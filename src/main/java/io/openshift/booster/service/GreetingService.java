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

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

/**
 * Builds greeting messages from the ConfigMap-supplied template and a caller-supplied name.
 *
 * <p>All input handling lives here rather than in the controller, so the rules are enforced
 * for every caller (HTTP today, other transports later) and can be unit tested without a
 * servlet container.
 */
@Service
public class GreetingService implements InitializingBean {

    /** Longest accepted name, in Unicode code points. Bounds response size and log volume. */
    public static final int MAX_NAME_LENGTH = 64;

    /**
     * Characters accepted in a name: letters, combining marks, digits, spaces and the small
     * set of punctuation that appears in real names.
     *
     * <p>This is an allow-list, not a deny-list. It rejects angle brackets, quotes,
     * ampersands, percent signs, control characters and newlines, so a name can never carry
     * markup or forge a log record on its way into a response body. Paired with the
     * client-side rendering fix in {@code static/index.html} this closes reflected XSS at
     * both ends rather than relying on encoding alone.
     */
    public static final String NAME_REGEX = "^[\\p{L}\\p{M}\\p{N} .,'_-]*$";

    private static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

    private static final Logger LOG = LoggerFactory.getLogger(GreetingService.class);

    private final GreetingProperties properties;

    private GreetingTemplate template;

    public GreetingService(final GreetingProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolves and validates the configured template once, at start-up.
     *
     * <p>{@code afterPropertiesSet} runs after {@code @ConfigurationProperties} binding, so
     * the ConfigMap value is already in place. Parsing here means a missing or malformed
     * {@code greeting.message} fails the boot with one actionable message instead of
     * turning every single request into a 500.
     */
    @Override
    public void afterPropertiesSet() {
        this.template = GreetingTemplate.parse(properties.getMessage());
        LOG.info("Greeting message template loaded ({} characters of literal text)", template.length());
    }

    /**
     * Renders a greeting.
     *
     * @param rawName caller-supplied name; surrounding whitespace is ignored
     * @return the rendered greeting
     * @throws InvalidGreetingNameException if the name is empty, too long or contains
     *         characters outside {@link #NAME_REGEX}
     */
    public Greeting greet(final String rawName) {
        final String name = validateName(rawName);
        // The name is deliberately not logged: it is caller-supplied personal data.
        LOG.debug("Rendering greeting for a name of {} characters", name.length());
        return new Greeting(template.render(name));
    }

    private String validateName(final String rawName) {
        if (rawName == null) {
            throw new InvalidGreetingNameException("name must not be null");
        }

        final String name = rawName.trim();
        if (name.isEmpty()) {
            throw new InvalidGreetingNameException("name must not be blank");
        }
        // Count code points so an astral character cannot smuggle past the limit by
        // occupying two chars in a UTF-16 string.
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            throw new InvalidGreetingNameException(
                    "name must not exceed " + MAX_NAME_LENGTH + " characters");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new InvalidGreetingNameException("name contains unsupported characters");
        }
        return name;
    }
}
