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

import java.time.Instant;

/**
 * Uniform error body for the JSON API.
 *
 * <p>Deliberately free of exception types, messages and stack traces: a client gets a
 * stable machine-readable code plus a safe human sentence, and the operator gets the detail
 * in the server log where it cannot be used to fingerprint the stack.
 *
 * @param code      stable, machine-readable error identifier
 * @param message   human-readable, safe to return to the caller
 * @param path      request path the error relates to
 * @param timestamp when the error was produced, in UTC
 */
public record ApiError(String code, String message, String path, Instant timestamp) {
}
