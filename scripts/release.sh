#!/usr/bin/env bash
# Manual release flow: build -> Docker image -> Docker Hub -> update GitOps
# manifest -> commit & push. Argo CD picks up the manifest change automatically.
#
# Usage: ./scripts/release.sh <version> [dockerhub-user]
set -euo pipefail

VERSION="${1:?Usage: ./scripts/release.sh <version> [dockerhub-user]}"
DOCKERHUB_USER="${2:-shahid9741}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${DOCKERHUB_USER}/argocd-deployment:${VERSION}"

echo "==> Building Docker image ${IMAGE}"
docker build -t "${IMAGE}" "${REPO_ROOT}/app"

echo "==> Pushing ${IMAGE} to Docker Hub (make sure you ran 'docker login' first)"
docker push "${IMAGE}"

echo "==> Updating gitops/deployment.yaml image tag"
sed -i "s|image: .*/argocd-deployment:.*|image: ${IMAGE}|" "${REPO_ROOT}/gitops/deployment.yaml"

echo "==> Committing and pushing GitOps manifest change"
cd "${REPO_ROOT}"
git add gitops/deployment.yaml
git commit -m "deploy: bump image to ${VERSION}"
git push

echo "==> Done. Argo CD will auto-sync within its polling interval (default 3m),"
echo "    or trigger it immediately with: argocd app sync gitops-demo"
