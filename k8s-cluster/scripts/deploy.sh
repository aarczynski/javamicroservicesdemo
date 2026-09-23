#!/usr/bin/env bash
# Day-to-day deploy: builds+pushes both app images (tagged by git short-sha,
# so imagePullPolicy: IfNotPresent still re-pulls when code actually
# changed), regenerates the Grafana dashboards ConfigMap from source JSON,
# and applies everything. Safe to run even when nothing changed — same sha
# in, same sha out, kubectl apply no-ops.
#
# Pushes to the self-hosted registry (k8s-cluster/manifests/registry/registry.yaml),
# not ghcr.io — no login needed, but it's plain HTTP with no TLS cert, so
# Docker needs to trust it explicitly first; `make k8s-deploy` handles that
# via ensure-insecure-registry (scripts/ensure-insecure-registry.sh) before
# this script ever runs.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFESTS="$ROOT_DIR/k8s-cluster/manifests"
export KUBECONFIG="${KUBECONFIG:-$ROOT_DIR/k8s-cluster/kubeconfig}"

# Keep in sync with local_registry_host in k8s-cluster/ansible/group_vars/all.yml.
REGISTRY="192.168.10.190:5000"
REGISTRY_RE='192\.168\.10\.190:5000'

cd "$ROOT_DIR"

SHA="$(git rev-parse --short HEAD)"
echo "==> Building images for sha $SHA"
./gradlew clean :app-job-offers:build :app-candidates:build

docker build -t "$REGISTRY/app-candidates:$SHA" -f app-candidates/docker/Dockerfile app-candidates
docker push "$REGISTRY/app-candidates:$SHA"

docker build -t "$REGISTRY/app-job-offers:$SHA" -f app-job-offers/docker/Dockerfile app-job-offers
docker push "$REGISTRY/app-job-offers:$SHA"

echo "==> Pinning manifests to sha $SHA"
sed -i '' -E "s|($REGISTRY_RE/app-candidates):[^\"[:space:]]+|\1:$SHA|" "$MANIFESTS/candidates/app.yaml"
sed -i '' -E "s|($REGISTRY_RE/app-job-offers):[^\"[:space:]]+|\1:$SHA|" "$MANIFESTS/job-offers/app.yaml"

COMPOSE_ONLY_DASHBOARDS="postgres-monitoring.json"

shared_dashboard_args() {
  for dashboard in "$ROOT_DIR"/observability/grafana/provisioning/dashboards/*; do
    if [[ " $COMPOSE_ONLY_DASHBOARDS " != *" $(basename "$dashboard") "* ]]; then
      echo "--from-file=$dashboard"
    fi
  done
}

echo "==> Regenerating dashboards ConfigMap"
kubectl create configmap grafana-dashboards --namespace=observability \
  $(shared_dashboard_args) \
  --from-file="$MANIFESTS/observability/dashboards/" \
  --dry-run=client -o yaml \
  | kubectl label -f - --local -o yaml grafana_dashboard=1 \
  > "$MANIFESTS/observability/dashboards-configmap.yaml.tmp"
mv "$MANIFESTS/observability/dashboards-configmap.yaml.tmp" "$MANIFESTS/observability/dashboards-configmap.yaml"

echo "==> Applying"
kubectl apply -f "$MANIFESTS/candidates/app.yaml"
kubectl apply -f "$MANIFESTS/candidates/hpa.yaml"
kubectl apply -f "$MANIFESTS/job-offers/app.yaml"
kubectl apply -f "$MANIFESTS/observability/dashboards-configmap.yaml"

echo "==> Waiting for rollout"
kubectl rollout status deployment/app-candidates -n candidates
kubectl rollout status deployment/app-job-offers -n job-offers

echo "==> Deploy complete ($SHA)"
