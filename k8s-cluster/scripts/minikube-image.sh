#!/usr/bin/env bash
# Builds all three app images locally and loads them straight into
# minikube's own image cache via `minikube image load` — no registry
# involved, not ghcr.io and not the RPi cluster's self-hosted one at
# 192.168.10.101:5000. minikube is meant to work from anywhere (home LAN or
# not, offline or not) to test manifest/code changes before they touch the
# real cluster — depending on either registry would break that.
#
# Tagged `:local`, not a git sha or version — this is a disposable dev loop
# (rebuild, reload, `make minikube-deploy`, repeat), not a pinned deploy.
# Kubernetes' default imagePullPolicy for a non-`latest` tag is IfNotPresent,
# so once loaded, the pod uses exactly this image without ever trying to
# pull it from anywhere.
#
# load-background's image (via Dockerfile.k8s) bakes in whatever's currently
# at data-generator/output/candidates/01-candidates.sql — run `make
# generate-data` first if that file doesn't exist yet. Irrelevant to what
# candidate IDs load-background actually serves on minikube though: its /data
# PVC starts empty by design (see overlays/minikube/README.md) and only gets
# real data via `make minikube-load-data`.
# Forced, not `${VAR:-default}` — see minikube-start.sh for why an inherited
# ambient KUBECONFIG must never leak in here.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export KUBECONFIG="$ROOT_DIR/k8s-cluster/kubeconfig-minikube"
export MINIKUBE_HOME="$ROOT_DIR/k8s-cluster/.minikube"

cd "$ROOT_DIR"

echo "==> Building images"
./gradlew clean :app-job-offers:build :app-candidates:build
docker build -t app-candidates:local -f app-candidates/docker/Dockerfile app-candidates
docker build -t app-job-offers:local -f app-job-offers/docker/Dockerfile app-job-offers
docker build -t load-background:local -f load-background/Dockerfile.k8s .

echo "==> Loading into minikube"
minikube image load app-candidates:local
minikube image load app-job-offers:local
minikube image load load-background:local

# `minikube image load` overwrites the `:local` tag's content in place —
# kubelet's IfNotPresent policy means an already-running pod won't notice,
# so a restart is the only way to actually pick up the rebuilt image. No-op
# (silently skipped) the first time, before `make minikube-deploy` has ever
# created these Deployments.
echo "==> Restarting deployments already running on minikube, if any, to pick up the reloaded images"
for deploy_ns in "app-candidates candidates" "app-job-offers job-offers" "load-background load-background"; do
  read -r deploy ns <<<"$deploy_ns"
  if kubectl get deployment "$deploy" -n "$ns" >/dev/null 2>&1; then
    kubectl rollout restart "deployment/$deploy" -n "$ns"
  fi
done

echo "==> Images loaded. Run \`make minikube-deploy\` to (re)deploy pods against them."
