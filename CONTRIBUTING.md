# Contributing to PreCog Spring

Thank you for considering a contribution. This document describes the expectations and the gate every change must pass before it can be merged.

## Prerequisites

| Tool   | Version       | Notes                                              |
| ------ | ------------- | -------------------------------------------------- |
| JDK    | **17 or newer** | Enforced by `maven-enforcer-plugin`                |
| Maven  | **3.8.0 or newer** | Also enforced                                      |

No OpenShift cluster, no `oc` login, and no external services are required. The entire test suite runs in-process on a laptop with no network access after the first dependency download.

## The Gate

Every push and pull request must pass the full verification pipeline:

```bash
mvn -B checkstyle:check   # Lint: defect-focused rules, warnings fail the build
mvn -B test               # Unit and slice tests (8 test classes, ~2s)
mvn -B jacoco:report jacoco:check  # Coverage gate
```

**Coverage thresholds** (from `pom.xml`):
- `io.openshift.booster.service` package: **80%** line coverage (`jacoco.service.coverage`)
- Whole bundle: **70%** line coverage (`jacoco.total.coverage`)

The CI workflow (`.github/workflows/ci.yml`) runs these as three discrete steps so a failure's cause is visible from the job list.

## Code Style

Checkstyle (`config/checkstyle.xml`) encodes rules that catch real defects:
- Unused/duplicate imports
- Missing braces on `if`/`for`/`while`
- `==` / `!=` on `String` (use `.equals()`)
- Missing `default` in `switch`
- Stray `System.out` / `System.err` (use SLF4J)
- Naming conventions (constants, variables, methods, types, packages)

The ruleset is deliberately small. A noisy blocking gate gets bypassed; a focused one stays enabled.

## Testing

- **Unit tests** (no Spring context): `GreetingTemplateTest`, `GreetingServiceTest`, `GreetingPropertiesTest`, `ApiExceptionHandlerTest`, `SecurityHeadersFilterTest`
- **Web slice tests** (`@WebMvcTest`): `GreetingControllerTest`
- **Integration tests** (`@SpringBootTest` with random port): `BoosterApplicationTest`, `ConfigMapOverrideTest`

All tests are in `src/test/java/io/openshift/booster/`. Add a test for every new behaviour or bug fix. The coverage gate will catch gaps.

## Security Expectations

This service is a demo booster. It has **no authentication** and **no authorisation**. Do not expose it to the internet as-is. Production deployments must place an authenticating reverse proxy or authenticated ingress in front of it.

When you change code, preserve these invariants:
- **No stack traces in responses** — `ApiExceptionHandler` maps every exception to a fixed `ApiError` shape
- **No format-string injection** — `GreetingTemplate` parses the ConfigMap message once at start-up; rendering is concatenation only
- **No reflected XSS** — `name` validated against an allow-list; UI uses `textContent`, not `innerHTML`
- **No third-party script** — UI serves its own `app.js`/`app.css`; no CDN origins
- **Strict CSP** — `default-src 'none'` with explicit re-adoption of only `self` sources; no `unsafe-inline`, no `unsafe-eval`
- **Bounded input** — `name` ≤ 64 code points, `greeting.message` ≤ 512 chars; neither is logged
- **Actuator allow-list** — only `health`, `info`, `metrics` exposed; `env`, `configprops`, `beans`, `heapdump`, `threaddump`, `loggers`, `shutdown` excluded

## Dependency Hygiene

- All plugin and tool versions are pinned in `pom.xml` properties (no version ranges)
- `maven-enforcer-plugin` bans duplicate dependency declarations and SNAPSHOTs in release builds
- **OWASP Dependency-Check** runs weekly via CI (`dependency-audit` job) and on demand:
  ```bash
  mvn -B -Psecurity-audit verify
  ```
  Fails on CVSS ≥ 7. Suppressions live in `config/dependency-check-suppressions.xml`; each must carry a justification and an expiry date.
- **Dependabot** opens weekly grouped PRs for Maven and GitHub Actions updates (`.github/dependabot.yml`)
- **Dependency Review** runs on every PR, comparing the diff against the GitHub Advisory Database; high/critical findings block the PR
- A resolved dependency tree is committed as `dependency-tree.txt` so staleness is auditable without re-resolving. Regenerate it after any dependency change:
  ```bash
  mvn dependency:tree -DoutputFile=dependency-tree.txt
  ```

## Commit Hygiene

- Use **Conventional Commits** prefixes: `feat:`, `fix:`, `test:`, `chore:`, `docs:`, `refactor:`, `security:`
- Keep each feature or fix in its own commit (or small PR) that includes the tests pinning the new behaviour
- Avoid bulk commits that mix formatting, refactors, and features — they hide the mineable work

## Pull Request Checklist

Before opening a PR, verify locally:

- [ ] `mvn -B checkstyle:check` passes
- [ ] `mvn -B test` passes
- [ ] `mvn -B jacoco:report jacoco:check` passes (coverage thresholds met)
- [ ] New behaviour has a test; bug fix has a regression test
- [ ] No new `System.out`/`System.err` usage
- [ ] Security invariants preserved (see above)
- [ ] If dependencies changed: `mvn dependency:tree -DoutputFile=dependency-tree.txt` and commit the updated file
- [ ] Commit messages follow Conventional Commits

## Reporting Security Issues

Do not open a public issue for a suspected vulnerability. Email the maintainers directly (see `SECURITY.md` if present, otherwise the repository owner) so the fix can be coordinated before disclosure.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0 (see `LICENSE`).