# Argo CD Installation & Configuration Guide

Assumes Minikube is already running (see [MINIKUBE_SETUP.md](MINIKUBE_SETUP.md)).

## 1. Create the `argocd` namespace and install Argo CD

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml --server-side --force-conflicts
```

> `--server-side` avoids a common `kubectl apply` error on this manifest
> (`metadata.annotations: Too long`) — the `applicationsets.argoproj.io` CRD
> is large enough to exceed the `last-applied-configuration` annotation size
> limit that client-side apply relies on.

## 2. Wait for all components to come up

```bash
kubectl -n argocd get pods -w
```

You should see these pods reach `Running`/`1-2/2 Ready` (Ctrl+C once they're all up):

- `argocd-application-controller-*`
- `argocd-applicationset-controller-*`
- `argocd-dex-server-*`
- `argocd-notifications-controller-*`
- `argocd-redis-*`
- `argocd-repo-server-*`
- `argocd-server-*`

Confirm services too:

```bash
kubectl -n argocd get svc
```

## 3. Expose the Argo CD UI

**Option A — port-forward (simplest, works everywhere):**

```bash
kubectl -n argocd port-forward svc/argocd-server 8081:443
```

Open https://localhost:8081 (self-signed cert — accept the browser warning).

**Option B — NodePort/LoadBalancer:**

```bash
kubectl -n argocd patch svc argocd-server -p '{"spec": {"type": "NodePort"}}'
minikube service argocd-server -n argocd
```

## 4. Retrieve the initial admin password

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d
```

(PowerShell equivalent)

```powershell
$b64 = kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}"
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($b64))
```

Log in to the UI with username `admin` and this password.

## 5. (Optional) Install the Argo CD CLI

- **Windows (choco):** `choco install argocd-cli`
- **macOS (brew):** `brew install argocd`
- **Linux:** see https://argo-cd.readthedocs.io/en/stable/cli_installation/

Log in from the CLI:

```bash
argocd login localhost:8081 --username admin --password <password-from-step-4> --insecure
```

## 6. (Recommended) Change the admin password

```bash
argocd account update-password
```

## Troubleshooting

- **Pods stuck in `Pending`** — check Minikube has enough CPU/memory (`kubectl -n argocd describe pod <name>`, look at `Events`).
- **`ImagePullBackOff`** — Minikube's node needs internet access to pull Argo CD images from `quay.io`/`ghcr.io`. Retry after checking your network/proxy.
- **UI shows "unable to connect"** — confirm the port-forward is still running in its terminal; it stops if you close that shell.
- **Login fails with the initial password** — the secret is deleted automatically after the admin password is changed once; if you already changed it, use the new password (or reset via `kubectl -n argocd delete secret argocd-initial-admin-secret` **before** first login, which regenerates it on next argocd-server restart).
- **An Application shows `Synced` but health stuck on `Progressing` forever, even though its pods are `Running`/`Ready`** — this isn't an Argo CD problem, it's usually one resource in the app that never reports a terminal status. For this repo specifically it's the `Ingress`: Argo CD's built-in health check waits for it to get an address, which never happens without an ingress controller — see the Ingress addon step in [MINIKUBE_SETUP.md](MINIKUBE_SETUP.md#4-enable-the-ingress-addon).
