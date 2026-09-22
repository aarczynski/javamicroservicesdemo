#!/usr/bin/env bash
# Creates (or resumes) the minikube cluster itself — the k8s control plane +
# node, no workloads yet. Idempotent: `minikube start` on an already-running
# cluster just verifies/no-ops.
#
# KUBECONFIG and MINIKUBE_HOME are forced (not `${VAR:-default}`) to a path
# inside the repo (k8s-cluster/), not ~/.kube or ~/.minikube — this is meant
# to be a disposable, project-local dev cluster, never mixed with the real
# RPi cluster's kubeconfig (k8s-cluster/kubeconfig) or whatever else this
# laptop's shell happens to have KUBECONFIG pointed at. `${VAR:-default}`
# would NOT be safe here: if the shell already exports KUBECONFIG (as this
# repo's own k8s-cluster/kubeconfig convention encourages), bash keeps that
# value instead of substituting the default — `minikube start` then merges a
# "minikube" context into whatever that file is and switches its
# current-context, which is exactly what happened to the real cluster's
# kubeconfig the first time this was written without the hard override.
#
# --cni=false + --container-runtime=docker: minikube's own CNI is skipped so
# Cilium (installed by minikube-bootstrap.sh) can be the real CNI, same as
# the RPi cluster — "containerd" runtime refuses to start without a CNI
# already present, "docker" runtime doesn't have that restriction.
#
# --ports=30080:30080: docker-driver-only flag that publishes that container
# port straight to 127.0.0.1 on the host, permanently, at container-creation
# time - no sudo, no `minikube tunnel`, no foreground terminal to leave open.
# Paired with the fixed NodePort Service app-candidates-lb (nodePort: 30080,
# k8s-cluster/manifests/overlays/minikube/nodeport-candidates.yaml) - real
# Kubernetes load balancing across all app-candidates replicas (unlike
# `kubectl port-forward`, which locks onto one pod). Only takes effect at
# container creation, so changing it needs `minikube delete` first - can't be
# applied to an already-running minikube.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export KUBECONFIG="$ROOT_DIR/k8s-cluster/kubeconfig-minikube"
export MINIKUBE_HOME="$ROOT_DIR/k8s-cluster/.minikube"

KUBERNETES_VERSION="${KUBERNETES_VERSION:-v1.36.0}"
MINIKUBE_CPUS="${MINIKUBE_CPUS:-8}"
MINIKUBE_MEMORY="${MINIKUBE_MEMORY:-20000}"

echo "==> Starting minikube (kubernetes $KUBERNETES_VERSION, ${MINIKUBE_CPUS} CPUs, ${MINIKUBE_MEMORY}MB)"
echo "    KUBECONFIG=$KUBECONFIG"
echo "    MINIKUBE_HOME=$MINIKUBE_HOME"
minikube start \
  --kubernetes-version="$KUBERNETES_VERSION" \
  --driver=docker \
  --container-runtime=docker \
  --cni=false \
  --cpus="$MINIKUBE_CPUS" \
  --memory="$MINIKUBE_MEMORY" \
  --ports=30080:30080

echo "==> Removing kube-proxy — Cilium's kube-proxy-replacement takes over (matches the RPi cluster)"
kubectl -n kube-system delete daemonset kube-proxy --ignore-not-found
kubectl -n kube-system delete configmap kube-proxy --ignore-not-found

echo "==> minikube is up"

# Harmless on a fresh cluster: minikube-forward.sh skips any service that
# doesn't exist yet ("not deployed yet?") instead of erroring. On a cluster
# that was `minikube-stop`'d (not deleted) and already has everything
# deployed, this is what actually restores access - no separate "resume"
# command needed, and minikube-rebuild-all/minikube-deploy still re-run it
# for real once bootstrap/deploy have created the services.
"$ROOT_DIR/k8s-cluster/scripts/minikube-forward.sh"

echo "==> If this is a fresh cluster (forwards above were skipped): make minikube-bootstrap"
echo "==> Real load-balanced access to app-candidates (all replicas, not just one pod): http://localhost:30080"
