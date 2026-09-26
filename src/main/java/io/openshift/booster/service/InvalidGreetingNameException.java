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
 * Raised when the caller-supplied greeting name is not acceptable.
 *
 * <p>Kept as its own type so the web layer can map it to {@code 400 Bad Request} without
 * the risk of a blanket {@code IllegalArgumentException} handler masking genuine server
 * faults as client errors.
 */
public class InvalidGreetingNameException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidGreetingNameException(final String message) {
        super(message);
    }
}
