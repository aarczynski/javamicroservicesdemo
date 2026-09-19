#!/usr/bin/env bash
# Deploy for load-background: builds+pushes its image (tagged by git
# short-sha, same pattern as deploy.sh for app-candidates/app-job-offers),
# pins the manifest, applies, and waits for rollout. Separate from
# deploy.sh/`make k8s-deploy` because load-background's image only needs
# rebuilding when src/candidate-search.js, entrypoint.sh, or Dockerfile.k8s
# change — rare compared to app code, so it isn't rebuilt on every deploy.
#
# Build context is the repo root, not load-background/ — Dockerfile.k8s COPYs
# data-generator/output/candidates/01-candidates.sql into the image (see its
# own header comment); the actual data the running container serves is kept
# current separately via the /data PVC (k8s-cluster/scripts/load-data.sh),
# not by rebuilding this image.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFESTS="$ROOT_DIR/k8s-cluster/manifests"
export KUBECONFIG="${KUBECONFIG:-$ROOT_DIR/k8s-cluster/kubeconfig}"

# Keep in sync with local_registry_host in k8s-cluster/ansible/group_vars/all.yml.
REGISTRY="192.168.10.190:5000"
REGISTRY_RE='192\.168\.10\.104:5000'

cd "$ROOT_DIR"

SHA="$(git rev-parse --short HEAD)"
echo "==> Building load-background image for sha $SHA"
docker build -t "$REGISTRY/load-background:$SHA" -f load-background/Dockerfile.k8s .
docker push "$REGISTRY/load-background:$SHA"

echo "==> Pinning manifest to sha $SHA"
sed -i '' -E "s|($REGISTRY_RE/load-background):[^\"[:space:]]+|\1:$SHA|" "$MANIFESTS/load-background/app.yaml"

echo "==> Applying"
kubectl apply -f "$MANIFESTS/load-background/app.yaml"

echo "==> Waiting for rollout"
kubectl rollout status deployment/load-background -n load-background

echo "==> Deploy complete ($SHA)"
