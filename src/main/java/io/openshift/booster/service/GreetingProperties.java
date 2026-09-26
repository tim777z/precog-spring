/*
 * Copyright 2016-2017 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.openshift.booster.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Externalised greeting configuration, normally supplied by a Kubernetes ConfigMap.
 *
 * <p>The constraints below are validated during context refresh. Without them a ConfigMap
 * that is absent, empty or mistyped booted successfully and only failed on the first
 * request, which reads as an application fault in production and turns a configuration
 * mistake into a stream of 500s.
 */
@Component
@Validated
@ConfigurationProperties("greeting")
public class GreetingProperties {

    /**
     * The greeting message. It must contain exactly one {@code %s} placeholder, which is
     * replaced with the caller's name, and no other percent signs.
     *
     * <p>Defaults come from {@code src/main/resources/application.yml}. Locally that file
     * provides the value; on OpenShift it is supplied by a ConfigMap, whose contents take
     * precedence over the classpath.
     */
    @NotBlank(message = "greeting.message must be set (application.yml or a ConfigMap)")
    @Size(max = GreetingTemplate.MAX_TEMPLATE_LENGTH,
            message = "greeting.message must not exceed " + GreetingTemplate.MAX_TEMPLATE_LENGTH + " characters")
    private String message = null;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
