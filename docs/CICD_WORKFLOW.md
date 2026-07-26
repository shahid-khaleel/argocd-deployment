# CI/CD Workflow

There are two equivalent ways to run the pipeline described in the project
brief (build -> image -> Docker Hub -> update manifest -> commit -> Argo CD
deploys). Pick whichever fits how you want to demo it.

## Option A — GitHub Actions (automated)

[`/.github/workflows/ci-cd.yml`](../.github/workflows/ci-cd.yml) runs on every
push to `main` that touches `app/**`:

1. Checks out the repo, sets up JDK 21, builds the jar with Maven.
2. Logs in to Docker Hub using repo secrets.
3. Builds and pushes `<user>/argocd-deployment:<git-short-sha>` and `:latest`.
4. Rewrites the image tag in `gitops/deployment.yaml`.
5. Commits and pushes that change back to `main`.
6. Argo CD (already watching the repo) detects the manifest change and
   syncs it into Minikube.

### One-time setup

In your GitHub repo: **Settings -> Secrets and variables -> Actions**, add:

| Secret | Value |
|---|---|
| `DOCKERHUB_USERNAME` | your Docker Hub username |
| `DOCKERHUB_TOKEN` | a Docker Hub [access token](https://hub.docker.com/settings/security) (not your password) |

Push a change under `app/` and check the **Actions** tab for the run.

> Note: this workflow builds/pushes/commits from GitHub's runners, which can
> reach the public internet — it can't reach your local Minikube cluster
> directly. Argo CD (running in Minikube) still has to pull the new manifest
> from GitHub itself, which it does on its normal polling interval, or via
> `argocd app sync` / a webhook (see [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#8-optional-get-instant-sync-instead-of-polling)).

## Option B — Manual release script (no GitHub Actions needed)

Useful when you want to run the whole pipeline from your own machine, e.g. for
a live local demo:

```bash
./scripts/release.sh 1.2.0 <dockerhub-user>
```

```powershell
./scripts/release.ps1 -Version 1.2.0 -DockerHubUser <dockerhub-user>
```

This does the same 5 steps as the Actions workflow (build image, push, edit
`gitops/deployment.yaml`, commit, push), just synchronously in your terminal
instead of on a GitHub runner. Requires `docker login` to have been run once.

## Either way, the last mile is identical

Both paths end with an updated `gitops/deployment.yaml` on `main`. From there
it's Argo CD's job (not CI's) to notice the Git change and reconcile the
cluster — that hand-off from "Git changed" to "cluster changed" is the actual
GitOps mechanism this project demonstrates.
