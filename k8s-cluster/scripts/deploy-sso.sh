#!/usr/bin/env bash
# Deploy for the SSO (Ory Hydra + its Postgres + the OAuth2 client registration Job) on the sso nodes.
# Separate from deploy.sh/`make k8s-deploy`: no image is built here, it only changes with its manifests.
# The hydra-clients Job is deleted first — a Job's pod template is immutable, and a fresh run re-registers any
# client missing after a database reset (hydra-clients.sh skips the ones that exist).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFESTS="$ROOT_DIR/k8s-cluster/manifests"
export KUBECONFIG="${KUBECONFIG:-$ROOT_DIR/k8s-cluster/kubeconfig}"

echo "==> Applying SSO"
kubectl delete job hydra-clients -n sso --ignore-not-found
kubectl apply -k "$MANIFESTS/sso"

echo "==> Waiting for rollout"
kubectl rollout status deployment/postgres-hydra -n sso
kubectl rollout status deployment/hydra -n sso
kubectl wait --for=condition=complete job/hydra-clients -n sso --timeout=180s

echo "==> SSO deploy complete"
