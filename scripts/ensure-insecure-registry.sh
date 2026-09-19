#!/usr/bin/env bash
# Ensures Docker Desktop trusts the self-hosted k8s image registry
# (k8s-cluster/manifests/registry/registry.yaml, 192.168.10.101:5000) over
# plain HTTP. Without this, `docker push` from k8s-cluster/scripts/deploy.sh
# fails with "server gave HTTP response to HTTPS client" — the registry has
# no TLS cert (LAN-only homelab, same posture as minio.yaml's plaintext
# creds). Wired into `make start`/`make k8s-deploy` so this is never a manual
# step to remember.
#
# Idempotent: merges into the existing ~/.docker/daemon.json (preserving
# whatever else is already in there) and only restarts Docker Desktop the
# one time the entry is actually missing — every later run no-ops without
# touching Docker at all.
set -euo pipefail

REGISTRY="192.168.10.101:5000"
DAEMON_JSON="$HOME/.docker/daemon.json"

CHANGED="$(python3 - "$DAEMON_JSON" "$REGISTRY" <<'PYEOF'
import json
import pathlib
import sys

path, registry = pathlib.Path(sys.argv[1]), sys.argv[2]
cfg = {}
if path.exists() and path.stat().st_size > 0:
    cfg = json.loads(path.read_text())

registries = cfg.setdefault("insecure-registries", [])
if registry in registries:
    print("unchanged")
else:
    registries.append(registry)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(cfg, indent=2) + "\n")
    print("changed")
PYEOF
)"

if [[ "$CHANGED" == "unchanged" ]]; then
  echo "==> Docker already trusts $REGISTRY as insecure — nothing to do"
  exit 0
fi

echo "==> Added $REGISTRY to Docker's insecure-registries — restarting Docker Desktop to apply"
osascript -e 'quit app "Docker"'
open -a Docker

echo "==> Waiting for Docker Desktop to come back up"
until docker info >/dev/null 2>&1; do
  sleep 2
done
echo "==> Docker Desktop is back"
