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
package io.openshift.booster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Verifies the ConfigMap contract without needing a cluster.
 *
 * <p>This is the runnable replacement for the OpenShift integration test that used to sit
 * behind {@code org.arquillian.cube:arquillian-cube-openshift} and
 * {@code io.fabric8:openshift-client:3.1.8}. Those needed a live cluster, so the behaviour
 * they covered was effectively never exercised on a checkout. A test property has the same
 * precedence as a ConfigMap-supplied value at the binding layer, so the wiring it proves is
 * the wiring that runs in the cluster.
 *
 * <p>The value below is the exact message shipped in
 * {@code .openshiftio/resource.configmap.yaml}; changing one without the other breaks the
 * documented demo, so the equality is asserted literally.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "greeting.message=Hello %s from a ConfigMap!")
class ConfigMapOverrideTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("an externally supplied message replaces the packaged default")
    void usesTheExternalisedMessage() {
        final ResponseEntity<String> response = rest.getForEntity("/api/greeting", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"content\":\"Hello World from a ConfigMap!\"");
    }

    @Test
    void interpolatesTheSuppliedName() {
        final ResponseEntity<String> response =
                rest.getForEntity("/api/greeting?name=John", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"content\":\"Hello John from a ConfigMap!\"");
    }
}
