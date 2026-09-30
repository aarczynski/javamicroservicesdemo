#!/usr/bin/env bash
# load-data.sh (full reload of generated data, see there) pointed at the
# minikube cluster instead of the RPi one. Not part of minikube-deploy/
# minikube-rebuild-all: those stay fast and don't touch data-generator at all.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Forced, not `${VAR:-default}` — see minikube-start.sh for why an inherited
# ambient KUBECONFIG must never leak in here.
export KUBECONFIG="$ROOT_DIR/k8s-cluster/kubeconfig-minikube"
export MINIKUBE_HOME="$ROOT_DIR/k8s-cluster/.minikube"

exec "$ROOT_DIR/k8s-cluster/scripts/load-data.sh"
