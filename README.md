# PreCog Spring — Spring Boot ConfigMap Booster

A small Spring Boot service that returns a greeting whose wording is **not** compiled into the
application. The message lives in a Kubernetes ConfigMap, so it can be changed on a running
deployment without rebuilding or redeploying the image. The project exists to demonstrate
externalised configuration on OpenShift: a ConfigMap is mounted as a file, Spring Boot reads
`application.yml` from it, and `@ConfigurationProperties` binds the value into the service.

The service also serves a small browser UI at `/` that calls the same endpoint.

---

## Contents

- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Architecture](#architecture)
- [API reference](#api-reference)
- [Configuration and environment variables](#configuration-and-environment-variables)
- [Health, metrics and logging](#health-metrics-and-logging)
- [Testing](#testing)
- [Static analysis and the coverage gate](#static-analysis-and-the-coverage-gate)
- [Running in a container](#running-in-a-container)
- [Running on OpenShift](#running-on-openshift)
- [Security notes](#security-notes)
- [Migration from the 1.x booster layout](#migration-from-the-1x-booster-layout)

---

## Prerequisites

| Tool   | Version                     | Notes                                                        |
| ------ | --------------------------- | ------------------------------------------------------------ |
| JDK    | **17 or newer**             | Enforced by `maven-enforcer-plugin`; the build fails early.  |
| Maven  | **3.8.0 or newer**          | Also enforced. `mvn -B verify` is the whole gate.            |

No JDK 8, no OpenShift cluster, no `oc` login, and no external services are needed for the
build, the tests, or running the service locally. The whole test suite runs in a couple of
seconds on a laptop with no network access after the first dependency download.

## Quick start

```bash
# Build, lint, test, and enforce the coverage gate.
mvn -B verify

# Run on http://localhost:8080
mvn spring-boot:run

# Or build a jar and run it.
mvn -B package -DskipTests
java -jar target/precog-1.0.0.jar
```

Then:

```bash
curl http://localhost:8080/api/greeting
# {"content":"Hello, World!"}

curl 'http://localhost:8080/api/greeting?name=Ada'
# {"content":"Hello, Ada!"}

curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Open <http://localhost:8080/> for the UI.

## Architecture

Four small types, in `io.openshift.booster.service`, with a strict one-way dependency:

```
  ConfigMap / application.yml / GREETING_MESSAGE
                  |
                  v
        GreetingProperties          @ConfigurationProperties("greeting") + bean validation
                  |                holds greeting.message as a plain validated string
                  v
        GreetingService             builds a GreetingTemplate once, in afterPropertiesSet()
                  |
                  v
        GreetingTemplate            prefix + %s + suffix, parsed and validated at start-up
                  |
                  v
        GreetingController          GET /api/greeting, validates the `name` parameter
                  |
                  v
        Greeting                    immutable record, serialised as {"content": "..."}
```

- **`GreetingProperties`** is the only thing that knows about configuration. Its constraints
  are checked while the context starts, so a missing or malformed `greeting.message` fails
  the boot with one actionable message rather than turning every subsequent request into a
  500.
- **`GreetingService`** owns all input handling. It parses the configured message once and
  rejects a name that is blank, longer than 64 characters, or contains anything outside a
  small allow-list. Because the rules live here rather than in the controller, they hold for
  every caller and are unit-testable without a servlet container.
- **`GreetingTemplate`** splits the message at its single `%s` placeholder once, at start-up.
  Rendering is then plain concatenation. See [Security notes](#security-notes) for why the
  message is never handed to `String.format`.
- **`GreetingController`** is a thin HTTP adapter. It adds bean validation on the query
  parameter so a hostile value is answered with a structured `400` before it reaches the
  service, and it logs one line per request.

`ApiExceptionHandler` (`@RestControllerAdvice`) turns every exception into one JSON shape,
and `SecurityHeadersFilter` adds the response headers described under
[Security notes](#security-notes).

## API reference

### `GET /api/greeting`

| Parameter | Type   | Required | Default  | Notes                                                    |
| --------- | ------ | -------- | -------- | -------------------------------------------------------- |
| `name`    | string | no       | `World`  | 1–64 characters, letters/marks/digits/spaces and `. , ' - _` |

Responses:

| Status | Body                                              | Cause                                        |
| ------ | ------------------------------------------------- | -------------------------------------------- |
| `200`  | `{"content":"Hello, World!"}`                     | success                                      |
| `400`  | `{"code":"invalid_name","message":…,"path":…,"timestamp":…}` | name empty, too long, or contains a character outside the allow-list |
| `405`  | `{"code":"request_rejected","message":…,…}`      | any verb other than `GET`                    |
| `500`  | `{"code":"server_error","message":"The request could not be completed.","path":…,"timestamp":…}` | an unexpected fault; the detail is in the log, never in the response |

The error body never contains an exception type, an exception message, or a stack trace.

### `GET /`

A dependency-free single-page UI. It loads `app.css` and `app.js` from the same origin and
renders the result with `textContent`.

## Configuration and environment variables

`greeting.message` is the only application property. Spring Boot's relaxed binding gives it
three equivalent names, in increasing precedence:

| Source                     | How                                                              |
| -------------------------- | ---------------------------------------------------------------- |
| `src/main/resources/application.yml` | the packaged default, `Hello, %s!`                    |
| `GREETING_MESSAGE`         | environment variable, including from `docker compose` / Kubernetes |
| ConfigMap                  | `application.yml` mounted at `/deployments/config`, selected by `SPRING_BOOT_CONFIG_PATH` |

Rules for the value, all enforced **at start-up**:

- exactly one `%s` placeholder, which is replaced with the caller's name;
- no other `%` anywhere — a literal percent sign must be omitted;
- at most 512 characters.

A value that breaks a rule stops the application from starting, with the reason in the log.
This is deliberate: the previous behaviour booted successfully and then returned a 500 for
every request, which is indistinguishable from an application fault and gets paged on.

See [`.env.example`](.env.example) for the environment-variable form.

## Health, metrics and logging

`spring-boot-starter-actuator` is on the classpath and the endpoints are enabled explicitly:

| Endpoint                        | Purpose                                  |
| ------------------------------- | ---------------------------------------- |
| `/actuator/health`              | aggregate status only — no component detail |
| `/actuator/health/liveness`     | Kubernetes liveness probe                |
| `/actuator/health/readiness`    | Kubernetes readiness probe               |
| `/actuator/info`                | build information                        |
| `/actuator/metrics`             | Micrometer metric names and values       |

The exposure list is an explicit allow-list. `env`, `configprops`, `beans`, `heapdump`,
`threaddump`, `loggers` and `shutdown` are **not** exposed — `env` and `configprops` in
particular render configuration values, which is the usual way an actuator endpoint leaks a
secret. `management.info.env.enabled` is also set to `false` so that stays true if the
exposure list is ever widened.

Logging is structured, single-line and UTC, via `src/main/resources/logback-spring.xml`:

```
2026-01-01T00:00:00.000Z INFO  [precog] [] io.openshift.booster.service.GreetingController - GET /api/greeting -> 200 (name 5 characters, response 13 characters)
```

Set the level with the standard property, for example `logging.level.io.openshift.booster=DEBUG`.

## Testing

```bash
mvn -B test
```

Every test runs in-process. There is no cluster, no container, and no network dependency.

| Test                             | Scope                                                        |
| -------------------------------- | ------------------------------------------------------------ |
| `GreetingTemplateTest`           | message parsing, and every rejected form                     |
| `GreetingServiceTest`            | name validation, whitespace, code-point counting             |
| `GreetingPropertiesTest`         | accessor behaviour and the binding constraints               |
| `ApiExceptionHandlerTest`        | status mapping, and that no handler leaks internal detail    |
| `SecurityHeadersFilterTest`      | every header, and that the policy stays strict               |
| `GreetingControllerTest`         | the HTTP contract through MockMvc, no full context           |
| `BoosterApplicationTest`         | the shipped configuration over a real HTTP port              |
| `ConfigMapOverrideTest`          | an externally supplied message replacing the packaged default |

The last two replace the two tests that used to make a fresh checkout untestable:
`BoosterApplicationTest` depended on the abandoned `com.jayway.restassured` library and on a
`${local.server.port}` placeholder only the retired booster parent POM supplied, and
`OpenShiftIT` needed a live OpenShift cluster plus `arquillian-cube-openshift` and
`io.fabric8:openshift-client:3.1.8`. `ConfigMapOverrideTest` keeps the behaviour that
integration test was there to prove, without any of that.

## Static analysis and the coverage gate

`mvn -B verify` runs, in order:

1. `maven-enforcer-plugin` — JDK 17+, Maven 3.8+, no duplicate dependency declarations, no
   SNAPSHOTs in a release build.
2. `maven-checkstyle-plugin` — `config/checkstyle.xml`, warnings fail the build. The ruleset
   is deliberately small and defect-focused (unused and duplicate imports, missing braces,
   `==` on strings, missing `default` in `switch`, stray `System.out`) rather than a full
   style wish-list; a noisy blocking gate gets bypassed.
3. Tests.
4. `jacoco-maven-plugin` — reports coverage and fails the build below 80% line coverage for
   `io.openshift.booster.service` or 70% for the bundle. The HTML report is written to
   `target/site/jacoco/index.html`.

CVE scanning is a separate profile because it is slow and network-dependent, so it must not
gate a pull request:

```bash
mvn -B -Psecurity-audit verify    # writes target/dependency-check-report.html
```

CI runs that on a weekly schedule. Suppressions live in
`config/dependency-check-suppressions.xml`; each one must carry a justification and an
expiry date, and that file is intentionally empty today.

## Running in a container

```bash
cp .env.example .env      # then edit
docker compose up --build
```

The image is built in two stages so the Maven toolchain never reaches the runtime layer. The
runtime container runs as a fixed non-root uid, with a read-only root filesystem, all Linux
capabilities dropped and `no-new-privileges` set. `docker-compose.yml` binds to `127.0.0.1`
by default; set `BIND_ADDRESS=0.0.0.0` only behind an authenticating proxy.

## Running on OpenShift

`src/main/fabric8/deployment.yml` is an OpenShift DeploymentConfig fragment that mounts the
`app-config` ConfigMap at `/deployments/config` and points `SPRING_BOOT_CONFIG_PATH` at it.

`.openshiftio/resource.configmap.yaml` is the ConfigMap manifest:

```bash
oc apply -f .openshiftio/resource.configmap.yaml
oc rollout restart deployment/spring-boot-configmap
```

To change the message on a running deployment:

```bash
oc edit configmap/app-config     # greeting.message: "Bonjour %s from a ConfigMap!"
oc rollout restart deployment/spring-boot-configmap
```

Point the readiness and liveness probes at `/actuator/health/readiness` and
`/actuator/health/liveness`.

> There is deliberately **no** `application.yml` at the repository root. Spring Boot's default
> configuration search path includes `optional:file:./`, so a root-level file would silently
> override the packaged `src/main/resources/application.yml` for anyone who runs the
> application from the working copy — including `mvn spring-boot:run`, the built jar, and
> Surefire. The ConfigMap's own `application.yml` lives inside the manifest above, where it
> is data rather than ambient configuration.


## Security notes

What this service does about the attacks it is plausibly exposed to:

- **No end-user authentication.** The service is a read-only greeting endpoint behind a
  ConfigMap. Do not expose it to the internet as-is: put an authenticating reverse proxy or
  an authenticated ingress in front of it, or bind it to a private port. The actuator
  endpoints are unauthenticated for the same reason they are useful — a liveness probe has
  nowhere to put a credential — which is why only `health`, `info` and `metrics` are
  exposed and health details are `never`.
- **No stack traces in responses.** `server.error.include-stacktrace=never`, plus an advice
  that maps every exception to a fixed JSON body. A stack trace names framework versions,
  internal class names and build paths, which is free reconnaissance.
- **No format-string injection.** `greeting.message` arrives from a ConfigMap, so it is
  deployment input, not a constant. `String.format` on such a value is a known sink:
  `%99999999s` makes the formatter allocate a very large buffer. `GreetingTemplate` parses the
  message once at start-up, rejects everything that is not a single `%s`, and renders by
  concatenation. There is no format parser left in the request path.
- **No reflected XSS.** `name` is validated against an allow-list that excludes `<`, `>`, `"`,
  `&`, `%` and control characters, so it cannot carry markup, and the UI writes the response
  with `textContent` rather than `innerHTML`. The previous UI used jQuery's `.html()` over
  `JSON.stringify` output — which does not escape `<` or `>` — so `?name=<img src=x
  onerror=…>` executed script in the page's origin.
- **No third-party script.** The UI previously loaded jQuery 1.12.4 and Bootstrap from
  `maxcdn.bootstrapcdn.com`: a dead host, and a jQuery line with CVE-2019-11358,
  CVE-2020-11022, CVE-2020-11023 and CVE-2015-9251. Everything is now served from the
  application, so there is no CDN origin to trust and no unpinned third-party code executing
  in the app's origin.
- **A strict Content-Security-Policy**, with no `unsafe-inline`, no `unsafe-eval` and no
  wildcards. The page carries no inline script or style precisely so the policy does not have
  to be weakened to accommodate it.
- **Bounded input.** `name` is capped at 64 characters counted in code points (so an astral
  character cannot slip past by occupying two UTF-16 units) and `greeting.message` at 512.
  Neither is logged: both are caller or operator data.
- **Method restriction.** `/api/greeting` answers `GET` only. Accepting every verb made it
  reachable from a cross-origin form post with no preflight, and returned a misleading 200.
- **Least-privilege CI.** `permissions: contents: read` at the workflow level; no job reads a
  secret. The retired `Jenkinsfile` loaded a third-party shared library from
  `fabric8io/fabric8-pipeline-library@master` — unpinned remote code executing inside the
  build, which is a supply-chain compromise waiting to happen.

## Migration from the 1.x booster layout

The 1.x layout was pinned to `io.openshift.booster:spring-boot-booster-parent:1.5.12-3-rhoar`
(Spring Boot 1.5 / Spring Framework 4.3) while the README and CI both declared Java 17. That
combination cannot build or run: Spring 4.3 predates the class-file and CGLIB changes that
Java 17 requires, and Spring Boot 1.5 has been end-of-life since 2018. A fresh clone could
not produce a working build.

What changed, and why:

| Change                     | Reason                                                                                     |
| -------------------------- | ------------------------------------------------------------------------------------------ |
| Spring Boot 1.5 → 3.5, `spring-boot-starter-parent` | Current, supported line; the old parent no longer exists.          |
| `javax.*` → `jakarta.*`    | Required by Boot 3.                                                                            |
| JUnit 4 → JUnit 5          | Current test stack; `spring-boot-starter-test` provides it.                                   |
| `com.jayway.restassured` → `TestRestTemplate` / `MockMvc` | The `jayway` group is abandoned and unmaintained.                     |
| Removed `arquillian-cube-openshift`, `arquillian-junit-standalone`, `io.fabric8:openshift-client:3.1.8` | Both projects are end-of-life, and their ITs need a live cluster. |
| Removed the `Jenkinsfile` and `.openshiftio/application.yaml` | The pipeline needed the retired parent's `openshift-it` profile and an unpinned third-party shared library; the template referenced a shut-down service and a JDK 8 base image. The ConfigMap manifest is kept. |
| Deleted `src/main/resources/application-local.yml` | Its value now lives in the packaged `application.yml`. Two files setting the same property is a drift hazard, and the `local` profile existed only so `mvn spring-boot:run` had a value. |
| Deleted the root `application.yml` and `src/test/resources/application.yml` | Both sat on Spring Boot's default configuration search path, where they silently overrode the packaged configuration. See [Running on OpenShift](#running-on-openshift). |
| Deleted `src/licenses/` | Generated by a license plugin the retired parent configured, so nothing regenerates it. It listed `arquillian`, `com.jayway.restassured` and `openshift-client` — dependencies this project no longer has — which is a worse state for a compliance review than an absent file. Reintroduce a license plugin (or `mvn license:aggregate-add-third-party`) alongside the next dependency change. |


## License

Apache License 2.0 — see [LICENSE](LICENSE).
