#!/usr/bin/env bash
# Same as load-data.sh (the RPi cluster's data loader) — imports data-
# generator's SQL output into Postgres and syncs load-background's candidate
# IDs — just pointed at the minikube cluster instead. Not part of
# minikube-deploy/minikube-rebuild-all: those stay fast and don't touch
# data-generator at all; this is the separate, explicit "I actually want
# realistic data on minikube too" step, same relationship load-data.sh has
# to bootstrap.sh/k8s-rebuild-all on the RPi cluster.
#
# Safe by default: skips a database that already has rows, so re-running
# never tries to double-insert and fail on a duplicate key. Pass --force to
# truncate first and reload unconditionally (e.g. after regenerating a
# bigger dataset with `make generate-data`).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Forced, not `${VAR:-default}` — see minikube-start.sh for why an inherited
# ambient KUBECONFIG must never leak in here.
export KUBECONFIG="$ROOT_DIR/k8s-cluster/kubeconfig-minikube"
export MINIKUBE_HOME="$ROOT_DIR/k8s-cluster/.minikube"
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
  local namespace="$1" pod_label="$2" db="$3" check_table="$4" truncate_tables="$5"
  shift 5
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
      # truncate_tables, not just check_table: TRUNCATE ... CASCADE only
      # cascades to tables that reference the truncated one, not the other
      # way round. job_offer is a fine table to COUNT (it's the thing we
      # actually care whether is bulk-loaded), but TRUNCATEing only
      # job_offer CASCADE leaves company/skill (its parents, not children)
      # untouched - the next import then hits duplicate key errors on
      # company.name/skill.name. Truncating every root table explicitly
      # (with CASCADE) clears the whole graph regardless of direction.
      # Found live 2026-09-19 testing scripts/load-data.sh (Compose's
      # equivalent, identical logic) — same bug, never exercised here.
      for t in $truncate_tables; do
        kubectl exec -n "$namespace" "$pod" -- \
          psql -U postgres -d "$db" -c "TRUNCATE TABLE $t CASCADE"
      done
    else
      echo "==> $db already has $row_count rows in $check_table — skipping (use --force to reload)"
      return 1
    fi
  fi

  echo "==> Importing into $db"
  for file in "${files[@]}"; do
    echo "    $file"
    import_file "$namespace" "$pod_label" "$db" "$file"
  done
}

sync_load_background() {
  local file="$1"
  echo "==> Syncing $file into load-background (so ambient load hits real candidate IDs)"
  local pod
  pod=$(kubectl get pod -n load-background -l app=load-background -o jsonpath='{.items[0].metadata.name}')
  kubectl cp "$file" "load-background/$pod:/data/01-candidates.sql"
  kubectl rollout restart deployment/load-background -n load-background
  kubectl rollout status deployment/load-background -n load-background --timeout=60s
}

echo "==> Generating fresh SQL files (make generate-data)"
(cd "$ROOT_DIR" && make generate-data)

OUT="$ROOT_DIR/data-generator/output"

if load_database candidates postgres-candidates app-candidates-db candidate candidate \
  "$OUT/candidates/01-candidates.sql" \
  "$OUT/candidates/02-candidate-preferred-employment-types.sql" \
  "$OUT/candidates/03-candidate-skills.sql"; then
  # Only sync when candidates was actually (re)imported - the file we'd sync
  # otherwise is freshly generated with different random UUIDs than whatever
  # is still sitting in Postgres from the skipped import, which would just
  # reintroduce the exact "load-background hits IDs that don't exist" bug.
  sync_load_background "$OUT/candidates/01-candidates.sql"
fi

# truncate_tables is "company skill", not "job_offer" - both are roots
# job_offer/job_offer_skill hang off of, see the comment in load_database.
load_database job-offers postgres-job-offers app-job-offers-db job_offer "company skill" \
  "$OUT/job-offers/01-companies.sql" \
  "$OUT/job-offers/02-skills.sql" \
  "$OUT/job-offers/03-job-offers.sql" \
  "$OUT/job-offers/04-job-offer-employment-types.sql" \
  "$OUT/job-offers/05-job-offer-skills.sql" || true

echo "==> Load complete"
