# Screenshots

Drop captures here as you work through the deployment guide. Suggested files:

- `01-springboot-running.png` — console/log output of the app starting up
- `02-login-ui.png` — the login page in a browser
- `03-dockerhub-repo.png` — the image/tags in your Docker Hub repository
- `04-minikube-pods-services.png` — `kubectl -n gitops-demo get pods,svc`
- `05-argocd-pods.png` — `kubectl -n argocd get pods`
- `06-argocd-healthy-synced.png` — the Argo CD UI showing the app **Healthy** and **Synced**
- `07-gitops-update-deployed.png` — the app (or `kubectl get pods`) after a Git commit triggered a new rollout
