# argocd-deployment

A sample full-stack Java (Spring Boot) app deployed end-to-end with a GitOps
workflow: **GitHub -> Docker Hub -> Argo CD -> Minikube**.

```
GitHub (source) --build--> Docker image --push--> Docker Hub
        |                                              |
        v                                              v
GitHub (gitops/ manifests) <--commit updates image tag--
        |
        v
   Argo CD (in Minikube) --sync/self-heal--> Kubernetes Deployment
```

## Repository layout

```
argocd-deployment/
├── app/                        # Spring Boot backend + static login UI
│   ├── src/main/java/...       # REST controllers (auth, health/version)
│   ├── src/main/resources/     # application.yml, static/ (login page)
│   ├── Dockerfile              # multi-stage build (Maven -> JRE)
│   └── pom.xml
├── gitops/                     # Manifests Argo CD watches
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml            # optional
├── argocd-application.yaml     # Argo CD Application (applied once, manually)
├── .github/workflows/ci-cd.yml # optional GitHub Actions CI/CD
├── scripts/release.sh|ps1      # manual build->push->update->commit script
└── docs/                       # detailed guides (linked below)
```

## Technology stack

Java 21 · Spring Boot 3 · Maven · HTML/CSS/JS · Docker · Docker Hub ·
Kubernetes · Minikube · Argo CD · GitHub Actions

## Prerequisites

| Tool | Used for | Check |
|---|---|---|
| Docker Desktop | building images, Minikube's driver | `docker --version` |
| Minikube | local Kubernetes cluster | `minikube version` |
| kubectl | talking to the cluster | `kubectl version --client` |
| Git | version control | `git --version` |
| A GitHub account | source + GitOps repo | — |
| A Docker Hub account | image registry | — |
| (Optional) Argo CD CLI | scripted sync/status | `argocd version --client` |
| (Optional) JDK 21 + Maven | building outside Docker | `java -version`, `mvn -v` |

You do **not** need a local JDK/Maven install — the Dockerfile builds the jar
inside a `maven:3.9-eclipse-temurin-21` build stage.

## Quick start

```bash
# 1. Build & smoke-test locally
docker build -t <dockerhub-user>/argocd-deployment:1.0.0 app
docker run -d --name gitops-demo -p 8080:8080 \
  -e DEMO_USERNAME=admin -e DEMO_PASSWORD=admin123 \
  -e APP_VERSION=1.0.0 -e WELCOME_MESSAGE="Welcome!" \
  <dockerhub-user>/argocd-deployment:1.0.0
curl http://localhost:8080/actuator/health
docker rm -f gitops-demo

# 2. Push the image
docker login
docker push <dockerhub-user>/argocd-deployment:1.0.0

# 3. Push this repo to GitHub
git remote add origin https://github.com/<github-user>/argocd-deployment.git
git add -A && git commit -m "Initial commit"
git push -u origin main

# 4. Stand up Minikube + Argo CD (see docs/ for full detail)
minikube start --driver=docker
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd port-forward svc/argocd-server 8081:443 &
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d

# 5. Register the Argo CD Application and let it deploy the app
kubectl apply -f argocd-application.yaml
kubectl -n argocd get applications
kubectl -n gitops-demo get pods,svc

# 6. Open the app
kubectl -n gitops-demo port-forward svc/gitops-demo 8080:80
# browse http://localhost:8080, log in with admin / admin123
```

## Full documentation

| Guide | Covers |
|---|---|
| [docs/MINIKUBE_SETUP.md](docs/MINIKUBE_SETUP.md) | Installing & starting Minikube, addons, troubleshooting |
| [docs/ARGOCD_SETUP.md](docs/ARGOCD_SETUP.md) | Installing Argo CD, exposing the UI, admin password, CLI |
| [docs/DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md) | Full step-by-step: image -> Application -> sync -> proving auto-update & self-heal |
| [docs/CICD_WORKFLOW.md](docs/CICD_WORKFLOW.md) | GitHub Actions pipeline and the manual release script alternative |

