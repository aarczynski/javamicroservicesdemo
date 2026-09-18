#!/usr/bin/env bash
# Imports data-generator's SQL output into Docker Compose's Postgres
# instances. Mirrors k8s-cluster/scripts/load-data.sh (the RPi cluster's
# loader) — same row-count guard, same --force truncate-and-reload — just
# via `docker exec` instead of `kubectl exec`/`kubectl cp`.
#
# load-background needs no separate sync step here, unlike the k8s/minikube
# versions: compose.yml already bind-mounts data-generator/output/candidates/
# 01-candidates.sql straight into the container (`load-background`'s
# `volumes:`), so a fresh `make generate-data` is already visible on disk —
# it only needs a restart to pick it up (k6's SharedArray reads the file
# once at startup, same as everywhere else this data file is used).
#
# Safe by default: skips a database that already has rows, so re-running
# never tries to double-insert and fail on a duplicate key. Pass --force to
# truncate first and reload unconditionally (e.g. after regenerating a
# bigger dataset with `make generate-data`).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FORCE=false
[[ "${1:-}" == "--force" ]] && FORCE=true

import_file() {
  local container="$1" db="$2" file="$3"
  docker exec -i "$container" psql -U postgres -d "$db" <"$file"
}

load_database() {
  local container="$1" db="$2" check_table="$3" truncate_tables="$4"
  shift 4
  local files=("$@")

  local row_count
  row_count=$(docker exec "$container" psql -U postgres -d "$db" -tAc "SELECT COUNT(*) FROM $check_table")

  # Flyway's baseline migration seeds a handful of fixed demo rows on every
  # fresh start (see README) - a plain "> 0" check would treat that as
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
      for t in $truncate_tables; do
        docker exec "$container" psql -U postgres -d "$db" -c "TRUNCATE TABLE $t CASCADE"
      done
    else
      echo "==> $db already has $row_count rows in $check_table — skipping (use --force to reload)"
      return 1
    fi
  fi

  echo "==> Importing into $db"
  for file in "${files[@]}"; do
    echo "    $file"
    import_file "$container" "$db" "$file"
  done
}

echo "==> Generating fresh SQL files (make generate-data)"
(cd "$ROOT_DIR" && make generate-data)

OUT="$ROOT_DIR/data-generator/output"

if load_database app-candidates-db app-candidates-db candidate candidate \
  "$OUT/candidates/01-candidates.sql" \
  "$OUT/candidates/02-candidate-preferred-employment-types.sql" \
  "$OUT/candidates/03-candidate-skills.sql"; then
  # Only restart when candidates was actually (re)imported - restarting on
  # a skip would just re-read the same file load-background already has
  # loaded, for no benefit and a few seconds of dropped ambient traffic.
  echo "==> Restarting load-background so it re-reads the fresh candidates file"
  (cd "$ROOT_DIR" && docker compose restart load-background)
fi

# truncate_tables is "company skill", not "job_offer" - both are roots
# job_offer/job_offer_skill hang off of, see the comment in load_database.
load_database app-job-offers-db app-job-offers-db job_offer "company skill" \
  "$OUT/job-offers/01-companies.sql" \
  "$OUT/job-offers/02-skills.sql" \
  "$OUT/job-offers/03-job-offers.sql" \
  "$OUT/job-offers/04-job-offer-employment-types.sql" \
  "$OUT/job-offers/05-job-offer-skills.sql" || true

echo "==> Load complete"
