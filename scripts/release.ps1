<#
.SYNOPSIS
  Manual release flow: build -> Docker image -> Docker Hub -> update GitOps
  manifest -> commit & push. Argo CD picks up the manifest change automatically.

.EXAMPLE
  ./scripts/release.ps1 -Version 1.1.0 -DockerHubUser shahid9741
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$DockerHubUser = "shahid9741"
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path "$PSScriptRoot\.."
$image = "$DockerHubUser/argocd-deployment:$Version"

Write-Host "==> Building Docker image $image" -ForegroundColor Cyan
docker build -t $image "$repoRoot/app"
if ($LASTEXITCODE -ne 0) { throw "docker build failed" }

Write-Host "==> Pushing $image to Docker Hub (make sure you ran 'docker login' first)" -ForegroundColor Cyan
docker push $image
if ($LASTEXITCODE -ne 0) { throw "docker push failed - did you run 'docker login'?" }

Write-Host "==> Updating gitops/deployment.yaml image tag" -ForegroundColor Cyan
$deploymentFile = "$repoRoot/gitops/deployment.yaml"
(Get-Content $deploymentFile -Raw) -replace 'image: .*/argocd-deployment:.*', "image: $image" |
    Set-Content -Encoding utf8 $deploymentFile

Write-Host "==> Committing and pushing GitOps manifest change" -ForegroundColor Cyan
Push-Location $repoRoot
git add gitops/deployment.yaml
git commit -m "deploy: bump image to $Version"
git push
Pop-Location

Write-Host "==> Done. Argo CD will auto-sync within its polling interval (default 3m)," -ForegroundColor Green
Write-Host "    or trigger it immediately with: argocd app sync gitops-demo" -ForegroundColor Green
