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
(function () {
    'use strict';

    var form = document.getElementById('greeting-form');
    var nameInput = document.getElementById('name');
    var button = document.getElementById('invoke');
    var output = document.getElementById('greeting-result');

    function show(text) {
        // textContent, never innerHTML. Both the greeting and any error text derive from
        // request or configuration input, so assigning them as markup would hand the
        // caller script execution in this page's origin. The previous implementation used
        // jQuery's .html() over JSON.stringify output, which does not escape < or > and was
        // therefore exploitable through the ?name= parameter.
        output.textContent = text;
    }

    function renderError(payload) {
        if (payload && typeof payload === 'object' && typeof payload.message === 'string') {
            show((payload.code ? payload.code + ': ' : '') + payload.message);
        } else {
            show('The service returned an unexpected error.');
        }
    }

    function handleResult(result) {
        if (result.ok && result.payload && typeof result.payload.content === 'string') {
            show(result.payload.content);
        } else if (result.ok) {
            show('The service returned an unexpected response.');
        } else {
            renderError(result.payload);
        }
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        button.disabled = true;
        show('Calling the service...');

        var name = nameInput.value.trim() || 'World';

        // encodeURIComponent so a name containing &, #, ? or a space cannot alter the
        // query string that is sent.
        fetch('/api/greeting?name=' + encodeURIComponent(name), {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        })
            .then(function (response) {
                return response.json().then(function (payload) {
                    return { ok: response.ok, payload: payload };
                }, function () {
                    return { ok: false, payload: null };
                });
            })
            .then(handleResult)
            .catch(function () {
                show('Could not reach the service.');
            })
            .then(function () {
                button.disabled = false;
            });
    });
})();
