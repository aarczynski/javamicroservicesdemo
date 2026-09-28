# RPS scaling journey — `app-candidates` / `app-job-offers`

History of what actually moved the needle on sustained throughput against the physical RPi5 cluster
(`http://192.168.10.100`), in the order the fixes landed. Session-by-session narrative and raw incident detail lives
in `.claude/handoff-k8s-rpi-cluster.md`; this file is the distilled, forward-looking summary — update it whenever a
change measurably moves the ceiling, don't let it rot into a second handoff.

## Current state

**2000 RPS clean on a 3x bigger dataset: 150k job offers / 10k companies, p99 211 ms, 0% KO** — 2026-09-28, after
#17 (`app-candidates` loads the candidate with `JOIN FETCH`) and #18 (`shared_buffers=1GB` on `postgres-job-offers`).
**`postgres-job-offers` CPU is now the ceiling** (2.45 of 3 cores at 2000rps): 175k offers still passes with 0% KO
but p99 jumps to ~0.5-1 s, 200k fails. Everything below that line was measured on the old 50k offers / 10k companies
dataset.

**2000 RPS sustained for ~10 minutes, 0% KO, p99 202 ms** — confirmed 2026-09-24 after fix #13 (native
`findCandidateMatchIds`). Two runs, same day:

| Run (Gatling report) | Shape | Requests | p50 / p95 / p99 / max | KO |
|---|---|---|---|---|
| `candidatesimulation-20260924085055671` | 1000 → 2000rps, 2 steps of ~6 min | 1,080,000 | 10 / 40 / 147 / 228 ms | 0 |
| `candidatesimulation-20260924090422665` | 2000rps held ~10 min | 1,320,000 | 12 / 47 / 202 / 321 ms | 0 |

Resources at ~2030rps on the server (2000 test + ambient `load-background`), from Prometheus: `app-job-offers` max
2.16 of 3 cores per replica, CFS throttling ≤3%; `app-candidates` ~1.8 of 3, no throttling; `postgres-job-offers`
~1.6 of 3; `platform-1` (Gateway) ~56% node CPU. Nothing is at its limit yet — the next ceiling is not measured.
Before #13 the same hardware was already on the queueing knee at 1900rps (see #13), so the 2026-09-20 result below
was a lucky run, not a stable ceiling.

**1900 RPS sustained, 0% KO** — confirmed 2026-09-20, after fix #11's `app-candidates` HPA got pinned to a static 3
replicas (see `k8s-cluster/manifests/candidates/hpa.yaml` and the HPA item in `.claude/handoff-k8s-rpi-cluster.md`).
Genuinely sustained, not a short burst: ramped to ~1900-2000rps over ~65s, held there (oscillating 1800-2000) for
~9 minutes straight, ~1min cooldown — 1,140,000 requests total, 0% KO, p50=14ms/p95=90ms/p99=556ms/mean=23ms/
max=1033ms. Response time distribution: 99.998% under 800ms, only 25 requests (0.002%) in the 800-1200ms band, zero
at or above 1200ms. This is the 3rd `app-candidates` replica (worker-5) directly paying for itself — the same
profile shape at 1500rps was the prior ceiling with only 2 replicas (see below). **2200rps is past this ceiling**:
`app-job-offers` (still only 2 replicas, no free worker for a 3rd) hits its own 3-core CPU limit and gets measurably
throttled, degrading `app-candidates`' latency via the synchronous Feign call between them — see fix #11.

