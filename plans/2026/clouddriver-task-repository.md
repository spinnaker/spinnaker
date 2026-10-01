# Clouddriver task repository: Redis/SQL failure modes, fixes, and a queued (pub/sub) dispatch option

**Status**: Planned. No phase implemented yet.

| Phase | Description | Status |
|---|---|---|
| 0 | Shared TCK regression tests that reproduce each finding | Not started |
| 1 | Small independent fixes (503 mapping, SQL retry no-op, Redis saga IDs / key TTL / null tasks) | Not started |
| 2 | SQL ordering + concurrency fix (sequence column, per-task row lock, terminal immutability) | Not started |
| 3 | Redis hardening (status-write isolation, atomic and idempotent writes) | Not started |
| 4 | `DefaultOrchestrationProcessor` fixes (stop after failure, bounded executor) | Not started |
| 5a | SQL execution leases + reaper (fast detection of lost work, no dispatch change) | Not started; depends on Phase 2 |
| 5b | SQL-backed dispatch (pending state, claim, bounded workers, draining), opt-in | Not started; depends on 5a |
| 5c | Optional broadcast "work available" nudge to cut claim latency | Not started; depends on 5b + [#8031](https://github.com/spinnaker/spinnaker/pull/8031) |

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
3. **Build dispatch on the SQL task repository (Phase 5), not on Redis Streams.** SQL can provide
   restart recovery, failover handling and work distribution (P2, P3). It also avoids a dual write
   between SQL and Redis, and keeps leases and fencing in the same transactional store as task
   status. See [Phase 5](#phase-5-sql-backed-execution-leases-and-dispatch). Streams stays an optional
   latency optimization (5c), not a requirement.

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

### Why SQL rather than Streams for dispatch

Every Streams design above still needs SQL for the lease, the fencing token and the task status. So
Streams would only add a push wake-up, and it would bring problems SQL avoids:

- **Dual write.** "Create task in SQL, then `XADD`" isn't atomic. A crash between the two leaves a task
  that never runs, or a message for a task that doesn't exist, so it needs an outbox table in SQL
  anyway. With SQL dispatch, creating and enqueueing the task is one transaction.
- **Two failure domains.** Dispatch would depend on Redis *and* SQL being up.
- **Redelivery semantics.** `XCLAIM` is last-claim-wins, so SQL fencing is needed regardless.
- **Throughput isn't a constraint.** Clouddriver handles at most tens of operations per second.
  keiko-sql already runs orca's far busier work queue on the same databases
  (`orca/keiko-sql/.../SqlQueue.kt`).

What Streams would still add is sub-second wake-up without polling. 5c covers that as an optional
nudge on top of SQL, not as the source of truth.

Redis-only installs don't get Phase 5. Dispatch requires the SQL task repository. Document that
alongside "SQL is the recommended backend".

## Phase 5: SQL-backed execution leases and dispatch

Prerequisites: Phase 2 (`seq` ordering, per-task row lock, terminal immutability, `tasks.current_state`)
and Phase 1's working SQL retries. Target databases: MySQL 8.0 and Postgres 16, matching
`kork-sql-test`.

### Schema additions (`tasks` table, plus one new table)

| Column | Purpose |
|---|---|
| `current_state` | From Phase 2, with a new `PENDING` value for accepted-but-unclaimed work (5b). |
| `lease_owner` | Instance ID currently executing the task (separate from `owner_id`, which stays "who accepted it" for display/compatibility). |
| `lease_expires_at` | Set from **DB time** (`CURRENT_TIMESTAMP(3)` / `clock_timestamp()`), never from the pod clock. S1 showed what pod clocks do to ordering. |
| `lease_version` | Fencing token, incremented on every claim. Every status write from an executor includes `AND lease_version = ?`. Zero rows updated means the lease was lost, so the executor stops. |
| `progress` | Checkpoint: index of the last atomic operation that *finished*, plus whether one is in flight. The reaper uses it to decide what's safe to do. |
| `available_at` | DB time from which a `PENDING` task may be claimed (supports delayed retry/backoff). |

New table `task_payloads(task_id, cloud_provider, body, auth_context)`. It stores the raw request
body and the request's user/allowed accounts, and is deleted when the task reaches a terminal state.
Index: `(current_state, available_at)` for claiming, `(current_state, lease_expires_at)` for the
reaper.

Operation descriptions can carry sensitive values (for example manifests). Keep payload rows only
while needed, delete them on terminal state, and evaluate encrypting `body` at rest before 5b ships.

### 5a: Leases + reaper (no change to who runs the work)

This alone fixes the worst restart behaviour (P3) without changing dispatch.

- The accepting pod claims the lease at `create()`, in the same transaction, and heartbeats it
  (default TTL 2 min, heartbeat every 20 s) for as long as the operation runs.
- A **reaper** runs on every pod, using a small `LIMIT` and conditional updates so pods don't
  collide. It finds `STARTED` tasks whose `lease_expires_at < now()` (DB time) and claims each with
  `UPDATE … SET lease_version = lease_version + 1 … WHERE id = ? AND lease_version = ?`. Only the
  winner acts:
  - **Saga-backed:** set `FAILED_RETRYABLE`. Orca's existing `MonitorKatoTask` → `:resume` path
    resumes the saga, on whichever pod Orca's request reaches.
  - **Otherwise:** set `FAILED` with "clouddriver instance executing this task stopped before it
    completed". **Never re-run a non-idempotent operation blindly.**
- Result: a pod that crashes, is OOM-killed or loses its node turns its tasks into fast, explicit
  failures within about one lease TTL. Today Orca waits for its 1 h `MonitorKatoTask` timeout.
- **Zombie protection:** a pod that was partitioned and comes back finds its fenced writes rejected
  and abandons the task. Fencing can't recall a cloud API call already sent, which is exactly why
  non-saga work is failed rather than re-run.
- **Graceful shutdown** (`@PreDestroy`): stop heartbeating after `shutdownWaitSeconds`. Tasks still
  running are released immediately (lease expiry set to now) instead of waiting out the TTL.

### 5b: SQL dispatch (opt-in, `clouddriver.operations.dispatch: queued`)

**Accepting work (`POST /ops`):**
1. Validate and authorize synchronously on the receiving pod, exactly as today
   (`OperationsService.collectAtomicOperations`). Bad requests still get an immediate 400.
2. In one transaction, insert the task (`current_state = PENDING`) and its `task_payloads` row. Dedupe
   on `clientRequestId` through the Phase 2 unique index.
3. **Local-first fast path:** if this pod has a free worker slot, claim the task inside the same
   transaction and start it immediately. In the common case that's the same latency as today, and
   other pods only see work that overflowed or was orphaned.
4. Return the task ID. If the DB is unavailable, return 503 and nothing is created (Orca retries).

**Claiming (each pod, bounded by `clouddriver.operations.max-concurrent`):**
- Poll only while there are free slots: every 1 s with jitter, backing off to 5 s when idle.
- Claim with keiko-sql's pattern: select candidate IDs
  (`current_state = PENDING AND available_at <= now()`, ordered by `seq`, `LIMIT slots * 3`), then
  claim each by primary key with
  `UPDATE … SET current_state = 'STARTED', lease_owner = ?, lease_version = lease_version + 1,
  lease_expires_at = now() + ttl WHERE id = ? AND current_state = 'PENDING'`. It works on both
  databases and holds no long locks. `SELECT … FOR UPDATE SKIP LOCKED` is an equivalent option on
  MySQL 8 / PG 16. Choose one after testing under contention.
- The worker reloads the payload, re-runs `collectAtomicOperations` with the stored user/accounts
  context (re-checking authorization at execution time), and runs the existing processor body,
  extracted from `DefaultOrchestrationProcessor`'s `Callable`.
- Write the `progress` checkpoint before and after each atomic operation, with fencing.

**What this provides:**

| Need | How SQL dispatch covers it |
|---|---|
| **Distribution** | Work goes to pods with free slots instead of whichever pod the load balancer picked. Per-pod caps replace the unbounded thread pool (P2). Optional fairness later: claim queries can cap in-flight tasks per account or provider. |
| **Graceful restart / rolling deploy** | A pod stops claiming at shutdown. `PENDING` work isn't owned by anyone, so other pods pick it up. In-flight work is handled by 5a's release rules. |
| **Crash / node loss** | Lease expiry and the reaper. Tasks with no op in flight (`progress` shows the next op not started) go back to `PENDING` and **are safely re-run elsewhere**, including the rest of a multi-op task after its last checkpoint. An op caught in flight is resumed if saga-backed, otherwise failed explicitly. |
| **Network partition** | Fencing tokens. The partitioned pod's writes are rejected, and it stops at the next checkpoint. |
| **DB failover** (e.g. Aurora writer failover, roughly 30–60 s) | Working SQL retries (Phase 1). The lease TTL is set longer than the failover window so heartbeats survive it. In-flight operations keep running and their status writes retry. Leases use the DB's clock, so pod clock skew doesn't matter. A clock jump on the new writer is small compared with the TTL. Tradeoff: a longer TTL means slower crash detection. Both are configurable. |
| **Backpressure to Orca** | When every pod is saturated, `PENDING` work waits in the table rather than being refused. Add a configurable max `PENDING` depth above which `POST /ops` returns 503 so Orca backs off. |

**Latency cost:** with local-first, only overflow and orphaned work waits for a poll, up to about 1 s
plus jitter. That is negligible against cloud operations that take seconds to minutes, and 5c can
remove it.

### 5c (optional): wake-up nudge

After committing a `PENDING` task that the accepting pod couldn't claim locally, publish a
best-effort "work available" message on a kork-pubsub **broadcast** channel (#8031). Idle pods poll
immediately on receipt. The message carries no task data and SQL stays the only source of truth, so a
lost message only costs one poll interval. Build this only if the polling latency is measured to
matter.

### Testing

- Extend the Phase 0 TCK: claim exclusivity under concurrent claimers, fencing rejection after lease
  loss, reaper outcomes for each `progress` state (not started / in flight with saga / in flight
  without saga), and lease expiry driven by DB time with skewed pod clocks.
- MySQL and Postgres via `kork-sql-test`. Run with `--max-workers=1` locally.
- A chaos-style integration test: two clouddriver contexts on one DB. Kill one mid-operation and
  assert the task is resumed (saga), re-run (op not started) or failed (op in flight), each within
  one TTL.

## PR sequencing

Independent first: Phase 1 items (each its own PR) → Phase 0 tests (can be folded into each fix's PR)
→ Phase 4 `break` + Phase 2 (land together, see risk note) → Phase 3 (Redis installs only, can run in
parallel with 5) → 5a → 5b → 5c (only if measured latency warrants it).
