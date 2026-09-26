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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link GreetingProperties} accessor behaviour and its constraints.
 *
 * <p>No Spring context: the constraints are exercised through a plain Jakarta Bean
 * Validation validator, which is exactly what Spring Boot applies during
 * {@code @ConfigurationProperties} binding.
 */
class GreetingPropertiesTest {

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void startsUnsetSoAMissingConfigMapIsDetected() {
        assertThat(new GreetingProperties().getMessage()).isNull();
    }

    @Test
    void roundTripsTheMessage() {
        final GreetingProperties properties = new GreetingProperties();

        properties.setMessage("Bonjour %s from a ConfigMap!");

        assertThat(properties.getMessage()).isEqualTo("Bonjour %s from a ConfigMap!");
    }

    @Test
    void acceptsAMessageWithAPlaceholder() {
        assertThat(validator.validate(propertiesWith("Hello %s from a ConfigMap!"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingOrBlankMessage(final String message) {
        assertThat(validator.validate(propertiesWith(message)))
                .isNotEmpty()
                .allSatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("message"));
    }

    @Test
    void rejectsAnOverlongMessage() {
        final String tooLong = "x".repeat(GreetingTemplate.MAX_TEMPLATE_LENGTH + 1);

        assertThat(validator.validate(propertiesWith(tooLong)))
                .isNotEmpty()
                .allSatisfy(violation ->
                        assertThat(violation.getMessage()).contains("512"));
    }

    @Test
    void acceptsAMessageExactlyAtTheLimit() {
        final String atLimit = "x".repeat(GreetingTemplate.MAX_TEMPLATE_LENGTH);

        assertThat(validator.validate(propertiesWith(atLimit))).isEmpty();
    }

    private static GreetingProperties propertiesWith(final String message) {
        final GreetingProperties properties = new GreetingProperties();
        properties.setMessage(message);
        return properties;
    }
}
