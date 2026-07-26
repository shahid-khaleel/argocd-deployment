# Minikube Installation & Configuration Guide

## Prerequisites

- A container runtime/driver: Docker Desktop (recommended on Windows/Mac) or another supported driver (Hyper-V, VirtualBox, KVM).
- `kubectl` CLI.
- `minikube` CLI.

Check what you already have:

```bash
docker --version
minikube version
kubectl version --client
```

## 1. Install Minikube

- **Windows (winget):** `winget install Kubernetes.minikube`
- **Windows (choco):** `choco install minikube`
- **macOS (brew):** `brew install minikube`
- **Linux:** see https://minikube.sigs.k8s.io/docs/start/

## 2. Install kubectl

- **Windows (winget):** `winget install Kubernetes.kubectl`
- **macOS (brew):** `brew install kubectl`
- **Linux:** see https://kubernetes.io/docs/tasks/tools/

## 3. Start the cluster

Using the Docker driver (works with Docker Desktop, no hypervisor needed):

```bash
minikube start --driver=docker --cpus=4 --memory=6g
```

Verify:

```bash
minikube status
kubectl get nodes
```

Expected:

```
minikube
type: Control Plane
host: Running
kubelet: Running
apiserver: Running
kubeconfig: Configured
```

## 4. (Optional) Enable the Ingress addon

Only needed if you plan to use `gitops/ingress.yaml` instead of port-forwarding:

```bash
minikube addons enable ingress
```

## 5. Useful commands

| Purpose | Command |
|---|---|
| Cluster dashboard | `minikube dashboard` |
| SSH into the node | `minikube ssh` |
| Get the node IP | `minikube ip` |
| Point local `docker` CLI at Minikube's Docker daemon (avoids pushing to a registry for quick local tests) | `minikube docker-env` (then eval it) |
| Stop the cluster (keeps state) | `minikube stop` |
| Delete the cluster (full reset) | `minikube delete` |

## Troubleshooting

- **`minikube start` hangs or fails with a driver error** — confirm Docker Desktop is running (`docker info`) before starting Minikube.
- **Not enough resources** — lower `--cpus`/`--memory`, or increase Docker Desktop's resource limits (Settings → Resources).
- **Corporate proxy/firewall blocks image pulls** — configure `minikube start --docker-env HTTP_PROXY=... --docker-env HTTPS_PROXY=...` or pre-pull images with `minikube image load`.
- **`kubectl` talks to the wrong cluster** — run `kubectl config use-context minikube`.
