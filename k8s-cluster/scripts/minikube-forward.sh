#!/usr/bin/env bash
# Host access without `minikube tunnel`'s sudo prompt: on macOS, minikube's
# docker driver puts the cluster on a network (192.168.49.0/24 by default)
# that Docker Desktop's VM doesn't route to the host, so a MetalLB
# LoadBalancer IP is only reachable from inside that network (e.g. `docker
# exec minikube curl ...`), not from a Mac terminal/browser directly.
# `kubectl port-forward` tunnels through the apiserver instead, which IS
# reachable from the host — no sudo needed.
#
# Runs every forward IN THE BACKGROUND and returns immediately — this is the
# last step `make minikube-rebuild-all`/`minikube-deploy` chain themselves,
# and neither should hold the terminal hostage for as long as you want
# access. PIDs/logs live under k8s-cluster/.minikube/forwards/ (already
# gitignored, see minikube-start.sh for why cluster state stays project-
# local). `make minikube-stop`/`minikube-delete` clean these up
# (minikube-unforward.sh, chained as their first step) — a forward pointed
# at a cluster that's about to stop or disappear would otherwise just spin
# retrying in the background. Re-running this script kills and restarts
# every forward first — safe after a redeploy, and avoids "address already
# in use" from a stale forward left over from a previous run.
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

"$ROOT_DIR/k8s-cluster/scripts/minikube-unforward.sh"

# `kubectl port-forward service/X` resolves to a backing pod once, at the
# moment it starts and exits if none is Ready - right after `minikube start`
# resumes a stopped cluster (or, worse, does a full VM restart - "Pulling
# base image" in its output instead of "Restarting existing docker
# container" - which cascades into most of the observability stack
# crashlooping too, not just these 4 services), how long that takes to
# settle is genuinely variable (found live 2026-09-21: ~2min one time,
# >3min the next for the exact same services) - no fixed timeout picked
# here would reliably cover it without either failing early or making a fast
# resume wait needlessly. So don't wait/time out at all: each forward is a
# small self-restarting loop, backgrounded once and left alone - it keeps
# retrying every 2s forever, however long the cluster actually takes, no
# second `make minikube-forward` needed. A service that's genuinely never
# deployed just retries forever too (rare/harmless - `make minikube-stop`/
# `minikube-delete` clean it up via minikube-unforward.sh regardless).
echo "==> Starting forwards (self-restarting until each connects, however long that takes)"
for entry in "${FORWARDS[@]}"; do
  IFS=: read -r name namespace service localPort remotePort <<<"$entry"

  # Belt-and-suspenders beyond minikube-unforward.sh above: a forward left
  # running from before this script existed (or from a crashed prior run
  # whose pidfile got lost) holds the port with no pidfile of ours to find
  # it — `lsof` catches that by the port itself, not by remembering the pid.
  stale_pid="$(lsof -ti tcp:"$localPort" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$stale_pid" ]]; then
    kill $stale_pid 2>/dev/null || true
    sleep 1
  fi

  nohup bash -c '
    while true; do
      kubectl port-forward -n "$1" "service/$2" "$3:$4"
      sleep 2
    done
  ' _ "$namespace" "$service" "$localPort" "$remotePort" \
    >"$FORWARD_DIR/$name.log" 2>&1 &
  disown
  echo $! >"$FORWARD_DIR/$name.pid"
done

echo "==> Verifying (up to 30s total, checked in parallel; a service still settling keeps retrying in the background after this)"
for entry in "${FORWARDS[@]}"; do
  IFS=: read -r name _ _ localPort _ <<<"$entry"
  varname="wait_pid_${name//-/_}"
  (
    for _ in $(seq 1 15); do
      if [[ -n "$(lsof -ti tcp:"$localPort" -sTCP:LISTEN 2>/dev/null || true)" ]]; then
        exit 0
      fi
      sleep 2
    done
    exit 1
  ) &
  eval "$varname=$!"
done
for entry in "${FORWARDS[@]}"; do
  IFS=: read -r name _ _ localPort _ <<<"$entry"
  wait_pid_var="wait_pid_${name//-/_}"
  pidfile="$FORWARD_DIR/$name.pid"
  if wait "${!wait_pid_var}"; then
    echo "    http://localhost:$localPort  ($name, pid $(cat "$pidfile"))"
  else
    echo "    $name still connecting — see $FORWARD_DIR/$name.log (will keep retrying in the background)"
  fi
done
echo "==> Running in the background. 'make minikube-forward' to restart, 'make minikube-stop'/'minikube-delete' to stop."