1500 RPS sustained, 0% KO — confirmed 2026-09-20, same session as fix #9 below (`maxRps=1500 stepDuration=3m
ramps=1`, 202,500 requests, p50=9ms/p95=25ms/p99=78ms/mean=12ms/max=608ms, ~2 min held near peak — 1s buckets
touched 1600 during the hold) — this was the ceiling **before** the 3rd `app-candidates` replica above. It resolves
what the
[former bottleneck](#former-bottleneck-1500-rps-resolved-2026-09-20) section below used to call the 1500rps ceiling
— the Gateway's unconfigured Envoy circuit breaker tripping once `app-job-offers`
latency degraded under CPU pressure — **without any change beyond what fix #9 already put in place for 1200rps**:
CPU limit `3` on both apps, and one dedicated worker node per app replica (hard `podAntiAffinity`). No new fix
needed a number of its own; see the bottleneck section for why #9 covers this too, not just the 87/13 split it was
built for.

1200 RPS also re-confirmed 2026-09-20, twice: (`maxRps=1200 ramps=1 stepDuration=60s`, 90,000 requests,
p99=359ms/mean=20ms) and again over a full 5-minute sustained hold (`maxRps=1200 stepDuration=5m ramps=1`, 360,000
requests, 0% KO, p99=41ms/mean=10ms; all 12 nodes stayed healthy afterward — no repeat of the `observability-1`
`NodeNotReady` seen at 1500rps under the old, longer-step methodology, see [Methodology
lessons](#methodology-lessons-apply-to-future-rounds)). This had regressed to 5-40% KO for most of 2026-09-20 after
reducing generic workers from 4 to 3 for a 3rd observability node — fixed by fix #9 below (freed a 3rd, unused
database node instead, plus a hard one-pod-per-node anti-affinity so the fix survives future restarts). The
original **1200 RPS sustained, 0% KO, p99=24ms** (`maxRps=1200 ramps=3 stepDuration=3m`, 486,000 requests) result is
preserved below as the historical reference.

## What got us from ~600rps to 1200rps clean

### 1. Async logging (2026-09-11)

Spring Boot's default `ConsoleAppender` is synchronous — every `log.info(...)` call (including the
"received request" log CLAUDE.md requires for business actions) blocks on a single global lock while writing to
stdout. Under load, most of Tomcat's thread pool ended up parked on that one lock (confirmed via `jcmd
Thread.print`: 171/200 threads waiting on the same `ReentrantLock` in `OutputStreamAppender.writeBytes`).

**Fix:** `logback-spring.xml` in both services wraps the console appender in `AsyncAppender`
(`queueSize=1024`, `discardingThreshold=0` so business logs are never silently dropped under load).

**Caveat:** this fix alone made things *worse* at 700rps (85.74% KO, up from 3.39%) — removing the lock let Tomcat
threads reach real work (Feign calls, DB queries) fast enough to exhaust the whole 200-thread pool, and the liveness
probe (sharing that same pool) started timing out, so kubelet killed and restarted the pod under load. Fixed by:

### 2. Separate actuator port (2026-09-11)

`management.server.port: 8081` — health checks get their own Tomcat connector/thread pool, isolated from port 8080
business traffic. Without this, a saturated business thread pool makes the liveness probe fail too, and kubelet
kills a pod that's merely slow, not dead — turning "degraded" into "restart loop and total outage for several
minutes."

Combined result: 700rps stopped restart-looping (0 restarts), 600rps went from p99=254ms (original baseline) to
p99=112ms.

### 3. Two replicas instead of one (2026-09-11)

`replicas: 1 → 2` for both `app-candidates` and `app-job-offers`. Trivial since both are stateless — the scheduler
spread them onto different worker nodes with no anti-affinity needed. This alone doesn't add capacity if the
database behind it is already the bottleneck (see next point), but it's a prerequisite: a single replica is also a
single point of failure, and CPU-bound apps benefit close to linearly from a second core-set.

### 4. `postgres-job-offers` CPU limit 2 → 3 (2026-09-11)

After replicas doubled app throughput, `postgres-job-offers` became the bottleneck (Prometheus showed it pinned at
2.0/2 cores). Bumped to 3 (checked first that `db-2`, the node it's pinned to, had physical headroom).
Result: 750rps clean, 0% KO, p99=43ms — best result up to that point.

### 5. Disable Postgres parallel query workers (2026-09-11)

At 1000rps, `postgres-job-offers` hit its new 3-core ceiling again (2.98/3), with Hikari `pending`=191 on both
`app-job-offers` replicas (default, never-tuned pool of 10). Root cause turned out to be `max_parallel_workers_per_gather`
(Postgres default `2`): the RPi5's cgroup CPU limit is a CFS quota, not a `cpuset`, so the container's `nproc` still
reports all 4 physical cores — Postgres happily plans parallel workers up to that visible core count, so a single
query could burn up to 3x its fair share of CPU under the 3-core limit. A single `EXPLAIN ANALYZE` in isolation
actually looked *faster* with parallelism (35.6ms vs 52.9ms) — the cost only shows up as aggregate CPU-time under
real concurrency, which is why the first pass at diagnosing this looked backwards.

**Fix:** `args: ["-c", "max_parallel_workers_per_gather=0"]` on `postgres-job-offers` (`k8s-cluster/manifests/job-offers/postgres.yaml`).
Traded a bit of single-query latency (600rps p99 387ms → 1051ms) for a large concurrency win (700rps: 72% KO → 1.77% KO).

**Methodology note:** the parallel-workers fix was first tested *together* with a JOIN-FETCH query split (see below,
which was independently a disaster) and the combined result looked uniformly bad (53.35% KO), nearly hiding a real
win. Always isolate one variable at a time under real concurrent load — a clean `EXPLAIN ANALYZE` does not predict
behavior under concurrency, and a bundled test can hide a working fix behind a broken one.

### 6. Fix the cartesian `JOIN FETCH` in `findCandidateMatches` (2026-09-19)

`JobOfferRepository.findCandidateMatches` fetched two collections (`offeredEmploymentTypes`, `skills`) in one query
via `@EntityGraph`. Hibernate turns that into a SQL `JOIN FETCH` on both collections in the same statement — Postgres
returns one row per *(employment type × skill)* combination per offer, not one row per offer, so a query that should
return N offers can return several times N rows, most of them discarded again by Hibernate's in-memory `DISTINCT`.

**A first attempt to fix this (2026-09-11) made things catastrophically worse** (43.87% KO at 600rps, worse still at
53.35% with a bigger connection pool) — later git archaeology (`git fsck --unreachable`) found the likely cause: an
old, never-merged version of this codebase resolved skills via a per-offer query inside the scoring loop (classic
N+1), and the 2026-09-11 attempt probably reintroduced that same pattern instead of a real batch fetch.

**The fix that actually worked (2026-09-19):** split into two queries, batched correctly this time —
- `findCandidateMatchIds(...)` — `SELECT DISTINCT o.id`, no fetch join at all, so there is no row multiplication even
  while filtering on `offeredEmploymentTypes`.
- `findByIdIn(ids)` with `@EntityGraph` for `skills`+`company` only (one collection, no multiplication) —
  `offeredEmploymentTypes` moved to `@BatchSize(size = 100)` instead of a second eager collection, so Hibernate loads
  it with a single extra `WHERE job_offer_id IN (...)` batched query instead of a join.

The key difference from the failed 2026-09-11 attempt: the second query is one `JOIN FETCH` covering *all* matched
offers at once, not one query per offer. Two or three cheap round-trips per request beat one expensive
row-multiplying query; N+1 round-trips do not.

**Result:** 1200rps p99 dropped from 49ms to 24ms; 1500rps went from failing outright to only failing on a different,
downstream bottleneck (see below) instead of Postgres.

**A real regression shipped with this fix, caught in production by a human reading pod logs, not by any existing
test:** moving `offeredEmploymentTypes` to lazy+`@BatchSize` meant the DTO-building code
(`JobOfferService.toMatchDto`) held a reference to an *uninitialized* Hibernate collection — nothing touched its
contents inside the transaction, so Jackson threw `LazyInitializationException` serializing the HTTP response
*after* the transaction (and Hibernate session) had already closed. Fixed with `Set.copyOf(...)` in `toMatchDto`
(forces initialization while the session is still open, and detaches a plain collection into the DTO). A new test,
`JobOfferServiceIntegrationSpec`, uses Spring Test's `TestTransaction.end()` to actually close the session mid-test
and assert the DTO still serializes — the only way to reproduce this class of bug in a test, since neither a
`@DataJpaTest` repository spec (never leaves its own transaction) nor a Mockito/Instancio unit test (fake entities
aren't real Hibernate proxies) can catch it.

### 7. What did *not* help: bigger Hikari pool (2026-09-19)

`app-job-offers` runs on Hikari's untouched default (`maximum-pool-size=10`). Given the query fix above added a
second (sometimes third) DB round-trip per request, bumping the pool to 30 (mirroring `app-candidates`'s successful
tuning) looked like the obvious next move. **It made 1500rps measurably worse** (0.53% KO → 14.9% KO, Hikari
`pending` jumped from 0 to 159) — more concurrent connections just meant more concurrent Postgres backend processes
competing for the same 3 CPU cores. This is the same lesson as the 2026-09-11 parallel-workers finding, from the
other direction: more concurrency does not help once the downstream resource (CPU, here) is the real limit — it
just moves the queueing from the connection pool into Postgres itself, where it's worse. **Reverted.** Rule of thumb
from HikariCP's own sizing guidance (`pool_size ≈ 2 × core_count + spindle_count`): for a 3-core Postgres, something
close to the *default* 10 is already roughly right, not 30.

### 8. `app-job-offers` CPU limit 2 → 3 (2026-09-19)

With Postgres no longer the bottleneck, the app itself became one — a single replica was measured at 1.99/2 cores
(effectively 100%) under 1500rps load, while Postgres CPU stayed under 1/3 cores. Same lever, same precedent as
fix #4, just on the app instead of the database this time. Not yet cleanly validated at 1500rps (see below).

## Former bottleneck (1500 RPS), resolved 2026-09-20

This section described why 1500rps wasn't clean, before fix #9 (below) turned out to fix it too. Left in place
because the mechanism explains *why* the fix worked, which the "Current state" summary doesn't have room for.

Postgres and Hikari were confirmed *not* the constraint at this level (Postgres CPU <1/3 cores, Hikari `pending`=0
with the default pool). Two things compounded instead:

1. **`app-job-offers` CPU**, addressed by fix #8 (2→3) but — at the time this was written — not yet cleanly
   re-measured, because of #2's test-blocking side effect.
2. **The Cilium Gateway's Envoy circuit breaker** for the `app-candidates` upstream cluster runs on Envoy's
   *unconfigured default* thresholds (~1024 max pending requests) — nobody ever set this explicitly, and Cilium
   1.19's Gateway API implementation has no exposed extension point to tune it (checked `CiliumGatewayClassConfig` —
   only covers the generated `Service`, not Envoy cluster settings). Once app latency degrades under load, the
   pending-request queue on the Gateway approaches that ceiling (measured peak: 977) and Envoy starts shedding
   excess load with `503`s. This was never an independent problem — it's downstream of #1: slower app responses →
   bigger pending queue → circuit breaker trips.

Validating whether fix #8 alone was enough had been blocked twice by an unrelated, if likely load-test-induced,
infrastructure failure: sustained 1500rps trace volume appeared to overwhelm `observability-1` (it hosted Kafka +
all of Tempo's write path + the OTEL Collector — 8 telemetry-heavy services on one RPi5, sized historically for
ambient load and short bursts, not a sustained 1500rps soak) badly enough that its kubelet stopped responding
entirely (`NodeNotReady`, SSH/ping dead at the userspace level, twice in one session). See the handoff for full
incident detail. **This was independently fixed the same day** by splitting off a 3rd observability node (option 1
below) — Kafka/Tempo's write path stayed on `-1`, the querier/query-frontend/otel-collector/backend components moved
to the new `-3`, removing the confound and letting a clean 1500rps run actually complete.

**Resolution: fix #9's anti-affinity + CPU 3 turned out to close #1 *and* #2 at once, with no dedicated 1500rps work
needed.** Once each app replica had a guaranteed-dedicated node (no more CPU throttling from node-sharing, no more
the Little's-Law connection-imbalance spiral documented under fix #9) and 3 full cores to use, `app-job-offers`
latency simply never degraded enough under 1500rps for the Gateway's pending-request queue to approach the ~1024
circuit-breaker threshold — so #2 never had a trigger to fire. Confirmed 2026-09-20: 202,500 requests at
`maxRps=1500 stepDuration=3m ramps=1`, **0% KO**, p99=78ms. Options 2 and 3 below remain undone and, for now,
unnecessary — revisit only if a future round pushes the ceiling past ~1500-1600rps and the Envoy queue becomes the
limit again.

## Options for pushing past 1200rps cleanly

1. ~~**A third observability node**~~ — **done 2026-09-20**, see fix #9 and the resolution note above. Splitting
   Kafka off from Tempo's other write-path components (`otel-collector`/`querier`/`query-frontend` moved to the new
   `-3`) removed the confound that was blocking 1500rps test runs from completing at all. Cost one of the four
   generic worker nodes at the time — recovered same-day by repurposing an idle 3rd database node instead (see fix
   #9), so no net loss to app capacity.
2. **Tail-based trace sampling** in `otel-collector` (already the `contrib` distribution, has `tail_sampling` built
   in; `replicaCount: 1` so there's no cross-instance span-routing complexity to solve first): sample 100% of error
   traces, a small percentage (e.g. 1%) of everything else. Cuts trace volume without losing debuggability for
   failures — the standard production pattern for this exact problem. Will likely also need `otel-collector`'s own
   resource limits raised (currently 500m CPU / 1Gi memory, sized for much lower ambient volume); `tail_sampling`
   buffers each trace in memory for `decision_wait` before deciding, which adds memory pressure under load. Not
   needed to hit 1500rps cleanly — revisit only if trace volume becomes the limit again at a higher RPS.
3. **Raise the Envoy circuit breaker directly** — no supported way to do this on Cilium 1.19 today. Would need a
   newer Cilium version (check release notes for Gateway API `BackendTrafficPolicy` support before upgrading, per
   the version-pinning discipline in the root `CLAUDE.md`) or an unsupported direct edit of the auto-generated
   `CiliumEnvoyConfig`, which Cilium's Gateway controller can silently revert on its own reconciliation. Not needed
   to hit 1500rps cleanly — the queue never approached the threshold once #1 (former bottleneck) was fixed.

### 9. Hard pod anti-affinity: one app replica per node, guaranteed (2026-09-20)

Repurposing a generic worker as a 3rd observability node (fix #10 below) dropped the pool from 4 workers to 3 for
4 total app pods (2 candidates + 2 job-offers) — the scheduler's default spreading is a soft preference, not a
guarantee, and it put one `app-candidates` and one `app-job-offers` replica on the same node. Measured consequences:
- **~10x CPU-throttling gap** (`container_cpu_cfs_throttled_seconds_total`) on the shared-node pod vs. a dedicated one.
- **A self-reinforcing Gateway connection-imbalance**: at clean latency, 1200rps only needs ~30-50 concurrent
  connections (Little's Law: connections ≈ rate × latency), a small enough sample that per-connection round-robin
  luck can visibly skew. Any small slowdown (the throttling above) raises latency, which raises the connections
  needed, which amplifies the existing skew, which slows the loaded pod further — a feedback loop that produced an
  87/13 CPU split between two identical `app-candidates` replicas and repeated `HikariPool "failed to obtain
  connection"` errors, at an RPS level that had been clean for the entire rest of this session.

