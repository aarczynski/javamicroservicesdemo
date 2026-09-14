#!/usr/bin/env bash
# Imports data-generator's SQL output into the cluster's Postgres instances.
#
# Safe by default: skips a database that already has rows, so re-running
# after a rebuild (or by accident) never tries to double-insert and fail on
# a duplicate key. Pass --force to truncate first and reload unconditionally
# (e.g. after regenerating a bigger dataset with `make generate-data`).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export KUBECONFIG="${KUBECONFIG:-$ROOT_DIR/k8s-cluster/kubeconfig}"
FORCE=false
[[ "${1:-}" == "--force" ]] && FORCE=true

import_file() {
  local namespace="$1" pod_label="$2" db="$3" file="$4"
  local pod
  pod=$(kubectl get pod -n "$namespace" -l "app=$pod_label" -o jsonpath='{.items[0].metadata.name}')
  kubectl cp "$file" "$namespace/$pod:/tmp/$(basename "$file")"
  kubectl exec -n "$namespace" "$pod" -- \
    psql -U postgres -d "$db" -f "/tmp/$(basename "$file")"
}

load_database() {
  local namespace="$1" pod_label="$2" db="$3" check_table="$4"
  shift 4
  local files=("$@")

  local pod
  pod=$(kubectl get pod -n "$namespace" -l "app=$pod_label" -o jsonpath='{.items[0].metadata.name}')
  local row_count
  row_count=$(kubectl exec -n "$namespace" "$pod" -- \
    psql -U postgres -d "$db" -tAc "SELECT COUNT(*) FROM $check_table")

  # Flyway's baseline migration seeds a handful of fixed demo rows on every
  # fresh deploy (see README) - a plain "> 0" check would treat that as
  # "already bulk-loaded" and skip forever. Bulk data is thousands of rows,
  # so use a threshold well above the demo seed count instead.
  if [[ "$row_count" -gt 100 ]]; then
    if [[ "$FORCE" == "true" ]]; then
      echo "==> $db already has $row_count rows in $check_table — truncating (--force)"
      kubectl exec -n "$namespace" "$pod" -- \
        psql -U postgres -d "$db" -c "TRUNCATE TABLE $check_table CASCADE"
    else
      echo "==> $db already has $row_count rows in $check_table — skipping (use --force to reload)"
      return 0
    fi
  fi

  echo "==> Importing into $db"
  for file in "${files[@]}"; do
    echo "    $file"
    import_file "$namespace" "$pod_label" "$db" "$file"
  done
}

echo "==> Generating fresh SQL files (make generate-data)"
(cd "$ROOT_DIR" && make generate-data)

OUT="$ROOT_DIR/data-generator/output"

load_database candidates postgres-candidates app-candidates-db candidate \
  "$OUT/candidates/01-candidates.sql" \
  "$OUT/candidates/02-candidate-preferred-employment-types.sql" \
  "$OUT/candidates/03-candidate-skills.sql"

load_database job-offers postgres-job-offers app-job-offers-db job_offer \
  "$OUT/job-offers/01-companies.sql" \
  "$OUT/job-offers/02-skills.sql" \
  "$OUT/job-offers/03-job-offers.sql" \
  "$OUT/job-offers/04-job-offer-employment-types.sql" \
  "$OUT/job-offers/05-job-offer-skills.sql"

echo "==> Load complete"
