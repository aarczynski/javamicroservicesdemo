#!/usr/bin/env bash
# Installs/upgrades every Helm release and plain manifest the cluster needs,
# in dependency order. Idempotent: helm upgrade --install and kubectl apply
# are both safe to re-run. Requires cluster-init.yml to have already run
# (KUBECONFIG must point at a live control plane).
#
# Versions/repos here mirror the "helm repo add" / "helm install --version"
# comments already documented at the top of each values-*.yaml file — kept
# in sync with those, not invented here.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFESTS="$ROOT_DIR/k8s-cluster/manifests"
export KUBECONFIG="${KUBECONFIG:-$ROOT_DIR/k8s-cluster/kubeconfig}"

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
helm upgrade --install cilium cilium/cilium -n kube-system \
  -f "$MANIFESTS/cilium/values-cilium.yaml" --version 1.19.5

echo "==> Gateway API CRDs (standard channel, v1.4.1)"
for crd in gatewayclasses gateways httproutes referencegrants grpcroutes; do
  kubectl apply -f "https://raw.githubusercontent.com/kubernetes-sigs/gateway-api/v1.4.1/config/crd/standard/gateway.networking.k8s.io_${crd}.yaml"
done

echo "==> Cilium GatewayClass (not auto-created by the Cilium chart, see gatewayclass.yaml)"
kubectl apply -f "$MANIFESTS/cilium/gatewayclass.yaml"

echo "==> MetalLB"
helm upgrade --install metallb metallb/metallb -n metallb-system --create-namespace \
  -f "$MANIFESTS/metallb/values-metallb.yaml"
echo "==> Waiting for MetalLB's validating webhook to be ready (IPAddressPool/L2Advertisement need it)"
kubectl wait --for=condition=Available deployment/metallb-controller -n metallb-system --timeout=120s
# The Deployment going Available doesn't guarantee the webhook Service is
# actually reachable yet (CNI service routing needs a few more seconds to
# converge) - retry the apply instead of a single fixed wait.
for i in $(seq 1 12); do
  if kubectl apply -f "$MANIFESTS/metallb/ip-address-pool.yaml" -f "$MANIFESTS/metallb/l2-advertisement.yaml" 2>/tmp/metallb-apply-err; then
    break
  fi
  echo "    webhook not reachable yet, retrying in 5s ($i/12)..."
  sleep 5
  if [[ "$i" == "12" ]]; then
    cat /tmp/metallb-apply-err >&2
    exit 1
  fi
done

echo "==> local-path-provisioner v0.0.37 (default StorageClass)"
kubectl apply -f "https://raw.githubusercontent.com/rancher/local-path-provisioner/v0.0.37/deploy/local-path-storage.yaml"

echo "==> Image registry (self-hosted, replaces ghcr.io — see registry.yaml)"
kubectl apply -f "$MANIFESTS/registry/namespace.yaml"
kubectl apply -f "$MANIFESTS/registry/"

echo "==> Observability stack"
kubectl apply -f "$MANIFESTS/observability/namespace.yaml"
helm upgrade --install prometheus prometheus-community/prometheus -n observability \
  -f "$MANIFESTS/observability/values-prometheus.yaml" --version 29.27.0
helm upgrade --install loki grafana-community/loki -n observability \
  -f "$MANIFESTS/observability/values-loki.yaml" --version 18.11.3
helm upgrade --install strimzi-kafka-operator strimzi/strimzi-kafka-operator -n observability \
  -f "$MANIFESTS/kafka/values-strimzi-operator.yaml" --version 1.2.0
kubectl apply -f "$MANIFESTS/kafka/kafka-cluster.yaml" -f "$MANIFESTS/minio/minio.yaml"
helm upgrade --install tempo grafana-community/tempo-distributed -n observability \
  -f "$MANIFESTS/observability/values-tempo-distributed.yaml" --version 3.5.1
helm upgrade --install otel-collector open-telemetry/opentelemetry-collector -n observability \
  -f "$MANIFESTS/observability/values-otel-collector.yaml" --version 0.171.0
helm upgrade --install alloy grafana/alloy -n observability \
  -f "$MANIFESTS/alloy/values-alloy.yaml" --version 1.12.1
helm upgrade --install grafana grafana-community/grafana -n observability \
  -f "$MANIFESTS/observability/values-grafana.yaml" --version 12.11.2
kubectl apply -f "$MANIFESTS/observability/dashboards-configmap.yaml"

echo "==> Headlamp"
kubectl apply -f "$MANIFESTS/headlamp/namespace.yaml"
helm upgrade --install headlamp headlamp/headlamp -n headlamp \
  -f "$MANIFESTS/headlamp/values-headlamp.yaml" --version 0.45.0

echo "==> metrics-server"
helm upgrade --install metrics-server metrics-server/metrics-server -n kube-system \
  -f "$MANIFESTS/metrics-server/values-metrics-server.yaml" --version 3.14.0

echo "==> Apps"
# Explicit filenames, not `-f "$dir/"` — that directory-wide form tries to
# apply every *.yaml in the dir including kustomization.yaml (added so the
# minikube overlay can use these dirs as kustomize bases), which isn't a
# valid raw manifest and makes kubectl fail with "apiVersion not set, kind
# not set". Same pattern deploy.sh already uses for candidates/job-offers.
kubectl apply -f "$MANIFESTS/candidates/namespace.yaml"
kubectl apply -f "$MANIFESTS/candidates/postgres.yaml" -f "$MANIFESTS/candidates/app.yaml" -f "$MANIFESTS/candidates/gateway.yaml"
kubectl apply -f "$MANIFESTS/job-offers/namespace.yaml"
kubectl apply -f "$MANIFESTS/job-offers/postgres.yaml" -f "$MANIFESTS/job-offers/app.yaml"
kubectl apply -f "$MANIFESTS/load-background/namespace.yaml"
kubectl apply -f "$MANIFESTS/load-background/app.yaml"

echo "==> Bootstrap complete"