Root-caused by elimination, not guesswork — checked and ruled out in this order: the job-offers query split (fix #6;
re-tested with the pre-split query from `main`, which was worse, not better, at the same RPS), Gateway/Envoy latency
between nodes (pinged from the Gateway-announcing node to both candidates' nodes, difference was measurement noise),
node CPU capacity in isolation (`kubectl top`/Grafana panels showing "65% usage, 105% commitment" looked survivable
until cross-checked against the JVM's own near-instantaneous `jvm_cpu_recent_utilization_ratio`, which showed both
JVMs on the shared node fully pegged at their 2-core limit — the Kubernetes-panel "CPU Usage by Pod/Node" queries
use a 5-minute `rate()` window that dilutes a short 1-2 minute test's peak by 2-3x, i.e. don't trust those two panels
for anything shorter than ~10 minutes).

**Fix:** freed a 3rd database node instead (`db-3`, the project only ever runs 2 Postgres instances, so 3 was
pre-existing headroom, not a real need) and repurposed it as `worker-4` — no need to give back the observability
node. Combined with a hard `podAntiAffinity` (`requiredDuringSchedulingIgnoredDuringExecution`, matching on
`app in (app-candidates, app-job-offers)`, `topologyKey: kubernetes.io/hostname`) on both deployments, so the
one-pod-per-node placement is guaranteed by the API server, not the scheduler's default heuristics or luck after a
restart. CPU limits raised 2→3 on both apps to use the now-dedicated node's headroom (leaving 1 core margin for
per-node DaemonSets — going to the full 4 risks the same >100%-commitment problem fix #10 already found once).

**Two more real bugs found deploying this anti-affinity, both fixed the same day:**
- `podAntiAffinity` defaults to the scheduled pod's *own* namespace when `namespaces` is omitted from the
  `labelSelector` term. `app-candidates` (namespace `candidates`) and `app-job-offers` (namespace `job-offers`) never
  saw each other under the first version of this rule — same-app self-avoidance worked, cross-app avoidance silently
  did nothing, and two pods still landed on one node. Fixed by adding an explicit `namespaces: [candidates,
  job-offers]` to the term on both deployments.
- The default `RollingUpdate` strategy (`maxSurge: 25%`) tries to briefly run a surge pod during any rollout —
  with the hard anti-affinity above and exactly one node per replica, there is no valid node for that surge pod, and
  the rollout deadlocks (`0/12 nodes are available: 4 node(s) didn't match pod anti-affinity rules`) until a human
  deletes an old pod by hand. Fixed by setting `strategy.rollingUpdate: {maxSurge: 0, maxUnavailable: 1}` on both
  deployments — a rollout now always frees a node before claiming it, never both at once.

**Result:** 1200rps, 90,000 requests, 0% KO, p99=359ms (mean 20ms) — clean, and CPU usage between the two replicas of
each app now nearly identical (candidates: 299m/325m; job-offers: 482m/440m — compare to the 87/13 split before).

**Postgres got the same one-instance-per-node treatment for the same reason**, applied same-day: `postgres-candidates`
and `postgres-job-offers` were already each the only workload on their node (`db-1`/`db-2`), so there was no sharing
to fix, but CPU limit was raised 2→3 on `postgres-candidates` (`postgres-job-offers` already had 3, from the
2026-09-11 fix) to use the now-uncontested headroom, matching the apps' limit. Historical note: an earlier plan had
3 database nodes for 2 Postgres instances specifically for *replication/redundancy* (every node holding data for
both services) — never implemented, and explicitly **not** being revisited now; today's fix is a single instance
per service on a single dedicated node, nothing more.

> **The headline lesson of this whole incident: on this hardware, one JVM (or Postgres) per physical node is not an
> optimization — it's a correctness requirement.** A single RPi5's 4 cores cannot reliably host two independent,
> CPU-hungry processes at once without one measurably starving the other, and that starvation can cascade (via
> Little's Law) into a load-balancing failure far more dramatic than the CPU numbers alone suggest. Whenever
> capacity work on this cluster considers adding a workload to an already-occupied node — instead of a dedicated
> one — treat that as the default wrong answer, not a convenient shortcut.

### 11. `app-job-offers` CPU-bound above ~2000rps — `app-candidates` is not the bottleneck (2026-09-20)

(Numbered 11, not 10 — "fix #10" elsewhere in this file already refers to the observability-3 repurposing
documented in `.claude/handoff-k8s-rpi-cluster.md`/`CLAUDE.md`, not a section in this file.)

A 2200rps load test showed `app-candidates` responding slowly (p50 8ms → 225ms, p90 20ms → 650ms, p99 40ms →
985ms during the peak) — but root-caused by elimination again, not by assuming candidates itself was at fault:
- **Not the database**: zero `HikariPool`/error log lines in Loki for the entire slow window.
- **Not `app-candidates`' own CPU**: peaked at ~2.0 of its 3-core limit, throttling negligible (~0.008 fraction).
- **Was `app-job-offers`**: both replicas hit **2.9-3.0 of their 3-core limit** at the exact same timestamps, with
  real measured CPU throttling (up to ~7.5% of a 30s window) — and `app-job-offers`' *own* p99 spiked to ~900ms in
  lockstep with candidates'. `app-candidates` calls `app-job-offers` synchronously (Feign) on every
  `matching-offers` request and waits for the response — job-offers' throttling becomes candidates' latency
  directly, with candidates itself never under real pressure.

`app-job-offers` only has 2 replicas (vs. candidates' 3, see fix #1 in
`.claude/handoff-k8s-rpi-cluster.md`), and there's currently no free worker to add a 3rd — all 5 generic workers
are occupied (3 pinned `app-candidates` + 2 `app-job-offers`). Confirmed after this: **1900rps sustained cleanly
for ~9 minutes** (0% KO, see [Current state](#current-state) above) — so 2200rps is past the real, now-measured
5-worker ceiling (1900), not just past the old 1500/1600 number from before the 3rd `app-candidates` replica. This
may be the actual ceiling of the current layout, not a bug to fix. **No fix applied yet** — next round of capacity
work should find the exact current max RPS (deliberately not chasing it by adding more hardware) before deciding
whether `app-job-offers` needs its own capacity increase.

**Bonus finding, unrelated to the bottleneck itself**: `jvm_cpu_recent_utilization_ratio` (the "near-instantaneous"
cross-check the methodology lessons below recommend) got stuck reporting a flat 0 for one specific JVM instance
each of the last two test rounds — confirmed via `container_cpu_usage_seconds_total` (cAdvisor, independent of the
JVM's own self-report) showing real, substantial usage (up to 2.4 cores) on the exact same instance at the exact
same timestamps. Not a ghost/stale series (the affected instance was confirmed still live and currently scheduled,
not a leftover from a prior rollout), not node-specific (checked kernel/OS version on the affected node both
times, no anomaly found — unlike the real kernel-version outlier on `worker-2`, see `CLAUDE.md`'s handoff notes).
Root cause not identified (would need attaching a profiler/JFR to the specific stuck JVM, and it's an ephemeral pod
that's already gone by the time this is noticed) — mitigated by deleting the affected pod(s) for a fresh JVM.
**Practical rule: don't trust `jvm_cpu_recent_utilization_ratio` alone for a single instance that reads exactly
0 under otherwise-real load — cross-check against `container_cpu_usage_seconds_total` for that specific pod before
concluding it's actually idle.**

### 12. Recurring ~40 s latency spikes at ~1500rps — pre-existing, cluster-side, cause unknown (2026-09-24)

At ~1500rps p99 jumps by 150-450 ms (sometimes ~2 s on the ramp) on **all** replicas of both services in the same
second, every ~40 s, in bursts of a few episodes. RPS on the server dips briefly (~1325-1430 vs ~1500) and catches up.
Nothing changes at ≤1200rps (p99 flat ~24 ms). **Not a regression** and **not new**: the same 40 s cadence is in
Prometheus for the 2026-09-22 04:55 UTC run (gaps 40,40,40,40,40,40 s, peak 642 ms) and earlier ones (peaks ~2200 ms).
Gatling reports (`load-test/build/reports/gatling/`): 2026-09-21 23:13 p99 1547 ms, 2026-09-22 04:15 p99 536 ms,
2026-09-22 04:55 p99 438 ms, 2026-09-23 22:09 p99 89 ms (0 KO) — today is not worse. See
`.claude/handoff-k8s-rpi-cluster.md` item 00 for what was ruled out (client, network, CPU, GC — Serial and G1,
Postgres, thermals/power) and the in-cluster probe plan. Side finding: the JVMs run **Serial GC with a 384 MB heap**
(container limit 1536Mi is under the ~1.8 GB "server class" threshold, `MaxRAMPercentage` defaults to 25%); trying G1
with a 922 MB heap on `app-job-offers` changed nothing measurable client-side and was reverted.

**Follow-up, same day:** an in-cluster probe (`busybox` pod on `observability-3` looping `wget` against the candidates
Service, the Gateway, candidates' `/actuator/health` and `app-job-offers` directly) saw **no** 40 s spikes at all in a
clean 1500rps run (360k requests, p99 36 ms, max 118 ms). The cadence did not reproduce; what did reproduce is #13.

### 13. `findCandidateMatchIds` as native SQL — Hibernate re-translated it on every request (2026-09-24)

1800-1900rps "stopped working": the same shape as the clean 2026-09-20 run (`maxRps=1900 ramps=1`, 3 min hold)
gave p95 459 ms / p99 1757 ms (vs 90 / 556 then). Not a regression — `app-job-offers` CPU per request has sat at
**~2.55-2.85 ms** since 2026-09-20 (checked in Prometheus run by run, including before the OTel Micrometer changes),
which is 81-90% of 2 replicas × 3 cores at 1900rps: right on the queueing knee, so ±10% of noise decided between
throttling in 9-29% of CFS periods (the "clean" run) and 48-72% (today). The probe confirmed the slowness is in
`app-job-offers` itself (direct calls just as slow, candidates' health endpoint clean).

A 60 s JFR (`settings=default`, at 1200rps — no measurable effect on latency) showed **~19% of all CPU samples in
`ConcreteSqmSelectQueryPlan.buildInterpretation`**, i.e. HQL→SQL translation on every call. Hibernate 7.2's
`SqmInterpretationsKey.isCacheable` refuses to cache a query plan that has an applied entity graph **or** any
multi-valued (`IN :list`) parameter binding — `findCandidateMatchIds` has two such lists (`employmentTypes`,
`skillNames`), `findByIdIn` has both an `IN` list and `@EntityGraph`. The first one alone was ~12-16% of CPU and runs
on every request. For comparison, the OTel agent + span export was ~5% and Micrometer ~2%.

**Why it cost so much — what Hibernate does with a JPQL/HQL `@Query`.** Running an HQL query is two separate
steps:
1. **Translate** the HQL text into SQL: parse it into a semantic tree (SQM), resolve every path (`o.company`,
   `jos.skill.name`) against the entity metamodel, pick table aliases, build a SQL AST with a `ColumnReference` per
   column, render the SQL string, and work out how each parameter binds. Pure CPU, lots of small objects — it's
   essentially a compiler run, and a `JOIN` + `EXISTS` subquery makes it a non-trivial one.
2. **Execute** the SQL over JDBC and map the rows.

Normally step 1 happens once: the result (the "query plan") goes into Hibernate's query plan cache and every later
call only does step 2. But an `IN :list` parameter changes the SQL text with the list size — `IN (?, ?)` for two
skills, `IN (?, ?, ?, ?, ?)` for five — so Hibernate 7.2 simply doesn't cache the plan of any query with a
multi-valued binding (`SqmInterpretationsKey.isCacheable` returns `false`) and redoes step 1 on every call. Our search
query has two such lists, so every single request paid a full HQL compilation before touching the database — ~0.4 ms
of CPU per request out of ~2.7 ms, on a service that sits at 80-90% of its CPU limit at 1900rps.

A **native** query skips step 1 entirely: the SQL is already SQL, Hibernate only expands `IN (:skillNames)` into the
right number of `?` placeholders (a string operation) and binds values. The database receives exactly the same
statement as before, so the Postgres execution plan and indexes are unaffected — only the JVM-side compilation is
gone. The trade-off is that the query now names tables/columns (`job_offer`, `geo_lat`) instead of entity fields, so a
column rename in a Flyway migration must be mirrored by hand; the repository and integration specs cover it.

Fix: `findCandidateMatchIds` rewritten as a native query with the same SQL shape (same joins, `IN (...)`, `EXISTS`
— Postgres sees the same statement as before, so no plan change), result typed via `@SqlResultSetMapping`
(`@ColumnResult(type = UUID.class)`; without it H2 reads the `UUID` column as `byte[]`). `findByIdIn` left as is (~4%,
only runs when something matched). Result on the same 1900rps shape, warm JVMs:

| | before | after |
|---|---|---|
| p50 / p95 / p99 / max | 24 / 459 / 1757 / 2173 ms | **11 / 46 / 236 / 460 ms** |
| `app-job-offers` CPU per request | ~2.85 ms | **~1.8-2.3 ms** |
| max CFS throttling per replica | 48-72% | **4-6%** |
| KO | 0 | 0 |

Gatling reports (`load-test/build/reports/gatling/`): after = `candidatesimulation-20260924072457189`. The
"before" reports (`…062744832` and the other morning runs) were wiped by a root-level `./gradlew clean` during the
deploy build — their summary tables are the numbers quoted above.

**Watch out — a rollout under load is not safe.** The 1200rps warm-up run started ~2 min after the new pods came up
got **15.5% 503s** from the Gateway's Envoy circuit breaker (see [former bottleneck](#former-bottleneck-1500-rps-resolved-2026-09-20)):
cold JVMs burned 6-10 ms CPU per request for the first ~2 minutes, throttled in 85-90% of periods, and the pending
queue overflowed. Same cold-start mechanism that got the `app-candidates` HPA pinned. Warm up with low traffic after
every deploy before measuring anything.

Confirmed at a higher level afterwards: **2000rps held ~10 min, p99 202 ms, 0% KO** (see [Current state](#current-state)).

**How we got here (2026-09-24), for the next round:** the question was "1800 used to work, now it doesn't". The
path that actually found it: (1) an in-cluster probe to rule the client in or out — slow seconds were identical on
the Service, the Gateway and a direct `app-job-offers` call, while candidates' `/actuator/health` stayed clean, so
the stall was in `app-job-offers`; (2) CPU **per request** (`container_cpu_usage_seconds_total` ÷ request rate),
compared run by run in Prometheus, instead of trusting a remembered result — flat since 2026-09-20, so nothing had
regressed, the service was simply at its knee; (3) a JFR profile at a load *below* saturation to see where the CPU
per request goes; (4) reading Hibernate's bytecode to confirm why the plan wasn't cached, instead of guessing at
config flags. Things that looked plausible and weren't it: the OTel agent (~5%), GC (Serial GC, heap not under
pressure), the OTel Micrometer changes of 2026-09-21/22, node kernel, Postgres.

### 14. "2000rps got worse after a break" — the load generator was on battery (2026-09-27)

After a few days off and a full power cycle, the same 2000rps shape gave p99 930 ms / max 1834 ms (5 min run) and
p99 459 ms / max 892 ms (3 min run), against 147-202 ms on 2026-09-24. **Nothing in the cluster had changed**: the
running image was byte-for-byte the 2026-09-24 build (same JAR timestamp, same JDK 25.0.4+7, same pod spec), CPU per
request and the Hikari connection-use distribution were identical (p50 3.3 / p99 ~25 ms), Postgres was warm (100%
cache hit), and there was no throttling, no TCP retransmits, no Cilium drops, no disk I/O and no thermal issue.

What was different was the **arrival pattern**: per-second completions at `app-candidates` had a stdev of ~324 req/s
(vs ~40 on 2026-09-24), with all 3 replicas losing ~10% of their traffic in the same second and getting ~20% extra
the next — a client pause followed by Gatling's open-model catch-up. Client-side spikes came **every ~30 s**, with a
phase that shifted between runs (so tied to the Gatling process, not to any cluster timer). The Mac running Gatling
was on battery (`pmset -g batt` → "Battery Power"). Plugged into AC (a 90 W monitor was enough): p99 174 ms, max
274 ms, arrival stdev ~37 req/s, max pool queue 35.

**Why a small client burst hurts this much:** `JobOfferService.search()` is `@Transactional` end to end, so each
request holds one of only 10 Hikari connections through scoring (pure JVM CPU work). A burst pushes the JVM to ~97%
CPU, connections are held longer, and the pool queue snowballed to 140-190 for several seconds — while
`pg_stat_activity` showed those connections as `idle` / `idle in transaction` (Postgres waiting on the app, not the
other way round). The same mechanism was already visible on 2026-09-24 as 1-2 s queue blips (≤85); a steady
arrival rate just never let it snowball. Narrowing the transaction to the two queries (not done yet) is the
structural fix if real, bursty traffic ever matters.

#12's unexplained ~40 s cadence — same symptom (all replicas at once, server RPS dips then catches up), and the
in-cluster probe saw none of it — is very likely the same cause. Not verified retroactively.

### 15. Trace sampling moved from the collector into the apps (2026-09-27)

The apps used to export 100% of spans and the collector's `tail_sampling` dropped ~99%. Now a javaagent extension
(`LocalTailSamplingSpanExporter` in `otel-metrics-filter`) buffers spans per trace until the local root ends and
keeps the same policies (1% by trace id, any HTTP 4xx/5xx, local root >= 500 ms); the collector keeps everything it
gets. DB timings moved from the `span_metrics` connector to the agent's `db.client.operation.duration`. Same
2000rps / `stepDuration=2m` run, warm JVMs, Mac on AC:

| | Before (22:36 UTC) | After (23:33 UTC) |
|---|---|---|
| `app-candidates` CPU per request | 2.74 ms | 2.32 ms (**-15%**) |
| `app-job-offers` CPU per request | 1.88 ms | 1.81 ms (-4%) |
| JVM GC time, candidates / job-offers | 0.044 / 0.041 s/s | 0.070 / 0.060 s/s (+50-60%) |
| `otel-collector` CPU / memory / ingress | 0.70 cores / 956 MB / 14 MB/s | 0.06 / 66 MB / 0.38 MB/s |
| `observability-3` node CPU | 24% | 6% |
| Gatling p99 / max | 174 / 274 ms | 213 / 346 ms |

The big win is the observability side (collector -91% CPU, -93% memory); the apps gain less, because spans are
still created for every request (the keep/drop decision needs the finished request) and only serialization/export
is saved. **The first run right after the rollout was misleading** (p99 799 ms, one 313 ms full GC in
`app-candidates`): JVMs ~8 min old vs the 3.5-day-old baseline — warm up and re-run before comparing.

**GC time looked +50-60% worse, but that's JVM age, not the exporter.** Dropping the 10 s decision memory
(`9f5d28b`, see CLAUDE.md "Trace sampling") didn't bring it down (candidates 0.070 → 0.100 s/s, and a 2000rps run
right after got p99 878 ms from one `app-job-offers` replica spiralling on its pool queue — the other replica had
p99 48 ms; the run-to-run spread at 2000rps today was 174-878 ms p99 on the same setup). A 60 s JFR allocation
profile (`settings=default`, `app-candidates` at 1200rps) attributes only **~1.0%** of allocations to
`LocalTailSamplingSpanExporter` and ~1.4% to the `database` semconv opt-in (DB metrics + query summary); span
creation by the agent is ~14% and unchanged. What actually differed was heap sizing: Serial GC grows the heap
lazily, and the baseline's longest-lived candidates pod had grown Eden to 107 MB (vs ~37-46 MB on every fresh pod),
so it ran ~3x fewer minor GCs and pulled the baseline average down. GC comparisons across pods of different age
aren't meaningful until the heap is pinned (`-Xms` = `-Xmx`, not done).

### 16. Heap pinned (`-Xms384m -Xmx384m`) — GC -60-70%, best 2000rps runs so far (2026-09-28)

Serial GC used to start at a 24 MB heap and grow it lazily to the 384 MB default max, so Eden sat at ~37-46 MB on
every pod younger than a few days (#15). Pinning `-Xms` = `-Xmx` (same 384 MB ceiling) gives every pod the full
107 MB Eden from startup. Same sequence as #15 (warm-up 1000rps/1m + 2000rps/2m, then measured 2000rps/2m, twice,
arrival stdev 33-41 req/s):

| | Baseline (#15, 3.5-day-old pods) | Run 1 | Run 2 |
|---|---|---|---|
| Gatling p50 / p95 / p99 / max | 10 / 42 / 174 / 274 ms | 10 / 31 / **114** / 198 ms | 9 / 35 / **124** / 188 ms |
| `app-candidates` CPU per request | 2.74 ms | 2.14 ms | 2.47 ms |
| `app-job-offers` CPU per request | 1.88 ms | 1.94 ms | 1.96 ms |
| GC time, candidates / job-offers | 0.044 / 0.041 s/s | 0.017 / 0.012 | 0.016 / 0.012 |
| Minor GCs per minute, candidates / job-offers | 406 / 376 | 181 / 118 | 178 / 117 |
| `otel-collector` CPU / memory | 0.70 cores / 956 MB | 0.05 / 67 MB | 0.06 / 67 MB |

Even the post-rollout warm-up runs were calmer than before (1000rps max 289 ms vs 1067 ms in #15), because a fresh
pod no longer spends its first minutes collecting a tiny young generation. CPU per request is within run-to-run
noise; the p99 gain comes from far fewer GC pauses feeding the pool-queue amplifier (#14).

**Follow-up, same day: a 1 GB pinned heap was tried and reverted.** `-XX:+UseSerialGC -Xms1g -Xmx1g`, limit
1536Mi → 2Gi (Serial made explicit because a limit above ~1792 MB makes the JVM pick G1 by itself). Eden grew to
286 MB and minor GCs dropped ~2.7x (candidates ~180 → ~66/min, job-offers ~118 → ~42/min); pause length stayed the
same (6-9 ms), so GC time fell to 0.007-0.009 / 0.005-0.007 s/s. **Latency didn't follow**: p99 111 / 161 ms and
max 180 / 485 ms over two runs, vs 114 / 124 ms and 198 / 188 ms with 384 MB — the worse run was one
`app-job-offers` replica queuing on its 10-connection pool (88 waiting), not GC. With GC already at ~1-2% of a
core, it's no longer what drives the tail; the pool-held-through-scoring amplifier (#14) is. Reverted to 384 MB.

### 17. `app-candidates` loads the candidate with `JOIN FETCH` instead of an entity graph (2026-09-28)

A 60 s JFR at 1200rps (all 5 pods, `settings=profile`) put ~10% of `app-candidates` CPU in
`LoaderSelectBuilder`/`SingleIdEntityLoaderStandardImpl.createLoadPlan`: `findById` carried
`@EntityGraph("Candidate.withSkillsAndEmploymentTypes")`, and Hibernate never caches a load plan with an applied
entity graph — the same mechanism as #13, via the other trigger. Replaced by a JPQL
`findWithSkillsAndEmploymentTypesById` with `LEFT JOIN FETCH` on both collections (a single scalar parameter, so its
plan is cached); `skills` became a `Set` so fetching it alongside `preferredEmploymentTypes` can't duplicate rows.
The same profile refuted "candidates burns CPU in Jackson": Jackson is ~6% in both apps, because 75% of responses
carry 0 offers (0.6 on average — 50k offers spread over the globe, ~90 km search radius). The rest is framework
overhead: Tomcat + Spring MVC ~30%, OTel agent ~19%, Feign ~22%. 2000rps/2m after warm-up:

| | Before | After |
|---|---|---|
| Gatling p99 / max | 113 / 420 ms | 89 / 201 ms |
| `app-candidates` CPU per request | 2.45 ms | 1.92 ms (**-22%**) |
| `postgres-candidates` CPU | 0.89 cores | 0.51 (**-42%**) |

More than the profile's 10%: the entity graph also loaded one of the collections with a separate query
(`CollectionLoaderSingleKey`, ~7%), which `JOIN FETCH` folds into the single statement.

### 18. Bigger dataset — `postgres-job-offers` becomes the ceiling; `shared_buffers` 128MB → 1GB (2026-09-28)

Goal: load Postgres, not the JVMs. Data scaled with `make k8s-reload-data` (100k candidates throughout; candidate
count barely matters — one PK lookup per request). At 250k offers / 20k companies (359 MB) 2000rps collapsed to
~810rps with 47% fast 503s and `postgres-job-offers` at 3.0/3 cores. Sampling `pg_stat_activity` at 700rps showed
>50% of active samples on an `IO` wait with zero disk reads: the 128MB default `shared_buffers` made most page reads
syscall copies from the OS page cache (~0.6 core of system CPU on `db-2`). `shared_buffers=1GB` (the pod has a 2Gi
limit) lifted 250k to ~1700rps — better, still not 2000. Bisecting the offer count at 2000rps (warm-up, then
2000rps/2m):

| Offers / companies | DB size | Offers per response | Result | p99 | `postgres-job-offers` CPU |
|---|---|---|---|---|---|
| 50k / 10k (before, 128MB buffers) | ~75 MB | 0.6 | 0% KO | 89 ms | 1.47 / 3 |
| **150k / 10k** | 217 MB | 2.6 | 0% KO | **211 ms** | 2.45 / 3 |
| 175k / 10k | 251 MB | 2.3 | 0% KO | 960 ms | 2.66 / 3 |
| 200k / 16k | 289 MB | 3.1 | 2.5% KO, ~1890rps | 2773 ms | 2.97 / 3 |
| 250k / 20k | 359 MB | 4.0 | 8.5% KO, ~1700rps | 3174 ms | 2.98 / 3 |

Why cost grows with data here: the search filters a geographic bounding box, so offers examined per request scale
with offer *density*, not `log(N)` — a typical request at 150k walks ~30 offers and ~75 skill probes for 4 matches.
On a warm DB that query plans in ~0.95 ms and executes in ~0.58 ms, so planning is comparable to execution; whether
the prepared statements actually skip planning under load is not verified yet (next step: `pg_stat_statements`).
`app-job-offers` follows closely: CPU per request rises 1.7 → ~2.3-2.5 ms with bigger responses (2.2-2.5 of 3 cores
per pod at 150-175k).

## Methodology lessons (apply to future rounds)

- **Don't count sampled traces with Tempo's search API** — it silently returns incomplete results (even with
  `limit=1000`), which twice made working sampling look broken (#15). Count spans with
  `tempo_distributor_spans_received_total` (Compose: `docker exec prometheus wget -qO- http://tempo:3200/metrics`)
  over a controlled burst instead.
- **`OTEL_SEMCONV_STABILITY_OPT_IN=database` renames the agent's JDBC pool metrics** (#15):
  `db_client_connections_pending_requests` / `..._use_time_milliseconds_*` became
  `db_client_connection_pending_requests` / `..._use_time_seconds_*` (seconds, not ms). Dashboards use `hikaricp_*`
  and are unaffected; ad-hoc queries like the ones in #14 need the new names.

- **Run Gatling from a Mac on AC power only** — check `pmset -g batt` before every test. On battery the generator
  sends in periodic bursts (#14), which looks exactly like a cluster-side regression. To tell them apart, look at
  arrival smoothness with 1 s resolution:
  `sum(irate(http_server_request_duration_seconds_count{job="app-candidates",http_route=~"/api.*"}[3s]))` — a
  synchronized dip-then-overshoot on all replicas is the client, not the cluster.

- **Do not profile a live pod under load with `jcmd JFR.start settings=profile`.** At 1500rps on a 3-core limit the
  recording itself pushed p99 to ~2.2 s on every replica for the whole recording. Use `settings=default`, short, or
  sample from outside the pod.
- **Distinguish a client-side stall from a cluster-side one with an in-cluster probe**, not by reasoning: a pod that
  loops `wget` against the Service and the Gateway and logs `epoch latency rc` sees the same slow seconds as the
  server iff the cause is not the load generator.
- **A "clean 1800rps" memory is not evidence** — compare the Gatling report tables (`Total/p95/p99/max/KO`) of both
  runs before concluding something regressed.
- **Track CPU per request, not just CPU.** `sum(rate(container_cpu_usage_seconds_total{...}))` ÷
  `sum(rate(http_server_request_duration_seconds_count{...}))` is comparable across runs of different RPS and shape;
  it's what showed #13 was a capacity problem, not a regression, and it's the number a fix should move.
- **A profile at ~60% load is more useful than one at saturation** — at saturation everything looks slow; below it,
  JFR `settings=default` (not `profile`) shows the real per-request cost with no measurable latency impact.
- **After any rollout, warm up before measuring** — cold JVMs cost 3-5x CPU per request for the first ~2 minutes and
  can trip the Gateway's circuit breaker at 1200rps (#13).
- **A root-level `./gradlew clean` also wipes `load-test/build/reports/gatling/`** — `deploy.sh`,
  `minikube-image.sh` and the `Makefile` used to do exactly that on every deploy; since 2026-09-24 they clean only the
  modules they build (`:app-job-offers:clean` etc.). Don't reintroduce a bare `clean`.

- **Test one variable at a time under real concurrent load.** A bundled test of two changes can make a working fix
  look like a failure (see #5). A clean `EXPLAIN ANALYZE` or single-request benchmark does not predict behavior
  under concurrency.
- **More concurrency is not always better once a downstream resource is the real limit** (see #5 and #7) — a bigger
  connection pool, more parallel query workers, or more replicas sharing an already-saturated resource just moves
  the queueing somewhere worse.
- **Verify load test data is fresh against the live database before trusting a "0% KO" result.** A stale
  `candidatesDataFile` full of nonexistent candidate IDs will report clean results while silently never exercising
  the code path under test (404s count as "OK" in the Gatling check).
- **When changing a JPA collection from eager to lazy, add a test that actually closes the transaction/session**
  (Spring Test's `TestTransaction.end()`) before asserting the result is still usable — a `@DataJpaTest` spec or a
  mock-based unit test cannot reproduce a `LazyInitializationException` that only happens after the HTTP-layer
  transaction boundary.
- **Keep load test steps short** (`stepDuration=1m` is enough) — sustaining peak RPS for many minutes is what
  overwhelms the observability pipeline, not the measurement itself.
- **Don't trust the "CPU Usage by Pod/Node" Grafana panels when the dashboard's visible time range is much longer
  than the test itself** — they use `$__rate_interval` (correct, self-adjusting to the visible range — prefer this
  over a hardcoded window in any new panel), but that still averages a 1-2 minute burst down to nothing if the
  browser has a wide time range open. Narrow the dashboard to roughly the test's duration, and cross-check against a
  near-instantaneous metric instead (`jvm_cpu_recent_utilization_ratio` for the JVMs, or `kubectl top` / Headlamp's
  live view) before concluding a node has spare capacity. **Caveat added 2026-09-20 (fix #11 below):**
  `jvm_cpu_recent_utilization_ratio` itself has been seen stuck at a flat 0 for a single JVM instance under real
  load, twice — if one instance reads exactly 0 while its siblings show real values, cross-check that specific pod
  against `container_cpu_usage_seconds_total` (cAdvisor) before trusting it either.
- **A hard `podAntiAffinity` across namespaces needs an explicit `namespaces` list on the term** — it silently
  defaults to the scheduled pod's own namespace otherwise, so a cross-namespace rule (e.g. keeping two different
  services' pods off the same node) can look correctly configured and simply never fire.
- **Hard anti-affinity plus a tight node budget needs `maxSurge: 0`** on the deployment's rolling-update strategy —
  the default `maxSurge: 25%` tries to run an extra pod during every rollout, and with no spare node satisfying the
  anti-affinity rule, the rollout deadlocks until a human intervenes.
