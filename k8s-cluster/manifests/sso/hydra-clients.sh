#!/bin/sh
# Registers the OAuth2 clients in Hydra (admin API at $ORY_SDK_URL). Idempotent: an existing client is left as is.
# Used by compose.yml (hydra-clients) and the hydra-clients Job in hydra.yaml (ConfigMap from kustomization.yaml).
#
# app-candidates  - gets client_credentials tokens with the job-offers.search scope to call app-job-offers.
# app-job-offers  - the credentials app-job-offers sends to the introspection endpoint. Hydra's admin API does not
#                   check them; they are registered so the configured identity exists.
set -eu

register_client() {
  client_id="$1"
  client_secret="$2"
  scope="$3"
  if hydra get oauth2-client "$client_id" >/dev/null 2>&1; then
    echo "OAuth2 client $client_id already registered"
    return
  fi
  hydra create oauth2-client \
    --id "$client_id" \
    --secret "$client_secret" \
    --grant-type client_credentials \
    --scope "$scope" \
    --format json >/dev/null
  echo "OAuth2 client $client_id registered"
}

register_client app-candidates app-candidates-secret job-offers.search
register_client app-job-offers app-job-offers-secret introspect
