#!/usr/bin/env bash
# Installs the cluster-wide pieces on top of a bare minikube cluster (see
# minikube-start.sh): Cilium as CNI + Gateway API controller, Gateway API
# CRDs, MetalLB, local-path-provisioner. Mirrors bootstrap.sh's chart
# versions/order for the RPi cluster, minus everything Phase 2 of
# k8s-cluster/manifests/overlays/minikube/README.md defers (Kafka, MinIO,
# Tempo/Loki/Prometheus/Grafana/Alloy, Headlamp, metrics-server).
# Idempotent: helm upgrade --install / kubectl apply / minikube addons
# disable are all safe to re-run.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFESTS="$ROOT_DIR/k8s-cluster/manifests"
OVERLAY="$MANIFESTS/overlays/minikube"
# Forced, not `${VAR:-default}` — see minikube-start.sh for why an inherited
# ambient KUBECONFIG must never leak in here.
export KUBECONFIG="$ROOT_DIR/k8s-cluster/kubeconfig-minikube"
export MINIKUBE_HOME="$ROOT_DIR/k8s-cluster/.minikube"

echo "==> Adding Helm repos"
helm repo add cilium https://helm.cilium.io/ >/dev/null
helm repo add metallb https://metallb.github.io/metallb >/dev/null
helm repo update cilium metallb >/dev/null

echo "==> CNI: Cilium 1.19.5 (includes Hubble Relay/UI, Gateway API controller)"
helm upgrade --install cilium cilium/cilium -n kube-system --version 1.19.5 \
  -f "$MANIFESTS/cilium/values-cilium.yaml" \
  -f "$OVERLAY/cilium-values.yaml"

echo "==> Gateway API CRDs (standard channel, v1.4.1)"
for crd in gatewayclasses gateways httproutes referencegrants grpcroutes; do
  kubectl apply -f "https://raw.githubusercontent.com/kubernetes-sigs/gateway-api/v1.4.1/config/crd/standard/gateway.networking.k8s.io_${crd}.yaml"
done

echo "==> MetalLB"
kubectl create namespace metallb-system --dry-run=client -o yaml | kubectl apply -f -
helm upgrade --install metallb metallb/metallb -n metallb-system \
  -f "$MANIFESTS/metallb/values-metallb.yaml"
echo "==> Waiting for MetalLB's validating webhook to be ready (IPAddressPool/L2Advertisement need it)"
# Retries, not a single 120s kubectl wait: re-running this script re-upgrades
# Cilium too (see above), which restarts cilium-agent and briefly disrupts
# ALL pod networking cluster-wide (same "SandboxChanged"/cascading restarts
# behavior documented in .claude/handoff-k8s-rpi-cluster.md's 2026-09-15
# entry) — metallb-controller's own readiness probe can flip false during
# that window, long enough on a re-run to blow through a single 120s wait
# even though the deployment recovers moments later on its own.
for i in $(seq 1 12); do
  if kubectl wait --for=condition=Available deployment/metallb-controller -n metallb-system --timeout=20s; then
    break
  fi
  echo "    not Available yet (likely still settling from the Cilium restart above), retrying ($i/12)..."
  if [[ "$i" == "12" ]]; then
    echo "metallb-controller never became Available" >&2
    exit 1
  fi
done
for i in $(seq 1 12); do
  if kubectl apply -f "$OVERLAY/metallb-ip-pools.yaml" -f "$OVERLAY/metallb-l2-advertisement.yaml" 2>/tmp/metallb-apply-err; then
    break
  fi
  echo "    webhook not reachable yet, retrying in 5s ($i/12)..."
  sleep 5
  if [[ "$i" == "12" ]]; then
    cat /tmp/metallb-apply-err >&2
    exit 1
  fi
done

echo "==> local-path-provisioner v0.0.37 (matches bootstrap.sh — same StorageClass name, 'local-path', as production)"
minikube addons disable storage-provisioner-rancher >/dev/null 2>&1 || true
minikube addons disable default-storageclass >/dev/null 2>&1 || true
minikube addons disable storage-provisioner >/dev/null 2>&1 || true
kubectl apply -f "https://raw.githubusercontent.com/rancher/local-path-provisioner/v0.0.37/deploy/local-path-storage.yaml"

echo "==> Bootstrap complete. Next: make minikube-deploy"
