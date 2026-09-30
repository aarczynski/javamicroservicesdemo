#!/usr/bin/env bash
# Replaces Docker Compose's Postgres data with a freshly generated
# data-generator dataset. Same steps as k8s-cluster/scripts/load-data.sh
# (the RPi cluster's and minikube's loader), just via `docker exec` instead
# of `kubectl exec`/`kubectl cp`.
#
# Always a full reload: data-generator produces new random UUIDs on every
# run, so generating without importing would leave the load test's
# candidatesDataFile pointing at IDs that don't exist in Postgres.
#
# Flyway stays intact: the schema (V1_0) and flyway_schema_history are never
# touched, only the business tables are truncated. The demo-data seed (V1_1)
# that TRUNCATE wipes is restored by re-executing the migration file itself —
# Flyway won't re-run it (already marked applied), and the file is unchanged,
# so its checksum still validates on the next app start.
#
# load-background needs no copy step here, unlike the k8s version:
# compose.yml bind-mounts data-generator/output/candidates/01-candidates.sql
# straight into the container, so it only needs a restart to re-read it
# (k6's SharedArray reads the file once at startup).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

psql_in() {
  local container="$1" db="$2"
  shift 2
  docker exec -i "$container" psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -q "$@"
}

reload_database() {
  local container="$1" db="$2" root_tables="$3" demo_data_file="$4"
  shift 4
  local files=("$@")

  # Root tables, not every table: TRUNCATE ... CASCADE only follows foreign
  # keys pointing at the truncated table, so truncating the roots clears the
  # whole graph. Truncating only job_offer would leave company/skill (its
  # parents) behind and the import would hit duplicate keys on their names.
  echo "==> Truncating $db ($root_tables, CASCADE)"
  psql_in "$container" "$db" -c "TRUNCATE TABLE $root_tables CASCADE"

  echo "==> Restoring Flyway demo-data seed ($demo_data_file)"
  psql_in "$container" "$db" <"$demo_data_file"

  echo "==> Importing generated data into $db"
  for file in "${files[@]}"; do
    echo "    $file"
    psql_in "$container" "$db" <"$file"
  done
}

echo "==> Generating fresh SQL files (make generate-data)"
(cd "$ROOT_DIR" && make generate-data)

OUT="$ROOT_DIR/data-generator/output"

reload_database app-candidates-db app-candidates-db "candidate" \
  "$ROOT_DIR/app-candidates/src/main/resources/db/migration/postgres/V1_1__demo-data.sql" \
  "$OUT/candidates/01-candidates.sql" \
  "$OUT/candidates/02-candidate-preferred-employment-types.sql" \
  "$OUT/candidates/03-candidate-skills.sql"
echo "==> Restarting load-background so it re-reads the fresh candidates file"
(cd "$ROOT_DIR" && docker compose restart load-background)

reload_database app-job-offers-db app-job-offers-db "company, skill" \
  "$ROOT_DIR/app-job-offers/src/main/resources/db/migration/postgres/V1_1__demo-data.sql" \
  "$OUT/job-offers/01-companies.sql" \
  "$OUT/job-offers/02-skills.sql" \
  "$OUT/job-offers/03-job-offers.sql" \
  "$OUT/job-offers/04-job-offer-employment-types.sql" \
  "$OUT/job-offers/05-job-offer-skills.sql"

echo "==> Load complete. Load test: candidatesDataFile=$OUT/candidates/01-candidates.sql"