## Application details

- **Backend:** Spring Boot 3 / Java 21, Maven build.
- **Auth:** `POST /api/auth/login` — dummy check against credentials sourced
  from the Kubernetes `Secret` (`DEMO_USERNAME`/`DEMO_PASSWORD`, default
  `admin` / `admin123`).
- **Health:** `GET /actuator/health` (+ `/actuator/health/liveness` and
  `/actuator/health/readiness`, wired to the Deployment's probes).
- **Version/config demo:** `GET /api/version` returns `APP_VERSION` and
  `WELCOME_MESSAGE` from the `ConfigMap`, plus the serving pod's hostname —
  handy for proving a GitOps config change (or a rolling image update across
  replicas) took effect.
- **Logging:** a request-logging filter logs method/path/status/duration for
  every request; app startup/auth events are also logged (see
  `RequestLoggingFilter`, `AuthController`).
- **Frontend:** a single static login page (`app/src/main/resources/static/`)
  served directly by Spring Boot — no separate frontend container needed.

## Build instructions

```bash
# via Docker (no local JDK/Maven needed)
docker build -t <dockerhub-user>/argocd-deployment:<version> app

# or locally, if you have JDK 21 + Maven installed
cd app
mvn clean package
java -jar target/gitops-demo.jar
```

## Deployment steps (summary)

See [docs/DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md) for the full walkthrough. In short:

1. Build & push the image to Docker Hub.
2. Push this repo to GitHub (source + `gitops/` manifests together).
3. Install Minikube + Argo CD, expose the Argo CD UI, grab the admin password.
4. `kubectl apply -f argocd-application.yaml` (one-time bootstrap).
5. Argo CD auto-syncs `gitops/` into the `gitops-demo` namespace.
6. Prove the loop: edit a manifest (or run `scripts/release.sh`/`.ps1` for a
   new image), commit, push — Argo CD picks it up and reconciles the cluster
   automatically, self-healing any manual drift.

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `docker build` fails downloading dependencies | No internet access from the build stage — check your network/proxy. |
| Argo CD app stuck `OutOfSync` | Check `argocd app get gitops-demo` / `kubectl -n argocd describe application gitops-demo` for the diff; confirm `gitops/deployment.yaml`'s image tag actually exists on Docker Hub. |
| Argo CD app `Degraded` / pods `CrashLoopBackOff` | `kubectl -n gitops-demo logs deploy/gitops-demo` — usually a bad env var or the image failing to start. |
| Pods `ImagePullBackOff` | Image/tag doesn't exist on Docker Hub yet, or it's private — either push it or make the Docker Hub repo public. |
| Can't reach the Argo CD UI | Confirm the `kubectl port-forward` is still running in its terminal. |
| `git push` to the GitOps repo rejected | Pull/rebase first (`git pull --rebase`) — the CI workflow or another teammate may have pushed a manifest update. |
| Login page loads but login fails | Confirm the `gitops-demo-secret` Secret's `DEMO_USERNAME`/`DEMO_PASSWORD` match what you're typing; check pod env with `kubectl -n gitops-demo exec deploy/gitops-demo -- env`. |
| ConfigMap edit doesn't show up in the app | ConfigMap changes don't restart pods automatically — run `kubectl -n gitops-demo rollout restart deployment gitops-demo`. |

## Cleanup

```bash
# Remove the Argo CD-managed app + its namespace
kubectl delete -f argocd-application.yaml
kubectl delete namespace gitops-demo

# Remove Argo CD itself
kubectl delete namespace argocd

# Tear down the whole local cluster
minikube stop
minikube delete

# Local Docker cleanup
docker rmi <dockerhub-user>/argocd-deployment:1.0.0
```

## Screenshots

See [screenshots/](screenshots/) for captures of: the Spring Boot app running,
the login UI, Docker Hub with the published image, `kubectl get pods,svc` in
Minikube, Argo CD pods running, the Argo CD Application in **Healthy**/**Synced**
state, and a successful redeploy triggered by a Git commit.
