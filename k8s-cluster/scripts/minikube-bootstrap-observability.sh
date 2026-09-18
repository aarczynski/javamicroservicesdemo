#!/usr/bin/env bash
# Phase 2 of k8s-cluster/manifests/overlays/minikube/README.md: the
# observability stack (Prometheus, Loki, Kafka + MinIO for tempo-distributed,
# Tempo, otel-collector, Alloy, Grafana), Headlamp, metrics-server. Requires
# minikube-bootstrap.sh (Phase 1: Cilium/MetalLB/storage) to have already
# run. Mirrors bootstrap.sh's chart versions/order for the RPi cluster, each
# release layered with this overlay's matching values-*.yaml override (see
# that directory's README.md for what each override drops and why).
# Idempotent: helm upgrade --install / kubectl apply are both safe to re-run.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFESTS="$ROOT_DIR/k8s-cluster/manifests"
OVERLAY="$MANIFESTS/overlays/minikube"
# Forced, not `${VAR:-default}` — see minikube-start.sh for why an inherited
# ambient KUBECONFIG must never leak in here.
export KUBECONFIG="$ROOT_DIR/k8s-cluster/kubeconfig-minikube"
export MINIKUBE_HOME="$ROOT_DIR/k8s-cluster/.minikube"

echo "==> Adding Helm repos"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo add grafana-community https://grafana-community.github.io/helm-charts >/dev/null
helm repo add grafana https://grafana.github.io/helm-charts >/dev/null
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts >/dev/null
helm repo add strimzi https://strimzi.io/charts/ >/dev/null
helm repo add headlamp https://kubernetes-sigs.github.io/headlamp/ >/dev/null
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ >/dev/null
helm repo update >/dev/null

echo "==> Namespace"
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

echo "==> Phase 2 bootstrap complete"
echo "    Grafana:  http://192.168.49.148 (docker-network only — 'make minikube-tunnel' or docker exec minikube curl)"
echo "    Headlamp: http://192.168.49.149"
