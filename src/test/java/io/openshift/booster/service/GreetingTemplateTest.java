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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link GreetingTemplate}.
 *
 * <p>No Spring context, no servlet container: this is the class that decides whether
 * operator-supplied configuration is safe to use, so it is worth testing exhaustively and
 * cheaply.
 */
class GreetingTemplateTest {

    @Nested
    @DisplayName("accepts a well-formed message")
    class Accepts {

        @Test
        void interpolatesTheName() {
            assertThat(GreetingTemplate.parse("Hello, %s!").render("World"))
                    .isEqualTo("Hello, World!");
        }

        @Test
        void handlesAPlaceholderAtEitherEnd() {
            assertThat(GreetingTemplate.parse("%s is here").render("Ada")).isEqualTo("Ada is here");
            assertThat(GreetingTemplate.parse("Hello %s").render("Ada")).isEqualTo("Hello Ada");
        }

        @Test
        void ignoresSurroundingWhitespaceInTheConfiguration() {
            assertThat(GreetingTemplate.parse("  Hello, %s!  ").render("World"))
                    .isEqualTo("Hello, World!");
        }

        @Test
        void reportsTheLiteralLength() {
            // "Hello, " + "!" -- the placeholder itself is not counted.
            assertThat(GreetingTemplate.parse("Hello, %s!").length()).isEqualTo(8);
        }

        @Test
        void doesNotPrintTheMessageInToString() {
            assertThat(GreetingTemplate.parse("Hello, %s!").toString()).doesNotContain("Hello");
        }
    }

    @Nested
    @DisplayName("rejects an unusable message")
    class Rejects {

        @Test
        void rejectsNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> GreetingTemplate.parse(null))
                    .withMessageContaining("greeting.message must be set");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t\n"})
        void rejectsBlank(final String template) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> GreetingTemplate.parse(template))
                    .withMessageContaining("must not be blank");
        }

        @Test
        void rejectsMoreThanOnePlaceholder() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> GreetingTemplate.parse("%s and %s"))
                    .withMessageContaining("exactly one");
        }

        /**
         * A template with no placeholder at all is a configuration mistake: the name would
         * silently never appear in the response.
         */
        @ParameterizedTest
        @ValueSource(strings = {"Hello there!", "%d", "Hello %d!", "%n", "%1$s", "%99999999s"})
        void rejectsAMessageWithoutAPlaceholder(final String template) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> GreetingTemplate.parse(template))
                    .withMessageContaining("exactly one");
        }

        /**
         * A second conversion specifier is the format-string injection sink: width and
         * precision directives such as {@code %99999999s} make the formatter allocate a very
         * large buffer from a value that only has to reach a ConfigMap.
         */
        @ParameterizedTest
        @ValueSource(strings = {"100%% sure, %s", "%s %d", "%%s", "%s 50% off"})
        void rejectsOtherConversionSpecifiers(final String template) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> GreetingTemplate.parse(template))
                    .withMessageContaining("conversion specifiers");
        }

        @Test
        void rejectsAnOverlongMessage() {
            final String tooLong = "%s" + "x".repeat(GreetingTemplate.MAX_TEMPLATE_LENGTH);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> GreetingTemplate.parse(tooLong))
                    .withMessageContaining("must not exceed");
        }

        @Test
        void acceptsAMessageExactlyAtTheLimit() {
            final String atLimit = "x".repeat(GreetingTemplate.MAX_TEMPLATE_LENGTH - 2) + "%s";

            assertThat(GreetingTemplate.parse(atLimit).render("ok")).endsWith("ok");
        }
    }

    @Nested
    @DisplayName("never lets the message act as a format string")
    class NoFormatStringInjection {

        @Test
        void rendersTheRemainderOfTheMessageLiterally() {
            assertThat(GreetingTemplate.parse("Hello, %s!").render("%s"))
                    .isEqualTo("Hello, %s!");
        }

        @Test
        void doesNotEvaluateTheName() {
            assertThat(GreetingTemplate.parse("%s").render("%1$s")).isEqualTo("%1$s");
        }

        @Test
        void rejectsAMessageThatStringFormatWouldOtherwiseHaveAccepted() {
            // String.format("Hello %d!", "World") throws at request time. Refusing the
            // configuration at start-up turns a runtime 500 into a boot failure.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> GreetingTemplate.parse("Hello %d!"));
        }
    }
}
