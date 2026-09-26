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

/**
 * A greeting message that has been split at its single {@code %s} placeholder so that
 * rendering never passes operator-supplied text to a format-string parser.
 *
 * <p>{@code greeting.message} is supplied through a ConfigMap, i.e. it is deployment
 * configuration rather than a compile-time constant. Handing such a value straight to
 * {@link String#format(String, Object...)} is a format-string injection sink: a template
 * containing width or precision specifiers (for example {@code %99999999s}) makes the
 * formatter allocate a very large buffer, and any additional conversion turns a greeting
 * into a 500. Parsing the template once at start-up and rendering by concatenation removes
 * that sink completely and also makes a bad ConfigMap a fail-fast boot error rather than a
 * per-request failure.
 */
public final class GreetingTemplate {

    /** The only conversion this service accepts: a single, unpositioned string placeholder. */
    public static final String PLACEHOLDER = "%s";

    /**
     * Upper bound on the configured message. A ConfigMap is operator input, but an
     * unbounded message would let one bad configuration turn every response into a
     * multi-megabyte payload.
     */
    public static final int MAX_TEMPLATE_LENGTH = 512;

    private final String prefix;

    private final String suffix;

    private GreetingTemplate(final String prefix, final String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    /**
     * Validates and splits a configured greeting message.
     *
     * @param template the raw {@code greeting.message} value
     * @return the parsed template
     * @throws IllegalArgumentException if the value cannot be used safely
     */
    public static GreetingTemplate parse(final String template) {
        if (template == null) {
            throw new IllegalArgumentException("greeting.message must be set");
        }

        final String value = template.trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException("greeting.message must not be blank");
        }
        if (value.length() > MAX_TEMPLATE_LENGTH) {
            throw new IllegalArgumentException(
                    "greeting.message must not exceed " + MAX_TEMPLATE_LENGTH + " characters");
        }

        final int placeholder = value.indexOf(PLACEHOLDER);
        if (placeholder < 0) {
            throw new IllegalArgumentException(
                    "greeting.message must contain exactly one '" + PLACEHOLDER + "' placeholder");
        }
        if (value.indexOf(PLACEHOLDER, placeholder + PLACEHOLDER.length()) >= 0) {
            throw new IllegalArgumentException(
                    "greeting.message must contain exactly one '" + PLACEHOLDER + "' placeholder");
        }

        // Reject any other conversion specifier: the rest of the message is literal text.
        final String literal = value.substring(0, placeholder)
                + value.substring(placeholder + PLACEHOLDER.length());
        if (literal.indexOf('%') >= 0) {
            throw new IllegalArgumentException(
                    "greeting.message must not contain conversion specifiers other than '"
                            + PLACEHOLDER + "'; '%' is otherwise literal text and must be omitted");
        }

        return new GreetingTemplate(
                value.substring(0, placeholder),
                value.substring(placeholder + PLACEHOLDER.length()));
    }

    /**
     * Renders this template.
     *
     * @param name the already-validated name to interpolate
     * @return the greeting text
     */
    public String render(final String name) {
        return prefix + name + suffix;
    }

    /**
     * @return length in characters of the configured message, placeholder excluded
     */
    public int length() {
        return prefix.length() + suffix.length();
    }

    @Override
    public String toString() {
        return "GreetingTemplate{length=" + length() + '}';
    }
}
