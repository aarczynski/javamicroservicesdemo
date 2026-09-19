# minikube overlay

Local-parity deployment target for a single-node `minikube` cluster on a dev
laptop, standing in for the 12-node RPi cluster described in
`.claude/handoff-k8s-rpi-cluster.md`. Driven entirely by `make minikube-*`
targets (see repo root `Makefile`).

## Why this exists, not just `kubectl apply -f k8s-cluster/manifests/`

The production manifests are wired to the physical cluster's topology:
`nodeSelector: kubernetes.io/hostname: k8s-rpi-*`, MetalLB IPs on the
`192.168.10.0/24` LAN, and Cilium's `k8sServiceHost` pointing at the
control-plane's LAN IP. None of that resolves on a single generic minikube
node with its own Docker-network IP range (`192.168.49.0/24`). This
directory holds the minimum set of overrides needed to run the same
components (Cilium CNI + Gateway API, MetalLB L2, apps, Postgres) without
touching the production files.

## What's overridden and why

| File | Overrides |
|---|---|
| `cilium-values.yaml` | `k8sServiceHost`/`k8sServicePort` → minikube's node IP:8443 (found via `docker exec minikube kube-apiserver --secure-port`, minikube's docker-driver node advertises on `192.168.49.2`, not the host-side `127.0.0.1:<random>` tunnel `kubectl cluster-info` shows). Drops Hubble relay/UI `nodeSelector`/`tolerations` (no tainted nodes here) and the pinned `192.168.10.197` LoadBalancer IP (let MetalLB auto-assign from `default-pool` instead). |
| `metallb-ip-pools.yaml` | Replaces `manifests/metallb/ip-address-pool.yaml`'s `192.168.10.x` ranges with `192.168.49.100-150` (`default-pool`) and `192.168.49.90/32` (`gateway-pool`), both inside minikube's docker network (`192.168.49.0/24`, gateway `.1`, node `.2`). |
| `metallb-l2-advertisement.yaml` | Same as `manifests/metallb/l2-advertisement.yaml` but drops the `nodeSelectors` restricting the Gateway's announcement to `k8s-rpi-platform-1/2` — there's only one node here. |
| `gateway.yaml` | Same as `manifests/candidates/gateway.yaml` but `metallb.io/loadBalancerIPs: "192.168.49.90"` instead of `192.168.10.100`. |
| `kustomization.yaml` | Assembles `candidates` + `job-offers` + `load-background` namespace/postgres/app manifests (referenced directly from `../../candidates/`, `../../job-offers/`, `../../load-background/`, unmodified) with this overlay's `gateway.yaml`, and JSON6902-patches `nodeSelector`/`tolerations` off both Postgres Deployments and `load-background` — minikube's single node carries none of the RPi cluster's taints. Also remaps the three app images from `192.168.10.101:5000/...` (the RPi cluster's self-hosted registry, unreachable from here) to plain `:local` tags with no registry at all, via kustomize's `images:` transformer — `make minikube-image` (`k8s-cluster/scripts/minikube-image.sh`) builds them and loads them straight into minikube's own image cache. Deliberately not ghcr.io either (that's what production used before its own switch to the local registry) — minikube is meant to work regardless of network, home LAN or not, even offline. `load-background`'s `/data` PVC starts empty by default (`minikube-deploy`/`minikube-rebuild-all` don't run data-generator, deliberately — it's slow) — it runs fine and generates real ambient RPS, just against candidate UUIDs that mostly 404, same graceful-degradation behavior documented in `load-background/app.yaml` for when the RPi cluster hasn't run `load-data.sh` yet. Run `make minikube-load-data` (or `minikube-reload-data` to force) when you actually want real data — `k8s-cluster/scripts/minikube-load-data.sh` mirrors `load-data.sh` exactly, just pointed at minikube. |

`manifests/metallb/values-metallb.yaml` (the MetalLB Helm chart values) is
used as-is: `speaker.tolerations` for `role=database/observability/platform`
are harmless no-ops on a node with no matching taints.

## Observability stack, Headlamp, metrics-server

The same `minikube-bootstrap.sh` script (`make minikube-bootstrap`, chained
into `make minikube-rebuild-all`) also installs Kafka (Strimzi), MinIO,
Tempo-distributed, Loki, Prometheus, Grafana, otel-collector, Alloy,
Headlamp, metrics-server, right after Cilium/MetalLB/storage — same shape as
`bootstrap.sh` for the RPi cluster, one script start to finish. Same
`nodeSelector`/`tolerations`-stripping pattern throughout, one `values-*.yaml`
override per Helm release, plus:

| File | Overrides |
|---|---|
| `values-loki.yaml`, `values-prometheus.yaml`, `values-otel-collector.yaml`, `values-strimzi-operator.yaml`, `values-metrics-server.yaml` | Drop `nodeSelector`/`tolerations` only — nothing else differs from production. |
| `values-tempo-distributed.yaml` | Same, but for all 7 components (`backendScheduler`, `backendWorker`, `blockBuilder`, `liveStore`, `distributor`, `querier`, `queryFrontend`) — production pins each one to `k8s-rpi-observability-1` or `-2` to balance load across two physical nodes; nothing to balance on one minikube node. |
| `values-grafana.yaml`, `values-headlamp.yaml` | Drop `nodeSelector`/`tolerations` and repoint the pinned `192.168.10.19x` LoadBalancer IP at this overlay's MetalLB pool (`.148` Grafana, `.149` Headlamp — see `metallb-ip-pools.yaml`). |
| `values-alloy.yaml` | Adds `alloy.mounts.dockercontainers: true`. Not a nodeSelector issue — this is a real behavioral difference from production. minikube here runs `--container-runtime=docker` (required so `--cni=false` can work — see `minikube-start.sh`), so `/var/log/pods/.../*.log` is a **symlink** into `/var/lib/docker/containers/<id>/<id>-json.log` (the legacy dockershim log layout), not a real file. Without this mount, Alloy can `ls` the symlink but every `stat`/read of it fails ("no such file or directory" — the target is outside its mount namespace) and nothing reaches Loki. The chart's own values file already documents this exact tradeoff; production (real containerd, no dockershim) correctly leaves it off. |
| `observability/kustomization.yaml` | Same base-plus-patches pattern as `../kustomization.yaml`, for the plain manifests: strips Kafka's `KafkaNodePool` `affinity`/`tolerations` and MinIO's Deployment+Job `nodeSelector`/`tolerations`. Used to also remap `minio/minio`/`minio/mc` to `quay.io/minio/minio`/`quay.io/minio/mc` here (Docker Hub had pulled both repos entirely) — that fix landed directly in `manifests/minio/minio.yaml` itself instead, so this overlay no longer needs an `images:` override for it. |

Base `kustomization.yaml` files were added to `manifests/observability/`,
`manifests/kafka/`, and `manifests/minio/` (listing the same files their
respective scripts already `kubectl apply -f` directly) purely so this
overlay's kustomization can reference them as directories — kustomize
refuses to load a file from outside its own root without one. No existing
file's content changed.

Verified end-to-end after standing this up (2026-09-15): sent requests
through the Gateway, confirmed Prometheus has `http_server_request_duration_seconds_count`
and `jvm_memory_used_bytes` for both apps, Loki has structured logs carrying
`trace_id`/`span_id`, and the exact `trace_id` from a Loki log line resolves
to a real trace in Tempo tagged `service.name=app-candidates` — the whole
Alloy → Loki and otel-collector → Kafka → Tempo pipelines work, not just
"pods are Running".

### Reaching things from the Mac

`make minikube-rebuild-all` and `make minikube-deploy` both end by running
`minikube-forward.sh`, which starts `kubectl port-forward`s to every Service
with a normal selector **in the background** and returns immediately — the
terminal stays free, no second command needed:

* `http://localhost:8080` — `app-candidates` (`service/app-candidates`)
* `http://localhost:3000` — Grafana, anonymous admin (`service/grafana`)
* `http://localhost:4466` — Headlamp, no login (`service/headlamp`)
* `http://localhost:4040` — Hubble UI, live network flows (`service/hubble-ui` in `kube-system`)

Re-run `make minikube-forward` any time to restart them (also cleans up a
stale forward left holding a port via `lsof`, not just ones it remembers
starting itself — e.g. one orphaned by a force-closed terminal). `make
minikube-stop`/`minikube-delete` also run `minikube-unforward.sh` first, for
the same reason: a forward pointed at a cluster that's about to stop or
disappear would otherwise just spin retrying in the background.

The Gateway itself (`cilium-gateway-api-gateway`) is the one Service that
**can't** be forwarded this way — its Service has no selector (Cilium steers
traffic to it through its own Envoy/eBPF dataplane, not standard kube
Endpoints), and `kubectl port-forward` requires one to resolve a backing pod.
To exercise the actual Gateway/MetalLB path from the host, use
`make minikube-tunnel` instead (routes the real LB IP, needs sudo).
