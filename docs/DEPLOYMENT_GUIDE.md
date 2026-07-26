# Step-by-Step Deployment Guide

End-to-end flow: build the app -> push the image to Docker Hub -> register the
Argo CD Application -> Argo CD deploys it into Minikube -> prove that a Git
change auto-syncs.

## 0. Prerequisites

- Completed [MINIKUBE_SETUP.md](MINIKUBE_SETUP.md) and [ARGOCD_SETUP.md](ARGOCD_SETUP.md).
- A Docker Hub account and `docker login` already run locally.
- This repository pushed to GitHub (see the main [README.md](../README.md) for repo creation steps).

## 1. Build and push the application image

From the repo root:

```bash
docker build -t <dockerhub-user>/argocd-deployment:1.0.0 app
docker login
docker push <dockerhub-user>/argocd-deployment:1.0.0
```

Or use the helper script, which also updates the manifest for you (skip to step 4 if you use this):

```bash
./scripts/release.sh 1.0.0 <dockerhub-user>       # bash
./scripts/release.ps1 -Version 1.0.0 -DockerHubUser <dockerhub-user>   # PowerShell
```

## 2. Confirm the manifest points at your image

Edit [`gitops/deployment.yaml`](../gitops/deployment.yaml) so `spec.template.spec.containers[0].image`
matches exactly what you pushed, e.g. `shahid9741/argocd-deployment:1.0.0`.

## 3. Point the Argo CD Application at your GitHub repo

Edit [`argocd-application.yaml`](../argocd-application.yaml) `spec.source.repoURL` to your fork/repo URL
if it differs from the default. Commit and push everything:

```bash
git add -A
git commit -m "Initial GitOps demo commit"
git push -u origin main
```

## 4. Register the Application with Argo CD

This is a one-time bootstrap step — apply it directly (it lives outside the
`gitops/` path that Argo CD watches, so Argo CD doesn't try to manage its own
Application object):

```bash
kubectl apply -f argocd-application.yaml
```

## 5. Watch Argo CD deploy it

```bash
kubectl -n argocd get applications
argocd app get gitops-demo          # if using the CLI
argocd app sync gitops-demo         # force an immediate sync (optional; automated sync also runs on its own)
```

In the UI (https://localhost:8081), the `gitops-demo` app tile should turn
**Healthy** and **Synced** once the Deployment's pods pass their readiness probes.

## 6. Verify the running app

```bash
kubectl -n gitops-demo get pods,svc,deploy,configmap,secret
kubectl -n gitops-demo port-forward svc/gitops-demo 8080:80
```

Open http://localhost:8080 and log in with `admin` / `admin123` (from the Secret).
Check the version banner shows `1.0.0` (from the ConfigMap).

## 7. Demonstrate the GitOps update flow

Two independent ways to prove "Git commit -> auto deploy":

**A. Config-only change (no new image needed)** — bump the welcome message or
version label to prove ConfigMap changes flow through:

```bash
# edit gitops/configmap.yaml -> change WELCOME_MESSAGE or APP_VERSION
git add gitops/configmap.yaml
git commit -m "Update welcome message"
git push
```

Argo CD detects the diff (default poll: every 3 minutes, or instantly via a
GitHub webhook — see below), re-applies the ConfigMap, and because
`selfHeal`/rolling pods pick up env changes on restart, watch:

```bash
kubectl -n argocd get applications gitops-demo -w
kubectl -n gitops-demo rollout restart deployment gitops-demo   # ConfigMap changes need a pod restart to take effect
```

**B. New image version** — bump the app version, rebuild, push a new tag, and
let the manifest update trigger a real rollout:

```bash
./scripts/release.sh 1.1.0 <dockerhub-user>
```

This pushes `…:1.1.0`, edits `gitops/deployment.yaml`'s image tag, commits, and
pushes. Argo CD picks up the new image tag and performs a rolling update —
watch it happen with:

```bash
kubectl -n gitops-demo get pods -w
```

## 8. (Optional) Get instant sync instead of polling

Add a GitHub webhook (Settings -> Webhooks) pointing at
`https://<your-argocd-server>/api/webhook` for `push` events, so Argo CD reacts
immediately instead of waiting for its 3-minute poll. Not required for this
demo — `minikube tunnel`/port-forward endpoints aren't publicly reachable
anyway, so polling (or `argocd app sync`) is the practical option for a local
cluster.

## Self-healing demo (optional but convincing)

Manually break the live state and watch Argo CD revert it:

```bash
kubectl -n gitops-demo scale deployment gitops-demo --replicas=5
kubectl -n argocd get applications gitops-demo -w   # watch it flip OutOfSync -> Synced as it's reset to 2
```
