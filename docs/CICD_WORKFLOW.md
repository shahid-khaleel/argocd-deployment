# CI/CD Workflow

There are two equivalent ways to run the pipeline described in the project
brief (build -> image -> Docker Hub -> update manifest -> commit -> Argo CD
deploys). Pick whichever fits how you want to demo it.

## Option A — GitHub Actions (automated)

[`/.github/workflows/ci-cd.yml`](../.github/workflows/ci-cd.yml) runs on every
push to `main` that touches `app/**`:

1. Checks out the repo, sets up JDK 21, builds the jar with Maven
   (`mvn -B clean package`, which also runs the test suite).
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

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Fails at **Log in to Docker Hub** | `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` repo secrets aren't configured yet. Add them under **Settings → Secrets and variables → Actions**, then either push a new commit under `app/**` or re-run the job. |
| Fails at **Commit and push updated manifest** with a non-fast-forward / rejected push | `main` advanced between this job's checkout and its push — most commonly from clicking **Re-run all jobs** in the GitHub UI, which replays against the *original* triggering commit rather than the current tip of `main`, so any commit landed since (by you, a teammate, or another workflow run) creates a race. The workflow handles this itself: before writing the manifest it re-fetches and hard-resets to `origin/main`, then retries the push up to 5 times with backoff. If you see this fail even after retries, something is pushing to `main` faster than one job can keep up with — check what else is automating pushes to this repo. |
| Workflow never triggers on a push | Check the push actually touched a path listed under `on.push.paths` in [ci-cd.yml](../.github/workflows/ci-cd.yml) (`app/**` or the workflow file itself) — a `gitops/`-only change (like the manual release script produces) intentionally does *not* re-trigger this workflow, since that would create an infinite loop of CI re-deploying its own manifest commits. |
| `docker push` denied when running `scripts/release.sh`/`.ps1` locally | Same fix as any local push: run `docker login` first (access token, not your account password). |
| A live credential (Docker Hub token, etc.) ends up pasted in chat, a PR description, or committed by mistake | Treat it as compromised the moment that happens — rotate/revoke it (Docker Hub → Account Settings → Security) and issue a fresh one, rather than reusing it. Repo secrets should be the only place a token lives long-term; never commit one to a tracked file "as a variable," since anything committed is permanent in Git history once pushed. |
