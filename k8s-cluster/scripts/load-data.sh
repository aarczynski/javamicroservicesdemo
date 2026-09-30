#!/usr/bin/env bash
# Replaces the cluster's data with a freshly generated data-generator dataset
# and syncs load-background's candidate IDs to it. Also used for minikube via
# minikube-load-data.sh, which only points KUBECONFIG elsewhere.
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
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export KUBECONFIG="${KUBECONFIG:-$ROOT_DIR/k8s-cluster/kubeconfig}"

first_pod() {
  local namespace="$1" pod_label="$2"
  kubectl get pod -n "$namespace" -l "app=$pod_label" -o jsonpath='{.items[0].metadata.name}'
}

import_file() {
  local namespace="$1" pod="$2" db="$3" file="$4"
  kubectl cp "$file" "$namespace/$pod:/tmp/$(basename "$file")"
  kubectl exec -n "$namespace" "$pod" -- \
    psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -q -f "/tmp/$(basename "$file")"
}

reload_database() {
  local namespace="$1" pod_label="$2" db="$3" root_tables="$4" demo_data_file="$5"
  shift 5
  local files=("$@")
  local pod
  pod=$(first_pod "$namespace" "$pod_label")

  # Root tables, not every table: TRUNCATE ... CASCADE only follows foreign
  # keys pointing at the truncated table, so truncating the roots clears the
  # whole graph. Truncating only job_offer would leave company/skill (its
  # parents) behind and the import would hit duplicate keys on their names.
  echo "==> Truncating $db ($root_tables, CASCADE)"
  kubectl exec -n "$namespace" "$pod" -- \
    psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -c "TRUNCATE TABLE $root_tables CASCADE"

  echo "==> Restoring Flyway demo-data seed ($demo_data_file)"
  import_file "$namespace" "$pod" "$db" "$demo_data_file"

  echo "==> Importing generated data into $db"
  for file in "${files[@]}"; do
    echo "    $file"
    import_file "$namespace" "$pod" "$db" "$file"
  done
}

sync_load_background() {
  local file="$1"
  echo "==> Syncing $file into load-background (so ambient load hits real candidate IDs)"
  local pod
  pod=$(first_pod load-background load-background)
  kubectl cp "$file" "load-background/$pod:/data/01-candidates.sql"
  kubectl rollout restart deployment/load-background -n load-background
  kubectl rollout status deployment/load-background -n load-background --timeout=60s
}

echo "==> Generating fresh SQL files (make generate-data)"
(cd "$ROOT_DIR" && make generate-data)

OUT="$ROOT_DIR/data-generator/output"

reload_database candidates postgres-candidates app-candidates-db "candidate" \
  "$ROOT_DIR/app-candidates/src/main/resources/db/migration/postgres/V1_1__demo-data.sql" \
  "$OUT/candidates/01-candidates.sql" \
  "$OUT/candidates/02-candidate-preferred-employment-types.sql" \
  "$OUT/candidates/03-candidate-skills.sql"
sync_load_background "$OUT/candidates/01-candidates.sql"

reload_database job-offers postgres-job-offers app-job-offers-db "company, skill" \
  "$ROOT_DIR/app-job-offers/src/main/resources/db/migration/postgres/V1_1__demo-data.sql" \
  "$OUT/job-offers/01-companies.sql" \
  "$OUT/job-offers/02-skills.sql" \
  "$OUT/job-offers/03-job-offers.sql" \
  "$OUT/job-offers/04-job-offer-employment-types.sql" \
  "$OUT/job-offers/05-job-offer-skills.sql"

echo "==> Load complete. Load test: candidatesDataFile=$OUT/candidates/01-candidates.sql"
