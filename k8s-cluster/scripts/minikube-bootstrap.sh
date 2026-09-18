#!/usr/bin/env bash
# Everything on top of a bare minikube cluster (see minikube-start.sh):
# Cilium CNI + Gateway API, MetalLB, local-path-provisioner, the full
# observability stack (Kafka/MinIO/tempo-distributed, Loki, Prometheus,
# Grafana, otel-collector, Alloy), Headlamp, metrics-server, and finally the
# apps themselves (candidates/job-offers/load-background) — one script, same
# shape as bootstrap.sh for the RPi cluster (which also ends with "Apps"),
# each Helm release layered with this overlay's matching values-*.yaml
# override where one exists (see k8s-cluster/manifests/overlays/minikube/
# README.md for what each override drops and why). Idempotent: helm upgrade
# --install / kubectl apply are both safe to re-run.
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
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo add grafana-community https://grafana-community.github.io/helm-charts >/dev/null
helm repo add grafana https://grafana.github.io/helm-charts >/dev/null
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts >/dev/null
helm repo add strimzi https://strimzi.io/charts/ >/dev/null
helm repo add headlamp https://kubernetes-sigs.github.io/headlamp/ >/dev/null
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ >/dev/null
helm repo update >/dev/null

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
# Retries, not a single kubectl wait: the Cilium upgrade above restarts
# cilium-agent and briefly disrupts ALL pod networking cluster-wide (same
# "SandboxChanged"/cascading restarts behavior documented in
# .claude/handoff-k8s-rpi-cluster.md's 2026-09-15 entry) — metallb-
# controller's own readiness probe can flip false during that window, long
# enough on a re-run to blow through a single wait even though the
# deployment recovers moments later on its own.
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

echo "==> Observability namespace"
kubectl apply -f "$MANIFESTS/observability/namespace.yaml"

echo "==> Prometheus 29.27.0"
helm upgrade --install prometheus prometheus-community/prometheus -n observability --version 29.27.0 \
  -f "$MANIFESTS/observability/values-prometheus.yaml" \
  -f "$OVERLAY/values-prometheus.yaml"

echo "==> Loki 18.11.3"
helm upgrade --install loki grafana-community/loki -n observability --version 18.11.3 \
  -f "$MANIFESTS/observability/values-loki.yaml" \
  -f "$OVERLAY/values-loki.yaml"

echo "==> Strimzi Kafka operator 1.2.0"
helm upgrade --install strimzi-kafka-operator strimzi/strimzi-kafka-operator -n observability --version 1.2.0 \
  -f "$MANIFESTS/kafka/values-strimzi-operator.yaml" \
  -f "$OVERLAY/values-strimzi-operator.yaml"
echo "    Waiting for the Kafka/KafkaNodePool CRDs the operator installs"
kubectl wait --for=condition=Established crd/kafkas.kafka.strimzi.io crd/kafkanodepools.kafka.strimzi.io --timeout=120s

echo "==> Kafka cluster + MinIO"
kubectl apply -k "$OVERLAY/observability"

echo "==> tempo-distributed 3.5.1"
helm upgrade --install tempo grafana-community/tempo-distributed -n observability --version 3.5.1 \
  -f "$MANIFESTS/observability/values-tempo-distributed.yaml" \
  -f "$OVERLAY/values-tempo-distributed.yaml"

echo "==> otel-collector 0.171.0"
helm upgrade --install otel-collector open-telemetry/opentelemetry-collector -n observability --version 0.171.0 \
  -f "$MANIFESTS/observability/values-otel-collector.yaml" \
  -f "$OVERLAY/values-otel-collector.yaml"

echo "==> Alloy 1.12.1"
helm upgrade --install alloy grafana/alloy -n observability --version 1.12.1 \
  -f "$MANIFESTS/alloy/values-alloy.yaml" \
  -f "$OVERLAY/values-alloy.yaml"

echo "==> Grafana 12.11.2"
helm upgrade --install grafana grafana-community/grafana -n observability --version 12.11.2 \
  -f "$MANIFESTS/observability/values-grafana.yaml" \
  -f "$OVERLAY/values-grafana.yaml"

echo "==> Headlamp 0.45.0"
kubectl apply -f "$MANIFESTS/headlamp/namespace.yaml"
helm upgrade --install headlamp headlamp/headlamp -n headlamp --version 0.45.0 \
  -f "$MANIFESTS/headlamp/values-headlamp.yaml" \
  -f "$OVERLAY/values-headlamp.yaml"

echo "==> metrics-server 3.14.0"
helm upgrade --install metrics-server metrics-server/metrics-server -n kube-system --version 3.14.0 \
  -f "$MANIFESTS/metrics-server/values-metrics-server.yaml" \
  -f "$OVERLAY/values-metrics-server.yaml"

echo "==> Apps (candidates, job-offers, load-background)"
kubectl apply -k "$OVERLAY"
kubectl rollout status deployment/postgres-candidates -n candidates --timeout=180s
kubectl rollout status deployment/postgres-job-offers -n job-offers --timeout=180s
kubectl rollout status deployment/app-candidates -n candidates --timeout=180s
kubectl rollout status deployment/app-job-offers -n job-offers --timeout=180s
kubectl rollout status deployment/load-background -n load-background --timeout=180s

echo "==> Bootstrap complete"
