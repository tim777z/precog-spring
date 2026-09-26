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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for {@code GET /api/greeting}, run through MockMvc.
 *
 * <p>{@code @WebMvcTest} starts the MVC slice only: no data source, no cluster, no bound
 * socket. These finish in about a second, so they can gate every change -- which the
 * previous suite could not do.
 */
@WebMvcTest(GreetingController.class)
@Import(GreetingControllerTest.GreetingConfiguration.class)
class GreetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void greetsTheWorldByDefault() throws Exception {
        mockMvc.perform(get("/api/greeting"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.content").value("Hello, World!"));
    }

    @Test
    void greetsTheSuppliedName() throws Exception {
        mockMvc.perform(get("/api/greeting").param("name", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello, John!"));
    }

    @Test
    void ignoresWhitespaceAroundTheSuppliedName() throws Exception {
        mockMvc.perform(get("/api/greeting").param("name", "  John  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello, John!"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "\"><img src=x onerror=alert(1)>",
            "Tom & Jerry",
            "line1%0Aline2"
    })
    @DisplayName("rejects a name carrying markup rather than reflecting it")
    void rejectsHostileNames(final String name) throws Exception {
        mockMvc.perform(get("/api/greeting").param("name", name))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.path").value("/api/greeting"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void rejectsAnEmptyName() throws Exception {
        mockMvc.perform(get("/api/greeting").param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void rejectsAnOverlongName() throws Exception {
        final String tooLong = "a".repeat(GreetingService.MAX_NAME_LENGTH + 1);

        mockMvc.perform(get("/api/greeting").param("name", tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("never echoes the rejected name back in the error body")
    void doesNotEchoTheRejectedName() throws Exception {
        final String payload = "<script>alert(1)</script>";

        mockMvc.perform(get("/api/greeting").param("name", payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("script"))));
    }

    @Test
    @DisplayName("only answers GET")
    void refusesOtherVerbs() throws Exception {
        // The endpoint is side-effect free, so accepting POST/PUT/DELETE made it reachable
        // from a cross-origin form post with no preflight and answered with a misleading 200.
        mockMvc.perform(post("/api/greeting").param("name", "John"))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * Supplies the collaborators a web slice does not scan for. Real objects rather than
     * mocks: what is under test is the HTTP contract, and the validation and template rules
     * are cheap enough to exercise for real.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class GreetingConfiguration {

        @Bean
        GreetingProperties greetingProperties() {
            final GreetingProperties properties = new GreetingProperties();
            properties.setMessage("Hello, %s!");
            return properties;
        }

        @Bean
        GreetingService greetingService(final GreetingProperties properties) throws Exception {
            final GreetingService service = new GreetingService(properties);
            service.afterPropertiesSet();
            return service;
        }
    }
}
