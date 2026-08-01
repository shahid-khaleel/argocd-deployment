# app/

The Spring Boot application deployed by this repo's GitOps pipeline. It's
intentionally small — a login form and a couple of demo endpoints — because
its job is to be a realistic *payload* for the pipeline, not the point of the
project. See the root [README.md](../README.md) for the end-to-end GitOps
flow this app is deployed through.

## What it does

- **`POST /api/auth/login`** — dummy authentication. Compares the submitted
  username/password against `DEMO_USERNAME`/`DEMO_PASSWORD`, which are
  injected as environment variables sourced from the Kubernetes `Secret`
  (`gitops/secret.yaml`) via `application.yml`'s `app.auth.username` /
  `app.auth.password` properties (defaulting to `admin` / `admin123` if unset).
  Returns `200` with a `LoginResponse` on success, `401` on failure. See
  [`AuthController`](src/main/java/com/shahid/gitopsdemo/controller/AuthController.java).
- **`GET /api/version`** — returns `version`, `welcomeMessage` (both sourced
  from the `ConfigMap`, `gitops/configmap.yaml`, via `app.version` /
  `app.welcome-message`), and the serving pod's `hostname`. Exists specifically
  to make GitOps changes *visible*: bump `APP_VERSION` in the ConfigMap, or
  roll out a new image, and this endpoint's response changes — including
  which pod answered, useful for watching a rolling update spread across
  replicas. See [`InfoController`](src/main/java/com/shahid/gitopsdemo/controller/InfoController.java).
- **`GET /actuator/health`** (+ `/actuator/health/liveness`,
  `/actuator/health/readiness`) — Spring Boot Actuator health/probe endpoints,
  wired directly to the Deployment's `startupProbe`/`readinessProbe`/`livenessProbe`
  in [`gitops/deployment.yaml`](../gitops/deployment.yaml).
- **Static login UI** (`src/main/resources/static/`) — a single HTML/CSS/JS
  page served directly by Spring Boot (no separate frontend container/build
  step). Calls `/api/auth/login` and `/api/version` from the browser.
- **Request logging** — [`RequestLoggingFilter`](src/main/java/com/shahid/gitopsdemo/RequestLoggingFilter.java)
  logs method/path/status/duration for every request; login attempts are also
  logged explicitly in `AuthController`.

## Configuration

All runtime config comes from environment variables (see
[`application.yml`](src/main/resources/application.yml)), which in the
cluster are populated from the `ConfigMap`/`Secret` in `gitops/` — the app
itself has no knowledge of Kubernetes:

| Env var | Default | Source in-cluster |
|---|---|---|
| `APP_VERSION` | `1.0.0` | `gitops/configmap.yaml` |
| `WELCOME_MESSAGE` | `Welcome to the GitOps demo app!` | `gitops/configmap.yaml` |
| `DEMO_USERNAME` | `admin` | `gitops/secret.yaml` |
| `DEMO_PASSWORD` | `admin123` | `gitops/secret.yaml` |

## Running it locally

```bash
# via Docker (no local JDK/Maven needed) — from the repo root
docker build -t argocd-deployment:local app
docker run -d --name gitops-demo -p 8080:8080 \
  -e DEMO_USERNAME=admin -e DEMO_PASSWORD=admin123 \
  -e APP_VERSION=local -e WELCOME_MESSAGE="Local run" \
  argocd-deployment:local
curl http://localhost:8080/actuator/health
docker rm -f gitops-demo

# or natively, with JDK 21 + Maven installed
cd app
mvn clean package
java -jar target/gitops-demo.jar
```

Then open http://localhost:8080 and log in with `admin` / `admin123` (or
whatever you set `DEMO_USERNAME`/`DEMO_PASSWORD` to).

## Build & test

- `mvn -B clean package` — compiles, runs tests, and packages
  `target/gitops-demo.jar`. This is exactly what CI runs in
  [`../.github/workflows/ci-cd.yml`](../.github/workflows/ci-cd.yml) before
  building the image.
- Test coverage today is a single Spring context-load smoke test
  ([`GitopsDemoApplicationTests`](src/test/java/com/shahid/gitopsdemo/GitopsDemoApplicationTests.java)) —
  there's no controller-level test for `/api/auth/login` or `/api/version`
  yet (tracked in the root [README's Known Issues](../README.md#known-issues--recommendations)).
- The [`Dockerfile`](Dockerfile) is a two-stage build
  (`maven:3.9-eclipse-temurin-21` -> `eclipse-temurin:21-jre-alpine`) that
  runs as a non-root `spring` user and skips tests at the image-build stage
  (`-DskipTests`) since CI already ran them in the preceding Maven step.

## Stack

Java 21 · Spring Boot 3.3 (`spring-boot-starter-web`, `-actuator`,
`-validation`) · Maven · JUnit 5 (`spring-boot-starter-test`) · plain
HTML/CSS/JS for the frontend.
