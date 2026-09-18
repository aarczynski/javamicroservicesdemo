#!/usr/bin/env bash
# Host access without `minikube tunnel`'s sudo prompt: on macOS, minikube's
# docker driver puts the cluster on a network (192.168.49.0/24 by default)
# that Docker Desktop's VM doesn't route to the host, so a MetalLB
# LoadBalancer IP is only reachable from inside that network (e.g. `docker
# exec minikube curl ...`), not from a Mac terminal/browser directly.
# `kubectl port-forward` tunnels through the apiserver instead, which IS
# reachable from the host — no sudo needed.
#
# FOREGROUND, blocking, Ctrl+C to stop — deliberately, same shape as `make
# start`'s `docker compose up --build`. This is the last step `make
# minikube-up` chains itself, so the one command that stands the cluster up
# also leaves it reachable, and stopping it is the same muscle memory as
# stopping Compose: Ctrl+C, not a second command to remember.
#
# Forwards straight to each Service, not the Gateway: the Gateway's Service
# (cilium-gateway-api-gateway) has no selector — Cilium steers traffic to it
# through its own Envoy/eBPF dataplane, not standard kube Endpoints — and
# `kubectl port-forward` requires one to resolve a backing pod. To exercise
# the actual Gateway/MetalLB path from the host, use `make minikube-tunnel`
# instead (routes the real LB IP, needs sudo).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Forced, not `${VAR:-default}` — see minikube-start.sh for why an inherited
# ambient KUBECONFIG must never leak in here.
export KUBECONFIG="$ROOT_DIR/k8s-cluster/kubeconfig-minikube"
export MINIKUBE_HOME="$ROOT_DIR/k8s-cluster/.minikube"

FORWARD_DIR="$ROOT_DIR/k8s-cluster/.minikube/forwards"
mkdir -p "$FORWARD_DIR"

# name:namespace:service:localPort:remotePort
FORWARDS=(
  "app-candidates:candidates:app-candidates:8080:8080"
  "grafana:observability:grafana:3000:80"
  "headlamp:headlamp:headlamp:4466:80"
  "hubble-ui:kube-system:hubble-ui:4040:80"
)

# Belt-and-suspenders: a forward left over from a crashed prior run (or one
# that outlived a force-closed terminal — see the EXIT trap below) holds the
# port with no child of THIS process to catch on Ctrl+C. `lsof` finds it by
# the port itself.
for entry in "${FORWARDS[@]}"; do
  IFS=: read -r _ _ _ localPort _ <<<"$entry"
  stale_pid="$(lsof -ti tcp:"$localPort" -sTCP:LISTEN 2>/dev/null || true)"
  [[ -n "$stale_pid" ]] && kill $stale_pid 2>/dev/null || true
done

CHILD_PIDS=()
cleanup() {
  echo "==> Stopping forwards"
  for pid in "${CHILD_PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  rm -f "$FORWARD_DIR"/*.pid
}
trap cleanup EXIT INT TERM

echo "==> Starting forwards"
for entry in "${FORWARDS[@]}"; do
  IFS=: read -r name namespace service localPort remotePort <<<"$entry"

  if ! kubectl get service "$service" -n "$namespace" >/dev/null 2>&1; then
    echo "    skipping $name — service $namespace/$service not found (not deployed yet?)"
    continue
  fi

  kubectl port-forward -n "$namespace" "service/$service" "$localPort:$remotePort" \
    >"$FORWARD_DIR/$name.log" 2>&1 &
  pid=$!
  CHILD_PIDS+=("$pid")
  echo "$pid" >"$FORWARD_DIR/$name.pid"
done

sleep 2
echo "==> Ready (Ctrl+C to stop all forwards)"
for entry in "${FORWARDS[@]}"; do
  IFS=: read -r name _ _ localPort _ <<<"$entry"
  pidfile="$FORWARD_DIR/$name.pid"
  if [[ -f "$pidfile" ]] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    echo "    http://localhost:$localPort  ($name)"
  else
    echo "    $name FAILED to start — see $FORWARD_DIR/$name.log"
  fi
done

wait
