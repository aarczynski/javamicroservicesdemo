# RPS scaling journey — `app-candidates` / `app-job-offers`

History of what actually moved the needle on sustained throughput against the physical RPi5 cluster
(`http://192.168.10.100`), in the order the fixes landed. Session-by-session narrative and raw incident detail lives
in `.claude/handoff-k8s-rpi-cluster.md`; this file is the distilled, forward-looking summary — update it whenever a
change measurably moves the ceiling, don't let it rot into a second handoff.

## Current state

**1500 RPS sustained, 0% KO** — confirmed 2026-09-20, same session as fix #9 below (`maxRps=1500 stepDuration=3m
ramps=1`, 202,500 requests, p50=9ms/p95=25ms/p99=78ms/mean=12ms/max=608ms, ~2 min held near peak — 1s buckets
touched 1600 during the hold). This resolves what the
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
are occupied (3 pinned `app-candidates` + 2 `app-job-offers`). 2200rps is already well past the documented
1500rps-safe/1600rps-borderline ceiling above, so this may simply be the real ceiling of the current 5-worker
layout, not a bug to fix. **No fix applied yet** — next round of capacity work should find the actual current max
RPS (deliberately not chasing it by adding more hardware) before deciding whether `app-job-offers` needs its own
capacity increase.

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

## Methodology lessons (apply to future rounds)

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
