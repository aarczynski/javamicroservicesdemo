#!/usr/bin/env bash
# Kills every background `kubectl port-forward` started by minikube-forward.sh
# (PID files under k8s-cluster/.minikube/forwards/). Called by minikube-
# forward.sh itself (fresh restart) and by `make minikube-stop`/`minikube-
# delete` (a forward left pointed at a stopped/deleted cluster just spins
# retrying and erroring in the background otherwise).
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FORWARD_DIR="$ROOT_DIR/k8s-cluster/.minikube/forwards"

[[ -d "$FORWARD_DIR" ]] || exit 0

for pidfile in "$FORWARD_DIR"/*.pid; do
  [[ -e "$pidfile" ]] || continue
  pid="$(cat "$pidfile")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null
    echo "==> Stopped forward $(basename "$pidfile" .pid) (pid $pid)"
  fi
  rm -f "$pidfile"
done
