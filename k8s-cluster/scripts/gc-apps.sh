#!/usr/bin/env bash
# Forces a full GC in every app-candidates / app-job-offers replica, so a
# load test starts with a nearly empty old generation. Serial GC collects the
# old generation only when it fills up — a ~0.3 s stop-the-world pause that,
# on long-running pods, can land in the middle of a measured run. Running it
# here, before the test, keeps that pause out of the results.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export KUBECONFIG="${KUBECONFIG:-$ROOT_DIR/k8s-cluster/kubeconfig}"

for app in candidates job-offers; do
  for pod in $(kubectl get pods -n "$app" -l "app=app-$app" -o name); do
    echo "==> Full GC in $app/${pod#pod/}"
    kubectl exec -n "$app" "$pod" -- jcmd 1 GC.run
  done
done
