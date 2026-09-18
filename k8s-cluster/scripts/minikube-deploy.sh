#!/usr/bin/env bash
# Applies k8s-cluster/manifests/overlays/minikube (apps, Postgres, Gateway —
# see that directory's README.md). Unlike deploy.sh (the RPi cluster's day-
# to-day deploy), this doesn't build/push images: app.yaml already points at
# published, public ghcr.io/aarczynski/app-candidates and app-job-offers
# tags, which minikube pulls directly. Idempotent: kubectl apply -k is safe
# to re-run, and re-running it after a fresh `make minikube-image` (see
# there) re-deploys the same tag so pods pick up a rebuilt local image.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OVERLAY="$ROOT_DIR/k8s-cluster/manifests/overlays/minikube"
# Forced, not `${VAR:-default}` — see minikube-start.sh for why an inherited
# ambient KUBECONFIG must never leak in here.
export KUBECONFIG="$ROOT_DIR/k8s-cluster/kubeconfig-minikube"
export MINIKUBE_HOME="$ROOT_DIR/k8s-cluster/.minikube"

echo "==> Applying overlay: $OVERLAY"
kubectl apply -k "$OVERLAY"

echo "==> Waiting for rollout"
kubectl rollout status deployment/postgres-candidates -n candidates --timeout=180s
kubectl rollout status deployment/postgres-job-offers -n job-offers --timeout=180s
kubectl rollout status deployment/app-candidates -n candidates --timeout=180s
kubectl rollout status deployment/app-job-offers -n job-offers --timeout=180s
kubectl rollout status deployment/load-background -n load-background --timeout=180s

echo "==> Deploy complete"
