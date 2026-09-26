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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link GreetingService}, built with no Spring context at all.
 *
 * <p>Replaces the previous suite, which could only run against a started server and, in the
 * case of the OpenShift integration test, against a live cluster.
 */
class GreetingServiceTest {

    private static final String MESSAGE = "Hello, %s!";

    /** U+10400 DESERET CAPITAL LETTER LONG I: one code point, two UTF-16 chars, and a letter. */
    private static final String ASTRAL_LETTER = "\uD801\uDC00";

    private GreetingService service;

    @BeforeEach
    void setUp() throws Exception {
        service = started(MESSAGE);
    }

    private static GreetingService started(final String message) throws Exception {
        final GreetingProperties properties = new GreetingProperties();
        properties.setMessage(message);
        final GreetingService service = new GreetingService(properties);
        service.afterPropertiesSet();
        return service;
    }

    @Test
    void rendersTheConfiguredMessage() {
        assertThat(service.greet("World").content()).isEqualTo("Hello, World!");
    }

    @Test
    void ignoresWhitespaceAroundTheName() {
        assertThat(service.greet("  World \n").content()).isEqualTo("Hello, World!");
    }

    @Test
    void acceptsNamesWithSpacesAndPunctuation() {
        assertThat(service.greet("Ada O'Brien-Smith Jr.").content())
                .isEqualTo("Hello, Ada O'Brien-Smith Jr.!");
    }

    @Test
    void acceptsNonLatinScripts() {
        assertThat(service.greet("\u5C71\u7530\u592A\u90CE").content())
                .isEqualTo("Hello, \u5C71\u7530\u592A\u90CE!");
    }

    @Test
    void acceptsANameExactlyAtTheLimit() {
        final String atLimit = "a".repeat(GreetingService.MAX_NAME_LENGTH);

        assertThat(service.greet(atLimit).content()).isEqualTo("Hello, " + atLimit + "!");
    }

    @Test
    void countsAstralCharactersAsOneRatherThanTwo() {
        final String atLimit = ASTRAL_LETTER.repeat(GreetingService.MAX_NAME_LENGTH);

        assertThat(atLimit.length()).isEqualTo(GreetingService.MAX_NAME_LENGTH * 2);
        assertThat(service.greet(atLimit).content()).startsWith("Hello, " + ASTRAL_LETTER);
    }

    @Test
    void rejectsAnOverlongCountOfAstralCharacters() {
        final String tooLong = ASTRAL_LETTER.repeat(GreetingService.MAX_NAME_LENGTH + 1);

        assertThatExceptionOfType(InvalidGreetingNameException.class)
                .isThrownBy(() -> service.greet(tooLong))
                .withMessageContaining("must not exceed");
    }

    @Nested
    @DisplayName("rejects an unusable name")
    class Rejects {

        @Test
        void rejectsNull() {
            assertThatExceptionOfType(InvalidGreetingNameException.class)
                    .isThrownBy(() -> service.greet(null))
                    .withMessageContaining("must not be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        void rejectsBlank(final String name) {
            assertThatExceptionOfType(InvalidGreetingNameException.class)
                    .isThrownBy(() -> service.greet(name))
                    .withMessageContaining("must not be blank");
        }

        @Test
        void rejectsAnOverlongName() {
            final String tooLong = "a".repeat(GreetingService.MAX_NAME_LENGTH + 1);

            assertThatExceptionOfType(InvalidGreetingNameException.class)
                    .isThrownBy(() -> service.greet(tooLong))
                    .withMessageContaining("must not exceed");
        }

        /**
         * The allow-list is the first of two independent barriers against markup and log
         * forging reaching a response body; the second is the client writing the value with
         * {@code textContent} instead of {@code innerHTML}.
         */
        @ParameterizedTest
        @ValueSource(strings = {
                "<script>alert(1)</script>",
                "World\"",
                "Tom & Jerry",
                "50% off",
                "line1\nline2",
                "tab\there",
                "{\"json\":1}",
                "../../etc/passwd"
        })
        void rejectsCharactersOutsideTheAllowList(final String name) {
            assertThatExceptionOfType(InvalidGreetingNameException.class)
                    .isThrownBy(() -> service.greet(name))
                    .withMessageContaining("unsupported characters");
        }
    }

    @Nested
    @DisplayName("refuses to start on an unusable message")
    class FailFast {

        static Stream<Arguments> badMessages() {
            return Stream.of(
                    Arguments.of("", "must not be blank"),
                    Arguments.of("   ", "must not be blank"),
                    Arguments.of("Hello there!", "exactly one"),
                    Arguments.of("%s and %s", "exactly one"),
                    Arguments.of("Hello %d!", "exactly one"),
                    Arguments.of("%s 50% off", "conversion specifiers"));
        }

        @ParameterizedTest
        @MethodSource("badMessages")
        void refusesToStart(final String message, final String expectedReason) {
            final GreetingService failing = new GreetingService(propertiesWith(message));

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(failing::afterPropertiesSet)
                    .withMessageContaining(expectedReason);
        }

        @Test
        void refusesToStartWithoutAMessage() {
            final GreetingService failing = new GreetingService(propertiesWith(null));

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(failing::afterPropertiesSet)
                    .withMessageContaining("must be set");
        }

        private GreetingProperties propertiesWith(final String message) {
            final GreetingProperties properties = new GreetingProperties();
            properties.setMessage(message);
            return properties;
        }
    }
}
