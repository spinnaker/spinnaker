# Clouddriver task repository: Redis/SQL failure modes, fixes, and a queued (pub/sub) dispatch option

**Status**: Planned. No phase implemented yet.

| Phase | Description | Status |
|---|---|---|
| 0 | Shared TCK regression tests that reproduce each finding | Not started |
| 1 | Small independent fixes (503 mapping, SQL retry no-op, Redis saga IDs / key TTL / null tasks) | Not started |
| 2 | SQL ordering + concurrency fix (sequence column, per-task row lock, terminal immutability) | Not started |
| 3 | Redis hardening (status-write isolation, atomic and idempotent writes) | Not started |
| 4 | `DefaultOrchestrationProcessor` fixes (stop after failure, bounded executor) | Not started |
| 5 | Optional queued dispatch on kork-pubsub "Single" (Redis Streams), opt-in | Not started; depends on [#8031](https://github.com/spinnaker/spinnaker/pull/8031) |

Update this table as each phase lands. Link the PR and note any deviations.

## Context: clouddriver has a task *store*, not a task *queue*

Today an operation never goes through a queue. `POST /ops` reaches whichever pod the load balancer
picks. `DefaultOrchestrationProcessor` creates a task in the `TaskRepository` and runs the operation
**on that same pod**, on an unbounded thread pool (`ThreadPoolExecutor(0, Integer.MAX_VALUE, …,
SynchronousQueue)`, `DefaultOrchestrationProcessor.java:63`). The `TaskRepository` (Redis, SQL, or
`DualTaskRepository` during migration) is only the status store that Orca's `MonitorKatoTask` polls
through `GET /task/{id}` on any pod.

This matters for the pub/sub question. A Redis Streams "queue" changes *who runs the work and when*
(dispatch). It does not change *where status lives* (storage). The two current problems are both
storage-layer problems, so a queue alone fixes neither one. See [Pub/sub evaluation](#pubsub-evaluation).

How Orca reacts (this determines how bad a storage failure becomes):
- `MonitorKatoTask` retries `lookupTask` only 5 times at 100 ms (`MonitorKatoTask.groovy:~121`). A 404
  is tolerated 30 times.
- After that, `BaseRetrofitExceptionHandler.shouldRetry` retries a GET only on network errors,
  502/503/504, or 429. It retries **any** method on 503. **A 500 is not retried, so the stage goes
  TERMINAL.**

## Findings

"Verified" means one of two things. **Repro**: reproduced against MySQL (Testcontainers) with a
throwaway test, which was then deleted. **Code path**: traced in source, not executed.

### Redis (`RedisTaskRepository`, `JedisTask`)

| # | Finding | Verified |
|---|---|---|
| R1 | **A short Redis outage fails pipelines.** The retry policy is 3 retries at a fixed 500 ms (`RedisTaskRepository.java:59-63`), so anything longer than about 1.5–2 s throws `ExcessiveRedisFailureRetries`, a plain `RuntimeException` that becomes an **HTTP 500**. On the Orca poll path, a 500 is not retried, so the stage goes TERMINAL even though the cloud operation may still be running fine. On the `POST /ops` path, the operation is never accepted and the stage fails. | Code path |
| R2 | **A status-write failure fails an operation that succeeded.** In the processor, `operate()` runs and then `task.updateStatus("Orchestration completed.")` (`DefaultOrchestrationProcessor.java:195`) runs inside the same `try`. If Redis fails there, the inner `catch (Exception)` records "Orchestration failed" and calls `failTask`. Those writes also fail against Redis, the exception escapes the `finally`, and the `Future` is discarded (`:290`). The task stays `STARTED` until its 12 h TTL, and Orca waits out the 1 h `MonitorKatoTask` timeout. Meanwhile the cloud resources really were created. | Code path |
| R3 | **Retried writes are not idempotent.** `rpush` of history, results, and outputs is retried after a timeout even when the first attempt landed (`:261`, `:342`). The result is duplicate history entries and **duplicate result objects**, which Orca merges into `deploy.server.groups` and similar outputs. | Code path |
| R4 | **Multi-key writes are not atomic.** `set()` runs `hmset` + `expire` + `sadd` with no `MULTI` (`:235`), and `addToHistory` runs `rpush` + `expire` + `srem`. A failure partway through leaves a task in `kato:tasks` with no hash, or a task hash with no TTL. | Code path |
| R5 | **Saga IDs are never persisted.** `JedisTask.addSagaId` only mutates the in-memory set (`JedisTask.java:130`). After a `get()`, `getSagaIds()` is empty, so `POST /task/{id}:resume` returns 404 ("No saga was found") and Orca's saga retry turns terminal. **Saga resume does not work on Redis.** | Code path |
| R6 | **`kato:taskmap:<clientRequestId>` keys never expire.** `setnx` is called with no TTL (`:103`), so these keys grow without bound. Once the task hash expires (12 h), a duplicate `create()` with the same request ID returns `getByClientRequestId()` → `get()` → **null**. | Code path |
| R7 | **`list()` returns nulls and `listByThisInstance()` throws NPE.** `kato:tasks` (`:206`) keeps IDs for tasks that never reached a terminal state (pod died), after their hashes expire. `get()` returns null for those, and `listByThisInstance` then calls `t.getOwnerId()` (`:216`) and hits an NPE. That method is called from `OperationsController.destroy()` (`@PreDestroy`), so graceful shutdown breaks. | Code path |
| R8 | Every status update costs two round trips (`lindex` then `rpush`), and the read-modify-write is not atomic. `currentState` NPEs when the history key has expired (`lindex` → null → `readValue(null)`). | Code path |
| R9 | The Redis and SQL implementations return different results. `JedisTask.getHistory()` drops the terminal entry (`:112`). SQL does not. | Code path |

### SQL (`SqlTaskRepository`, `SqlTask`)

The status history is insert-only, and **"latest" is decided by `created_at = clock.millis()` taken
from the writing pod's wall clock**. Two writes in the same millisecond, or writes from pods whose
clocks disagree, produce the wrong "latest" row.

| # | Finding | Verified |
|---|---|---|
| S1 | **Clock skew between pods hides state transitions.** In the retry/resume path, a different pod calls `retry()` and `updateOwnerId()`. If that pod's clock lags, its `STARTED` row sorts **before** the existing `FAILED_RETRYABLE` row, so the task stays failed for everyone, and every later status from that pod is invisible. `selectLatestState` (`SqlTaskRepository.kt:353`) and `runningTaskIds` (`:389`) both order by `created_at`. | **Repro: 200/200** retries invisible with 50 ms of skew |
| S2 | **Rows that share a millisecond make completed tasks look running.** `runningTaskIds` joins on `created_at = MAX(created_at)` (`:389`). With a tie, both the `STARTED` and the `COMPLETED` row match, so the task counts as running. That affects `GET /task` and `@PreDestroy`, which waits the full `shutdownWaitSeconds` on tasks that already finished. | **Repro: 200/200** with a fixed clock. 0/200 with the real clock against a slow local MySQL. A fast production DB makes ties more likely. |
| S3 | **History order is undefined.** `retrieveInternal` is a 4-way `UNION ALL` with **no `ORDER BY`** (`:340`), and `SqlTask.getStatus()` is `history.lastOrNull()` (`SqlTask.kt:81`). On MySQL it happens to come back in `(task_id, created_at, id)` index order. Within one millisecond the tiebreak is the ULID, whose low 80 bits are random (sulky `nextULID()`, not the monotonic variant). So a same-millisecond `COMPLETED` row can sort before the `STARTED` row. Postgres or a different query plan can reorder everything. | Code path. **Not reproduced** (0/200): the ULIDs used real time and rarely collided within a millisecond. |
| S4 | **A FAILED task can be overwritten as COMPLETED.** Neither `updateState` (`:189`) nor `updateCurrentStatus` (`:169`) enforces terminal immutability. Redis does, through `ensureUpdateable`. If S3 makes `getStatus()` report non-terminal, the processor's `finally` (`DefaultOrchestrationProcessor.java:280`) calls `complete()`, which appends a later `COMPLETED` row after `FAILED`. Orca then sees success. | Code path. **Not reproduced** (0/200, depends on S3). |
| S5 | **Read-then-insert races.** `updateCurrentStatus`/`updateState` read the latest state and then insert, at `READ_COMMITTED` with no row lock. A concurrent `complete()` and `updateStatus()` can append a `STARTED`-state row after the `COMPLETED` row. | Code path |
| S6 | **Configured SQL retries never run.** `@Retry(name = "sqlTransaction" / "sqlRead")` is on Kotlin **top-level extension functions** (`sql.kt:38`, `:48`). Spring AOP can only proxy beans, and clouddriver-sql has no AspectJ weaving. So the `resilience4j.retry.instances.sqlTransaction` config (`clouddriver.yml:301`) has no effect, and every transient DB error (deadlock, failover) propagates on the first attempt. | Code path |
| S7 | **`create()` dedupe races.** `create()` checks `getByClientRequestId` and then inserts (`:62`), and `task_request_id_idx` is **not unique**. Two concurrent requests with the same ID both insert, and every later `getByClientRequestId` → `fetchOne` throws `TooManyRowsException`. | Code path |
| S8 | The `addResultObjects` guard checks the **earliest** state (`orderBy(created_at.asc())`, `:143`), which is always `STARTED`, so it never fires. | Code path |
| S9 | `SqlTaskCleanupAgent` deletes only `COMPLETED`/`FAILED` (`SqlTaskCleanupAgent.kt:55`). `FAILED_RETRYABLE` tasks that are never resumed stay forever. The candidate query also scans `task_states` without an index on `state`. | Code path |

### Shared / processor

| # | Finding | Verified |
|---|---|---|
| P1 | **On SQL, a multi-op request keeps executing after one op fails.** The inner `catch` calls `failTask` and does not `break` (`DefaultOrchestrationProcessor.java:216`, `:250`). On Redis, the next `updateStatus` throws (`ensureUpdateable`), which stops the loop by accident. On SQL nothing throws, so the next `operate()` runs against a failed task. | Code path |
| P2 | **The executor is unbounded** (`Integer.MAX_VALUE` threads). A burst of operations means unbounded threads and cloud API calls, with no backpressure and nothing that tells Orca to slow down. | Code path |
| P3 | **A pod restart loses in-flight work.** `@PreDestroy` waits `shutdownWaitSeconds`, and anything still running is orphaned in `STARTED`. Non-saga operations can't be resumed; Orca waits until its timeout. | Code path |

## Recommendation (summary)

1. **Fix the existing implementations first** (Phases 0–4). The worst user-visible failures (R1, R2,
   R5, S1, S6) are each small, independent fixes.
2. **Make SQL the recommended production backend** once Phase 2 lands. Document the Redis repository
   as "single Redis, no HA guarantees". Its shape (no atomic multi-key reads, which `JedisTask`'s own
   Javadoc admits) limits how correct it can become.
3. **Build a Redis Streams *dispatch* layer as an opt-in Phase 5**, not as a replacement task store.
   It is worth having for backpressure, draining, and rolling deploys (P2, P3). It is not a fix for
   R1/S1–S5.

## Phase 0: TCK regression tests

`clouddriver-core`'s `TaskRepositoryTck` already runs against every implementation
(`SqlTaskRepositoryTest extends TaskRepositoryTck`). Add cases there so each fix below is proven on
every backend:
- Fixed `Clock`: `updateStatus` → `complete()`. Assert that `get().status` is terminal and that `list()` excludes the task (S2, S3).
- Two repositories with skewed clocks: `fail(true)` on A, then `retry()` on B. Assert `STARTED` (S1).
- `fail(false)`, then `complete()`. Assert the task is still failed and/or the call throws (S4).
- `addSagaId` → `get()`. Assert the saga IDs round-trip (R5).
- Terminal state, then `addResultObjects`. Assert it throws (S8).

The Redis-specific cases (R1/R3/R4) need a fault-injecting delegate or Toxiproxy around the Valkey
container. Use Valkey per the project test-infra standard.

## Phase 1: small, independent fixes (land first, separate PRs)

1. **Map "status store unavailable" to 503, not 500 (R1).** Put `@ResponseStatus(SERVICE_UNAVAILABLE)`
   on `ExcessiveRedisFailureRetries`, or add a controller advice that also covers transient
   `DataAccessException`s. Orca already retries 503 on any method. `POST /ops` is safe to retry
   because of `clientRequestId` dedupe (after S7 is fixed for SQL). This one change turns most
   Redis-blip TERMINALs into a delayed poll.
2. **Make SQL retries real (S6).** Replace the inert annotations with programmatic Resilience4j
   (`RetryRegistry.retry("sqlTransaction").executeSupplier { … }`) inside `transactional`/`read`.
   Keep the existing config names so operator config keeps working. Include deadlock/serialization
   failures, and exclude `AggregateChangeRejectedException` as the config already does.
3. **Redis saga IDs (R5).** Make `addSagaId` persist through `repository.set()`, matching
   `SqlTask.addSagaId`.
4. **Redis key hygiene (R6, R7).** Put `TASK_TTL` on `kato:taskmap:*` at `setnx` time (`SET NX EX`).
   Filter nulls in `list()` and `SREM` their IDs when `get()` finds no hash.
5. **SQL cleanup (S9).** Include `FAILED_RETRYABLE` with its own, longer TTL property.

## Phase 2: SQL ordering and concurrency fix

Schema (Liquibase, MySQL + Postgres):
- Add `seq BIGINT` to `task_states` and `task_outputs`. Use `AUTO_INCREMENT` with a unique key on
  MySQL and `GENERATED BY DEFAULT AS IDENTITY` on Postgres. The ALTER backfills existing rows in
  PK/ULID order, which matches current behaviour. The DB assigns the order, so pod clocks no longer
  matter.
- Make `tasks.request_id` unique. The migration first collapses existing duplicates (keep the
  earliest task). `create()` catches `DuplicateKeyException` and returns the existing task (S7).
- Optional: add `tasks.current_state` (denormalized, updated in the same transaction). It turns
  `runningTaskIds` into an indexed lookup instead of the `MAX()` self-join.

Code:
- Order by `seq` everywhere: history (add an explicit `ORDER BY` to the `task_states` branch, or sort
  in `TaskMapper`), `selectLatestState`, and `runningTaskIds` (`MAX(seq)` or `current_state`). Fixes
  S1–S3.
- Serialize writes per task with `SELECT … FROM tasks WHERE id = ? FOR UPDATE` at the start of every
  mutating transaction (S5).
- Enforce terminal immutability in the repository, matching Redis. `updateCurrentStatus`/`updateState`
  reject writes on a terminal task. The only allowed transition out of a terminal state is
  `FAILED_RETRYABLE → STARTED` through `retry()` (S4). Fix the `addResultObjects` guard to check the
  latest state (S8).
- `created_at` stays for display and TTL, but nothing orders by it any more.

Risk: P1 currently "works" on SQL because there is no immutability. Land Phase 4's `break` with or
before the immutability change so multi-op requests fail cleanly instead of throwing from
`updateStatus`.

## Phase 3: Redis hardening (for installs that stay on Redis)

- **Isolate status writes from operation outcome (R2).** Route non-terminal `updateStatus`/`updateOutput`
  through a small per-task ordered buffer that retries in the background with backoff. A failed
  progress write is logged and counted but never fails `operate()`. Terminal transitions (`complete`,
  `fail`) retry with a much longer, configurable budget before giving up.
- **Atomicity (R4, R8).** Use `MULTI`/Lua for `set()` and for "read current state, append if not
  terminal". A Lua script also halves the round trips.
- **Idempotent appends (R3).** Either tag each history/result entry with a client-generated ID and
  dedupe on read, or store history as a per-task Stream and append with `XADD` at an explicit
  client-chosen ID. A retried `XADD` with an ID at or below the last one is rejected, which gives
  natural dedupe. The server-assigned order also makes the Stream an exact ordering source.
- **Configurable retry policy** (exponential backoff, max duration) instead of the hard-coded 3 × 500 ms.
- Align `getHistory()` with SQL (R9). That changes the API response, so call it out in release notes.

## Phase 4: `DefaultOrchestrationProcessor`

- `break` out of the op loop after `failTask` (P1).
- Bounded, configurable executor (core/max/queue). When saturated, reject with 503 + `Retry-After`
  so Orca backs off. Add gauges for active/queued operations (P2).
- Replace the read-then-write `finally` (`if (!getStatus().isCompleted()) complete()`) with a
  repository-side "complete if not terminal" operation. Phase 2/3 make that atomic.

## Pub/sub evaluation

**Question:** could a Redis Streams–based task queue (kork-pubsub "Single", PR #8031) handle this
better than the current Redis/SQL repositories?

**What Streams would fix:**
- **Backpressure / bounded concurrency (P2).** Each pod consumes at most N operations from a
  consumer group, and excess work waits in the stream instead of spawning threads.
- **Draining and rolling deploys (P3).** On shutdown a pod stops reading. Work that hasn't started
  stays in the stream for other pods. Work abandoned by a crashed pod is visible through `XPENDING`
  and can be reclaimed.
- **Load spreading.** Work goes to whichever pod is free, not whichever pod the load balancer picked.
- **Ordering, if history were stored as a Stream.** Server-assigned IDs give a total order (the
  Phase 3 option). The SQL `seq` column fixes the same problem more cheaply for SQL installs.

**What Streams would not fix:**
- **Redis availability (R1/R2).** A Streams queue on the same Redis has the same blast radius. If
  Redis is down, nothing can be enqueued or acked. Phase 1's 503 mapping is what turns that into a
  delay instead of a failure.
- **SQL time ordering (S1–S5).** That's a storage bug, and Phase 2 fixes it.
- **Status reads.** Orca still polls `GET /task/{id}`, which still needs an authoritative store.

**The hard part is redelivery, not transport.** Cloud operations are generally **not idempotent**. A
deploy redelivered after a crash in the middle of `operate()` could create a second server group.
`XCLAIM` is last-claim-wins (already documented in the kork-pubsub-redis README), so it is not a
fencing mechanism. Any queue design must:
- Treat the task store (SQL) as the source of truth and Redis Streams as dispatch only. This is the
  split `cats-pubsub` already uses (Streams for scheduling, SQL `pubsub_agent_state` for state).
- Fence execution with a lease in SQL: `UPDATE tasks SET owner_id = ?, lease_expires_at = ? WHERE id
  = ? AND (lease_expires_at < now() OR owner_id = ?)`, with DB time. A reclaimer only runs work it
  wins the lease for.
- On reclaim: if the task never reached "Processing op", run it. If it is saga-backed, resume the
  saga. **Otherwise mark it `FAILED` ("worker lost before completion"). Never re-run it blindly.**

### Proposed Phase 5 design (opt-in, `clouddriver.operations.dispatch: queued`)

1. `POST /ops`: `TaskRepository.create()` (SQL), then `XADD clouddriver:operations {taskId, cloudProvider, serialized description, auth context}`, then return the task ID. If the stream is unavailable, return 503 and leave the task un-started (the Orca retry dedupes on `clientRequestId`).
   - The serialized payload is the raw description list that `OperationsService` converts. Converting
     happens on the consumer, so credentials resolve where the work runs.
   - The auth context (user, allowed accounts) must travel with the message and be re-applied on the
     consumer, as `DefaultOrchestrationProcessor` does today through `propagate()`.
2. Each pod runs a bounded `RedisStreamsSubscriber` (kork-pubsub-redis) in consumer group
   `clouddriver-operations`. The handler acquires the SQL lease, runs the existing processor body
   (extracted from the `Callable`), and `XACK`s only after a terminal status is written.
3. Lease heartbeat while running. The reclaim loop uses `XPENDING`/`XCLAIM` plus the lease and
   reclaim rules above.
4. The default stays `inline` (today's behaviour). The two modes share everything except dispatch.

Alternative worth costing before building: SQL-only dispatch (`SELECT … FOR UPDATE SKIP LOCKED` on a
`pending` state) gets bounded workers and draining with no new runtime dependency. It suits installs
that already moved tasks to SQL to get off Redis. If the dispatcher is written against the kork-pubsub
`PubsubPublisher`/`PubsubSubscriber` contract, either backend (or SQS) can sit behind it.

**Recommendation:** proceed with Phase 5 only after Phases 1–2 land and #8031 merges, and only as
opt-in. Phases 1–4 address every confirmed failure on their own.

## PR sequencing

Independent first: Phase 1 items (each its own PR) → Phase 0 tests (can be folded into each fix's PR)
→ Phase 4 `break` + Phase 2 (land together, see risk note) → Phase 3 → Phase 5.
