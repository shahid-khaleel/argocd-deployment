# gitops/

This directory is the **source of truth for the cluster**. Argo CD watches it
directly — nothing in here is ever applied by hand except as a one-time
bootstrap or for manual troubleshooting; in normal operation, every change
that reaches `main` under this path is reconciled onto the cluster by Argo CD
itself.

## How Argo CD consumes this directory

Configured by [`../argocd-application.yaml`](../argocd-application.yaml):

| Setting | Value | Meaning |
|---|---|---|
| `spec.source.repoURL` | `https://github.com/shahid-khaleel/argocd-deployment.git` | This repo |
| `spec.source.targetRevision` | `HEAD` | Always tracks the tip of the default branch (`main`) |
| `spec.source.path` | `gitops` | Only this directory is treated as manifests to apply — nothing outside it (e.g. `app/`, `docs/`) is ever synced |
| `spec.destination.namespace` | `gitops-demo` | Target namespace in-cluster |
| `spec.syncPolicy.automated.prune` | `true` | Resources removed from this directory are deleted from the cluster automatically |
| `spec.syncPolicy.automated.selfHeal` | `true` | Manual/out-of-band cluster drift (e.g. someone runs `kubectl scale` by hand) is reverted back to match Git |
| `spec.syncPolicy.syncOptions: CreateNamespace=true` | — | The `gitops-demo` namespace is created automatically if it doesn't exist (in addition to `namespace.yaml` below) |
| `spec.syncPolicy.retry` | 5 attempts, 5s backoff (factor 2, max 3m) | Retries a failed sync instead of giving up immediately |

In short: **edit a file in this directory, commit, push to `main`** — Argo CD
picks up the diff (on its poll interval, or instantly with a webhook, see
[docs/DEPLOYMENT_GUIDE.md](../docs/DEPLOYMENT_GUIDE.md#8-optional-get-instant-sync-instead-of-polling))
and reconciles the cluster to match. There is no `kubectl apply` step in the
normal flow — the only manifest applied by hand is
[`argocd-application.yaml`](../argocd-application.yaml) itself, once, because
it registers the Application with Argo CD in the first place and deliberately
lives outside the `gitops` path so Argo CD doesn't try to manage its own
registration.

## What each manifest does

| File | Kind | Role |
|---|---|---|
| [`namespace.yaml`](namespace.yaml) | `Namespace` | Declares `gitops-demo`. Belt-and-suspenders alongside `syncOptions: CreateNamespace=true` above — keeping it explicit here means the namespace shows up as a normal Argo CD-managed resource (and is pruned/tracked like everything else) rather than only existing as an implicit side effect. |
| [`configmap.yaml`](configmap.yaml) | `ConfigMap` | Non-secret runtime config: `APP_VERSION`, `WELCOME_MESSAGE`. Consumed by the Deployment via `envFrom.configMapRef`. Editing this and pushing is the easiest way to demonstrate a GitOps change with no new image — see [docs/DEPLOYMENT_GUIDE.md, step 7A](../docs/DEPLOYMENT_GUIDE.md#7-demonstrate-the-gitops-update-flow). Note: ConfigMap edits don't restart pods automatically; run `kubectl -n gitops-demo rollout restart deployment gitops-demo` to pick them up. |
| [`secret.yaml`](secret.yaml) | `Secret` (`Opaque`) | Demo login credentials (`DEMO_USERNAME`/`DEMO_PASSWORD`), consumed the same way via `envFrom.secretRef`. **Committed in plaintext `stringData` on purpose**, to demonstrate the Secret -> Deployment wiring end to end — the file carries its own warning comment. Not a production-safe pattern; see the root [README's Known Issues](../README.md#known-issues--recommendations) for what to use instead (Sealed Secrets, SOPS, External Secrets Operator) if adapting this repo. |
| [`deployment.yaml`](deployment.yaml) | `Deployment` | The app itself: 2 replicas, image reference (rewritten by CI/`scripts/release.*` on every release), `envFrom` wiring to the ConfigMap and Secret above, and `startupProbe`/`readinessProbe`/`livenessProbe` tuned for a Spring Boot cold start on a resource-constrained Minikube node (up to 150s startup budget before liveness checks even begin — see [docs/DEPLOYMENT_GUIDE.md](../docs/DEPLOYMENT_GUIDE.md#a-startup-timing-gotcha-on-constrained-nodes)). |
| [`service.yaml`](service.yaml) | `Service` (`ClusterIP`) | Stable in-cluster endpoint (`gitops-demo:80` -> pod `:8080`) fronting the Deployment's pods. Reached from outside the cluster via `kubectl port-forward` or the Ingress below. |
| [`ingress.yaml`](ingress.yaml) | `Ingress` | Optional host-based route (`gitops-demo.local`) through the `nginx` ingress class. Requires `minikube addons enable ingress` — without the addon, this resource never gets an address and Argo CD reports the Application stuck `Progressing` (not `Healthy`) forever, even though the app itself is fine. Delete this file if you don't want to deal with Ingress at all; `kubectl port-forward` to the Service is sufficient for the demo either way. |

## What updates these files

- **CI** ([`../.github/workflows/ci-cd.yml`](../.github/workflows/ci-cd.yml)):
  on every push to `main` touching `app/**`, rewrites the `image:` line in
  `deployment.yaml` to the newly built/pushed tag, then commits and pushes
  that change back to `main` itself.
- **`scripts/release.sh` / `scripts/release.ps1`**: the manual equivalent —
  same rewrite, commit, and push, run locally instead of on a GitHub runner.
- **You, directly**, for anything else (e.g. `configmap.yaml` edits, scaling
  `replicas`, adding a new manifest) — just edit, commit, and push like any
  other file in the repo. Argo CD does the rest.
