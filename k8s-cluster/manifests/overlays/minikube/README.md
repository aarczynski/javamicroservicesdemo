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
| `kustomization.yaml` | Assembles `candidates` + `job-offers` namespace/postgres/app manifests (referenced directly from `../../candidates/` and `../../job-offers/`, unmodified) with this overlay's `gateway.yaml`, and JSON6902-patches `nodeSelector`/`tolerations` off both Postgres Deployments — minikube's single node carries none of the RPi cluster's `role=database` taint. |

`manifests/metallb/values-metallb.yaml` (the MetalLB Helm chart values) is
used as-is: `speaker.tolerations` for `role=database/observability/platform`
are harmless no-ops on a node with no matching taints.

## Phase 2: observability stack

`minikube-bootstrap-observability.sh` (`make minikube-bootstrap-observability`,
or just `make minikube-up` for everything in one shot) adds Kafka (Strimzi),
MinIO, Tempo-distributed, Loki, Prometheus, Grafana, otel-collector, Alloy,
Headlamp, metrics-server — the same `nodeSelector`/`tolerations`-stripping
pattern as Phase 1, one `values-*.yaml` override per Helm release, plus:

| File | Overrides |
|---|---|
| `values-loki.yaml`, `values-prometheus.yaml`, `values-otel-collector.yaml`, `values-strimzi-operator.yaml`, `values-metrics-server.yaml` | Drop `nodeSelector`/`tolerations` only — nothing else differs from production. |
| `values-tempo-distributed.yaml` | Same, but for all 7 components (`backendScheduler`, `backendWorker`, `blockBuilder`, `liveStore`, `distributor`, `querier`, `queryFrontend`) — production pins each one to `k8s-rpi-observability-1` or `-2` to balance load across two physical nodes; nothing to balance on one minikube node. |
| `values-grafana.yaml`, `values-headlamp.yaml` | Drop `nodeSelector`/`tolerations` and repoint the pinned `192.168.10.19x` LoadBalancer IP at this overlay's MetalLB pool (`.148` Grafana, `.149` Headlamp — see `metallb-ip-pools.yaml`). |
| `values-alloy.yaml` | Adds `alloy.mounts.dockercontainers: true`. Not a nodeSelector issue — this is a real behavioral difference from production. minikube here runs `--container-runtime=docker` (required so `--cni=false` can work — see `minikube-start.sh`), so `/var/log/pods/.../*.log` is a **symlink** into `/var/lib/docker/containers/<id>/<id>-json.log` (the legacy dockershim log layout), not a real file. Without this mount, Alloy can `ls` the symlink but every `stat`/read of it fails ("no such file or directory" — the target is outside its mount namespace) and nothing reaches Loki. The chart's own values file already documents this exact tradeoff; production (real containerd, no dockershim) correctly leaves it off. |
| `observability/kustomization.yaml` | Same base-plus-patches pattern as `../kustomization.yaml`, for the plain manifests: strips Kafka's `KafkaNodePool` `affinity`/`tolerations` and MinIO's Deployment+Job `nodeSelector`/`tolerations`. Also remaps `minio/minio` and `minio/mc` to `quay.io/minio/minio` / `quay.io/minio/mc` (same exact release tags) via kustomize's `images:` transformer — as of 2026-09-15 both `minio/minio` and `minio/mc` **Docker Hub repositories are gone entirely** ("object not found" via the Hub API, not just the pinned tag), confirmed independently of this overlay. `manifests/minio/minio.yaml`'s own comment already predicted this direction back on 2026-09-03; it's now landed. The production RPi cluster likely has these cached locally on whichever node last pulled them (see that file's `nodeSelector` comment about `observability-1` vs `-2`) but will hit the same wall on the next pull to a node without that cache — worth fixing in `minio.yaml` itself, separately from this overlay, next time someone's touching that file. |

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

`make minikube-up` ends by running `minikube-forward.sh`, which starts
`kubectl port-forward`s to every Service with a normal selector and then
**blocks in the foreground** — same shape as `make start`'s `docker compose
up`, Ctrl+C stops all of them together, not a second command:

* `http://localhost:8080` — `app-candidates` (`service/app-candidates`)
* `http://localhost:3000` — Grafana, anonymous admin (`service/grafana`)
* `http://localhost:4466` — Headlamp, no login (`service/headlamp`)
* `http://localhost:4040` — Hubble UI, live network flows (`service/hubble-ui` in `kube-system`)

Ctrl+C only stops the forwards — the cluster itself keeps running. Re-run
`make minikube-forward` any time to bring host access back (also cleans up a
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
