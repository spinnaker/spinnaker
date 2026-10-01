# Clouddriver task repository: failure modes, fixes, and a SQL task queue

**Status**: Planned. No PR landed yet. Focus is the SQL task repository and SQL task queue. Redis
repository hardening (Phase 3) is deprioritized.

The phases describe *what* changes. The [Work plan](#work-plan) at the end turns them into an ordered
PR list. Track status there.

| Phase | Description |
|---|---|
| 0 | Shared TCK regression tests that reproduce each finding |
| 1 | Small independent fixes (503 mapping, SQL retry no-op, Redis saga IDs / key TTL / null tasks) |
| 2 | SQL ordering + concurrency fix (per-task sequence, `current_state`, row lock, terminal immutability, cleanup) |
| 3 | Redis hardening. **Deprioritized**: recommend moving to SQL via `DualTaskRepository` instead |
| 4 | `DefaultOrchestrationProcessor` fixes (stop after failure, atomic completion, bounded executor) |
| 5a | Per-instance execution leases + reaper (fast detection of lost work, no dispatch change) |
| 5b | SQL task queue: pending state, claim, bounded workers, draining. Opt-in |
| 5c | Optional real-queue transport (Redis Streams, SQS, …) fed by a transactional outbox. Only if needed |
| 5d | Optional task-completion events so Orca reacts instead of polling (needs an Orca change) |
| 6 | Read-path optimization (cheap unchanged polls, read pool); optional write coalescing at medium scale |

## Context: clouddriver has a task *store*, not a task *queue*

Today an operation never goes through a queue. `POST /ops` reaches whichever pod the load balancer
picks. `DefaultOrchestrationProcessor` creates a task in the `TaskRepository` and runs the operation
**on that same pod**, on an unbounded thread pool (`ThreadPoolExecutor(0, Integer.MAX_VALUE, …,
SynchronousQueue)`, `DefaultOrchestrationProcessor.java:63`). The `TaskRepository` (Redis, SQL, or
`DualTaskRepository` during migration) is only the status store that Orca's `MonitorKatoTask` polls
through `GET /task/{id}` on any pod.

This matters for the pub/sub question. A Redis Streams "queue" changes *who runs the work and when*
(dispatch). It does not change *where status lives* (storage). The two current problems are both
storage-layer problems, so a queue alone fixes neither one. See [Queue semantics](#queue-semantics-what-a-real-queue-offers-and-where-task-state-lives).

How Orca reacts (this determines how bad a storage failure becomes):
- `MonitorKatoTask` retries `lookupTask` only 5 times at 100 ms (`MonitorKatoTask.groovy:~121`). A 404
  is tolerated 30 times.
- After that, `BaseRetrofitExceptionHandler.shouldRetry` retries a GET only on network errors,
  502/503/504, or 429. It retries **any** method on 503. **A 500 is not retried, so the stage goes
  TERMINAL.**

## Target scale

Two tiers. Design for both, and optimize for the first.

| Tier | Ops/min | Ops/s | In flight (1–2 min ops) | Status writes/s (about 20 per op) | Orca polling reads/s (in flight ÷ 5 s) |
|---|---|---|---|---|---|
| **Typical** (most orgs) | 50–100 | about 1–2 | 50–200 | 20–40 | 10–40 |
| **Medium** | 500–2,000 | 8–33 | 500–4,000 | 160–660 | 100–800 |

**The workload is read-heavy.**
- **Per poll vs per write.** Each Orca poll runs `retrieveInternal`'s 4-way `UNION` and returns the
  whole history, results and outputs, which can include large stdout. Each write is one small insert.
- **Long operations.** Polls grow with duration (a 10-minute operation gets about 120 polls) while
  writes stay at about 20.
- **Other readers:** UI and API consumers, and `GET /task` listing.
- So even where poll and write counts are similar (short operations), read *work* dominates.
  Phase 6 targets reads first.

**What each tier means for the design:**
- **Typical:** nothing here strains SQL. The design must not add operational burden for these orgs:
  `inline` dispatch stays the default, no new infrastructure is required, and schema changes are
  instant.
- **Medium:** `task_states` reaches 55–230M rows (14–57M per day, 4-day `completedTtlMs`). Nothing may
  rewrite or scan it. `current_state` and cleanup indexing are required. Per-task heartbeats would
  cost about 200 writes/s, so leases are **per instance** (about 2 writes/s fleet-wide). Claims (8–33/s)
  are still trivial for SQL.

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
| S1 | **Clock skew between pods hides state transitions.** In the retry/resume path, a different pod calls `retry()` and `updateOwnerId()`. If that pod's clock lags, its `STARTED` row sorts **before** the existing `FAILED_RETRYABLE` row, so the task stays failed for everyone, and every later status from that pod is invisible. `selectLatestState` (`SqlTaskRepository.kt:353`) and `runningTaskIds` (`:389`) both order by `created_at`. | **Repro: 200/200** retries invisible with 50 ms of skew. Kept as `SqlTaskRepositoryKnownIssuesTest` (see Phase 0). |
| S2 | **Rows that share a millisecond make completed tasks look running.** `runningTaskIds` joins on `created_at = MAX(created_at)` (`:389`). With a tie, both the `STARTED` and the `COMPLETED` row match, so the task counts as running. That affects `GET /task` and `@PreDestroy`, which waits the full `shutdownWaitSeconds` on tasks that already finished. | **Repro: 200/200** with a fixed clock. 0/200 with the real clock against a slow local MySQL. A fast production DB makes ties more likely. Kept as `SqlTaskRepositoryKnownIssuesTest` (see Phase 0). |
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
3. **Make SQL the system of record and the default dispatcher (Phase 5).** A real queue (Streams,
   SQS) can be added later as an optional transport fed by a transactional outbox (5c). Correctness
   never depends on it. See [Queue semantics](#queue-semantics-what-a-real-queue-offers-and-where-task-state-lives)
   for when a broker would be worth adding.
4. **Design every SQL change for the [target scale](#target-scale).** That means instant or online
   schema changes only, indexed lookups only on `task_states`, per-instance (not per-task)
   heartbeats, and a read path that keeps unchanged polls cheap.

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

**Already landed with this plan:** `clouddriver-sql`'s `SqlTaskRepositoryKnownIssuesTest` reproduces
S1 and S2 against MySQL (50/50 each). Each test asserts the correct behaviour, but while the defect is
present it logs a `KNOWN ISSUE` warning and is **skipped** through a JUnit assumption rather than
failed, so the build stays green and the defect stays visible in test reports. The PR that fixes S1
or S2 should replace the assumption with a hard assertion (or move the case into the TCK).

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

(The SQL cleanup fix for S9 moved to Phase 2: at the target scale it needs `current_state` to avoid
scanning `task_states`.)

## Phase 2: SQL ordering and concurrency fix

**Schema** (Liquibase, MySQL 8 + Postgres 16). At 55–230M `task_states` rows, nothing here may
rewrite `task_states`. Adding an `AUTO_INCREMENT`/identity column would copy the whole table on MySQL,
so ordering uses a **per-task sequence** instead:
- `tasks.next_seq BIGINT NULL` and `task_states.seq BIGINT NULL` (and `task_outputs.seq`). Nullable
  `ADD COLUMN` is instant on MySQL 8.0.29+ and metadata-only on Postgres 11+.
- Each write transaction already locks the task row (below), so it reads `next_seq`, uses it as the new
  row's `seq`, and increments it. Result writes bump `next_seq` too, so it doubles as the task's
  version for Phase 6's poll cache. That gives an exact per-task order with no clock involved, which is
  all ordering needs (nothing compares order across tasks).
- Legacy rows (`seq IS NULL`) sort first, by the old `(created_at, id)`. They belong to tasks created
  before the upgrade, which almost all reach a terminal state, and are cleaned up, within the 4-day TTL.
- `tasks.current_state VARCHAR NULL` plus `tasks.completed_at BIGINT NULL`, written in the same
  transaction as every state change. **Required** at this scale. A batched backfill (an agent, not
  Liquibase) fills legacy non-terminal tasks. Until it finishes, `NULL` falls back to the legacy
  latest-state lookup for that task only.
- Indexes: `tasks(current_state, owner_id)` for running-task lookups, `tasks(current_state,
  completed_at)` for cleanup, and `task_states(task_id, seq)`. Build them online: MySQL
  `ALGORITHM=INPLACE, LOCK=NONE`; Postgres `CREATE INDEX CONCURRENTLY`, which means Liquibase
  `runInTransaction: false`.
- `tasks.request_id` becomes unique. A preparatory step collapses existing duplicates (keep the
  earliest task) before the online unique index build. `create()` catches `DuplicateKeyException`
  and returns the existing task (S7).

**Code:**
- Every mutating transaction starts with `SELECT … FROM tasks WHERE id = ? FOR UPDATE` (S5). The
  statement also returns `next_seq` and `current_state`, so the same round trip serves the immutability
  check and the sequence.
- Order by `seq` everywhere: history (an explicit `ORDER BY`, or sort in `TaskMapper`) and latest
  state. `runningTaskIds` uses `current_state` and never touches `task_states`. Fixes S1–S3.
- Enforce terminal immutability in the repository, matching Redis. `updateCurrentStatus`/`updateState`
  reject writes on a terminal task. The only allowed transition out of a terminal state is
  `FAILED_RETRYABLE → STARTED` through `retry()` (S4). Fix the `addResultObjects` guard to check
  `current_state` (S8).
- **Cleanup (S9):** select expired task IDs from `tasks` by `(current_state, completed_at)`, including
  `FAILED_RETRYABLE` with its own longer TTL property, then delete child rows by `task_id` in batches.
  Stop scanning `task_states` by `state`.
- `created_at` stays for display and TTL, but nothing orders by it any more.

Risk: P1 currently "works" on SQL because there is no immutability. Land Phase 4's `break` with or
before the immutability change so multi-op requests fail cleanly instead of throwing from
`updateStatus`.

## Phase 3: Redis hardening (deprioritized)

Only worth doing if a significant number of installs must stay on the Redis task repository. The
recommended path is moving to SQL with `DualTaskRepository`. Phase 1 already covers the worst
Redis failures (R1, R5–R7).


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

## Queue semantics: what a real queue offers, and where task state lives

Workload processing is what queue systems (SQS, RabbitMQ, Kafka, Redis Streams) are built for, so
choosing SQL needs a real justification. **SQL is not a better queue in general.** The argument is
narrower: a clouddriver task has two jobs, and only one of them is queue-shaped.

1. **Work to dispatch.** Accept an operation, buffer it, and hand it to exactly one worker. Queues
   excel at this.
2. **A record people read by ID**, continuously until it finishes and for days after: status, history,
   results, outputs. This is the [read-heavy](#target-scale) side of the workload. A queue can't serve
   it, because messages aren't queryable by ID and disappear once acknowledged.

Every design therefore has a database. The real choice is between:
- **(a)** a database that is also the queue, or
- **(b)** a database plus a queue, kept consistent.

### Queue features, measured against this workload

| Queue feature | Why queues are good at it | Needed here? | SQL equivalent |
|---|---|---|---|
| Buffering bursts, decoupling producers from consumers | Producers never wait for consumers | Yes (pipeline fan-out bursts) | `PENDING` rows. At 1–33/s this is trivial. |
| Push delivery | No polling, millisecond latency | Somewhat. Operations take seconds to minutes, and local-first means most never wait. | Overflow waits for a 1 s poll. 5c can add push. |
| Competing consumers, load balancing | Built in | Yes | Conditional-UPDATE or `SKIP LOCKED` claim (keiko-sql pattern). |
| Ack + visibility timeout → redelivery | At-least-once delivery for free | **Needed in reverse.** Redelivering a half-run, non-idempotent cloud operation is unsafe. Minutes-long operations also need per-message visibility extensions (e.g. SQS `ChangeMessageVisibility`), which amounts to a per-task heartbeat. | Leases + fencing + `progress` checkpoints, with "fail, don't redeliver" for anything in flight (5a). Queues have no notion of "this message must never run twice". |
| Dead-letter queue | Isolates poison messages | Yes | Terminal `FAILED` rows, which can also be queried by account, time or type. |
| Delayed delivery / backoff | SQS delay, RabbitMQ TTL + DLX | Yes (retries) | `available_at`. |
| Priority | RabbitMQ priorities (SQS has none) | Maybe later | `ORDER BY`. |
| **Per-key ordering and fairness** (Kafka partitions, SQS FIFO message groups) | One key processed at a time, fairly across keys | **Yes, and not done today.** Per-account cloud API limits, and not mutating the same server group concurrently. | A claim predicate (cap running tasks per account or resource key). This is real work in SQL and comes natively with partitioned queues. It is the strongest queue-native advantage for this workload. |
| Very high throughput | 10k–1M+ msgs/s | No (1–33/s) | – |
| Autoscaling signal | Queue depth | Yes | `COUNT(*) WHERE current_state = 'PENDING'`. |
| Fan-out / multiple consumers / replay | Topics, consumer groups, logs | **Not for dispatch.** Valuable for *task events*, e.g. pushing completion to Orca instead of polling. | None. This is a genuine queue/topic use: see [5d](#5d-optional-completion-events-to-orca-instead-of-polling). |
| Operations cost | – | Yes | SQL is already required. A broker is new infrastructure that most orgs, at 50–100 ops/min, shouldn't be forced to run. |

### Why (a) by default, and how (b) fits later

- **(b) is a dual write.** "Insert the task, then publish the message" isn't atomic: a crash between
  the two leaves a task nobody runs or a message for nothing. The standard fix is a **transactional
  outbox** in the database. The queue then becomes a delivery mechanism fed from SQL, and the
  database stays the source of truth either way.
- **The safety-critical parts live in the database regardless:** fencing, "never re-run a
  non-idempotent operation", and the readable task record. A broker can't own them.
- **At typical and medium scale**, the queue features this workload needs (buffering, competing
  consumers, delay, DLQ, depth) are a few columns and indexed queries in SQL. The ones SQL does less
  naturally (push, per-key fairness) are either not latency-critical or can be done with a claim
  predicate.

So: **SQL is the system of record and the default dispatcher. A real queue is an optional transport
(5c) fed by an outbox.** It accelerates delivery and can add per-key partitioning, but correctness
never depends on it: a lost or duplicate message is harmless because the SQL claim decides.

**When a broker would earn its place:**
- Claim rates in the hundreds per second across many pods.
- Per-key fairness needed at a scale where a SQL predicate gets expensive.
- Other services consuming the work stream.
- Orca moving from polling to completion events ([5d](#5d-optional-completion-events-to-orca-instead-of-polling)).
  This is the biggest potential read-load win, and it is a topic/event use, not a work queue.

**Common "database as a queue" problems, and why they don't bite here:**

| Problem | Why it's fine at this scale |
|---|---|
| Polling load | One indexed query per pod per second. |
| Lock contention | Conditional update / `SKIP LOCKED` at 1–33 claims/s. |
| Hot rows | Every task is its own row. |
| Index and MVCC churn | Each task row is updated a handful of times. Postgres autovacuum and MySQL purge handle that. |
| Unbounded growth | The Phase 2 cleanup. |

keiko-sql already runs orca's own, much busier work queue this way.

## Phase 5: SQL-backed execution leases and dispatch

Prerequisites: Phase 2 (per-task `seq` ordering, row lock, terminal immutability, `tasks.current_state`)
and Phase 1's working SQL retries. Target databases: MySQL 8.0 and Postgres 16, matching
`kork-sql-test`.

### Schema additions

New table `clouddriver_instances`, holding **one lease per instance**:

| Column | Purpose |
|---|---|
| `instance_id` | `ClouddriverHostname.ID`. |
| `epoch` | Incremented each time the instance (re-)registers. A pod declared dead must re-register with a new epoch, so writes it makes under the old epoch are fenced out. |
| `lease_expires_at` | Set from **DB time** (`CURRENT_TIMESTAMP(3)` / `clock_timestamp()`), never the pod clock. S1 showed what pod clocks do. |
| `state` | `ACTIVE`, `DRAINING` or `DEAD`. |

New `tasks` columns (all nullable, so instant adds):

| Column | Purpose |
|---|---|
| `current_state` | From Phase 2, with a new `PENDING` value for accepted-but-unclaimed work (5b). |
| `lease_owner`, `lease_epoch` | Instance and epoch executing the task (separate from `owner_id`, which stays "who accepted it" for display/compatibility). |
| `progress` | Checkpoint: index of the last atomic operation that *finished*, plus whether one is in flight. The reaper uses it to decide what's safe to do. |
| `available_at` | DB time from which a `PENDING` task may be claimed (supports delayed retry/backoff). |

New table `task_payloads(task_id, cloud_provider, body, auth_context)` (5b only). It stores the raw
request body and the request's user/allowed accounts, and is deleted when the task reaches a terminal
state. Indexes: `tasks(current_state, available_at)` for claiming and `tasks(lease_owner,
lease_epoch)` for the reaper.

Operation descriptions can carry sensitive values (for example manifests). Keep payload rows only
while needed, delete them on terminal state, and evaluate encrypting `body` at rest before 5b ships.

### 5a: Per-instance leases + reaper (no change to who runs the work)

This alone fixes the worst restart behaviour (P3) without changing dispatch.

- **Registration:** on startup each pod registers (or bumps its epoch) in `clouddriver_instances`. It
  then heartbeats that single row (default TTL 2 min, every 20 s). At 50 pods that's about 2–3
  writes/s fleet-wide, however many tasks are running. A per-task heartbeat would be about 200
  writes/s at the target scale.
- **Ownership:** `create()` stamps `lease_owner`/`lease_epoch` in the same transaction.
- **Fencing:** every write already takes the task row lock (Phase 2), so it also checks
  `lease_owner = me AND lease_epoch = myEpoch`. On a mismatch the write is rejected and the executor
  abandons the task.
- **Reaper** (every pod, low frequency). It finds instances whose lease expired (DB time) and declares
  each `DEAD` with a conditional `UPDATE … WHERE instance_id = ? AND epoch = ? AND lease_expires_at <
  now()`, so only one pod wins. The winner processes that instance's non-terminal tasks in batches:
  - **Nothing in flight** (`progress` shows the next op not started): back to `PENDING` in queued
    mode (5b). In inline mode, `FAILED`, since only queued mode can re-run elsewhere.
  - **Saga-backed op in flight:** set `FAILED_RETRYABLE`. Orca's existing `MonitorKatoTask` →
    `:resume` path resumes the saga, on whichever pod Orca's request reaches.
  - **Non-saga op in flight:** set `FAILED` with "clouddriver instance executing this task stopped
    before it completed". **Never re-run a non-idempotent operation blindly.**
- **Result:** a crashed, OOM-killed or partitioned pod's tasks become fast, explicit outcomes within
  about one lease TTL. Today Orca waits for its 1 h `MonitorKatoTask` timeout.
- **Zombie protection:** a partitioned pod that comes back finds its old-epoch writes rejected and
  abandons its tasks. Fencing can't recall a cloud API call already sent, which is why non-saga work
  is failed rather than re-run.
- **Graceful shutdown** (`@PreDestroy`): mark the instance `DRAINING` and stop accepting or claiming.
  After `shutdownWaitSeconds`, apply the reaper rules to its own remaining tasks and mark it `DEAD`,
  instead of leaving them for TTL expiry.
- **Out of scope:** a single hung operation on a live instance. The instance lease stays healthy, so
  the reaper doesn't see it. Orca's stage timeout still covers it, as today.

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
  (`current_state = PENDING AND available_at <= now()`, ordered by `available_at, id`; the ID is a
  time-ordered ULID; `LIMIT slots * 3`). Then claim each by primary key with
  `UPDATE … SET current_state = 'STARTED', lease_owner = ?, lease_epoch = ? WHERE id = ? AND
  current_state = 'PENDING'`. It works on both databases and holds no long locks.
  `SELECT … FOR UPDATE SKIP LOCKED` is an equivalent option on MySQL 8 / PG 16. Choose one in the
  load test. At 8–33 claims/s, contention is not expected to matter.
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
| **DB failover** (e.g. Aurora writer failover, roughly 30–60 s) | Working SQL retries (Phase 1). The instance lease TTL is set longer than the failover window so heartbeats survive it. In-flight operations keep running and their status writes retry. Leases use the DB's clock, so pod clock skew doesn't matter. A clock jump on the new writer is small compared with the TTL. Tradeoff: a longer TTL means slower crash detection. Both are configurable. |
| **Backpressure to Orca** | When every pod is saturated, `PENDING` work waits in the table rather than being refused. Add a configurable max `PENDING` depth above which `POST /ops` returns 503 so Orca backs off. |

**Latency cost:** with local-first, only overflow and orphaned work waits for a poll, up to about 1 s
plus jitter. That is negligible against cloud operations that take seconds to minutes, and 5c can
remove it.

### 5c (optional): real-queue transport via transactional outbox

Only if a [tipping point](#why-a-by-default-and-how-b-fits-later) is reached.

- **Outbox.** In the same transaction that creates a `PENDING` task, insert an outbox row. A relay
  publishes outbox rows through the kork-pubsub "Single" contract (Redis Streams in #8031, or SQS
  through `kork-pubsub-aws`) and deletes them once the publish succeeds.
- **Consumers.** A consumer receiving a message runs the normal SQL claim for that task ID. It acks
  whether or not it won the claim, because SQL decides. The SQL poll stays on as the fallback, so a
  lost message only costs one poll interval.
- **Per-key fairness.** The transport can partition by account (SQS FIFO message group, Kafka key),
  with the SQL claim predicate as the backstop.

### 5d (optional): completion events to Orca instead of polling

Orca's `MonitorKatoTask` polls `GET /task/{id}` every 5 s for the whole life of every operation. That's
the [dominant read load](#target-scale), and it adds up to 5 s of latency to every completion.

**Orca already has the receiving half.** `RescheduleExecution` (`RescheduleExecutionHandler.kt`)
re-runs an execution's waiting tasks immediately (`queue.ensure` + `queue.reschedule` of their
`RunTask` messages). Manual judgment already works this way: the approval `PATCH
/pipelines/{id}/stages/{stageId}` goes through `CompoundExecutionOperator.updateStage` →
`runner.reschedule`, so its 15 s backoff is only a fallback. What's missing is a way for clouddriver to
trigger it.

**Design:**
- **Correlation.** Orca already sends `X-SPINNAKER-EXECUTION-ID`, and clouddriver's `RequestContext`
  carries it. Store it on the task at `create()` as a new nullable `tasks.execution_id` (an instant
  add).
- **Publish.** After committing a transition to `COMPLETED`, `FAILED` or `FAILED_RETRYABLE`, publish a
  best-effort `{taskId, executionId, state}` event. Use the kork-pubsub "Single" contract (Redis
  Streams #8031, or SQS), so exactly one Orca instance handles each event. Clouddriver gets no HTTP
  dependency on Orca (it has no Orca client today).
- **Consume (Orca change).** On an event, push `RescheduleExecution(executionId)`. `MonitorKatoTask`
  runs immediately and makes one `GET /task/{id}`, which also picks up results. Duplicate events are
  harmless, since rescheduling is idempotent.
- **Polling stays as the fallback, at a longer backoff** (for example 30–60 s, configurable) when
  events are enabled. A lost event only delays completion detection to the next poll, so no outbox is
  needed.

**Effect:** completion is noticed in well under a second instead of up to 5 s. Task polling drops by
the backoff ratio, about 6–12× for long operations, which also shrinks what Phase 6 has to optimize.

**Cost:** an Orca change, plus an opt-in messaging dependency (Redis Streams or SQS) on both services.
Typical-scale orgs keep polling by default.

### Testing

- Extend the Phase 0 TCK: claim exclusivity under concurrent claimers, fencing rejection after an
  epoch change, reaper outcomes for each `progress` state (not started / in flight with saga / in
  flight without saga), and lease expiry driven by DB time with skewed pod clocks.
- **Load test at the target scale** before 5b leaves opt-in: about 33 ops/s, about 4,000 in flight,
  about 800 polling reads/s, against a `task_states` table pre-seeded to about 200M rows. Measure
  claim latency, lock waits, replication lag, and cleanup duration.
- MySQL and Postgres via `kork-sql-test`. Run with `--max-workers=1` locally.
- A chaos-style integration test: two clouddriver contexts on one DB. Kill one mid-operation and
  assert the task is resumed (saga), re-run (op not started) or failed (op in flight), each within
  one TTL.

## Phase 6: read-path optimization (and optional write coalescing)

Independent of dispatch. It can land any time after Phase 2. Reads first, because they dominate.

- **Cheap unchanged polls.** Phase 2 keeps a per-task version on the task row (`tasks.next_seq`, which
  advances on every status, result and output write). A poll first reads `(current_state, next_seq)`
  by primary key. If a small per-pod cache already holds the serialized task at that version, return
  it without running the 4-way `UNION`. Most polls of a running task see no change since the last
  poll, so they become a single primary-key read. This needs no Orca change, and the response is
  byte-for-byte the same.
- **Polling reads from a read pool.** Optionally route `GET /task/{id}` and `GET /task` to a
  configurable read pool or replica (kork-sql's named pools). Replica lag only shows a stale "still
  running" status, and Orca polls again. A just-created task missing on the replica shows as a 404,
  which Orca already tolerates 30 times. The version check and every read inside a write path stay on
  the primary.
- **Later, with Orca changes:** completion events (5d) cut the number of polls. `ETag`/`If-None-Match`
  on `GET /task/{id}` (304 when unchanged) cuts the cost of each one. Both are tracked under "Later" in
  the work plan.
- **Write coalescing (medium scale only, optional).** Buffer rapid non-terminal
  `updateStatus`/`updateOutput` calls per task and flush them as one multi-row insert (for example
  every 250 ms, and always before a terminal transition or result write). Terminal transitions are
  never delayed. Not worth the complexity at typical scale.

## Work plan

One PR per row, in order within each track. Tracks A and B can start in parallel. Every PR carries the
Phase 0 TCK cases for the findings it fixes (no separate tests-only PR). Run the TCK on MySQL and
Postgres (`kork-sql-test`, `--max-workers=1` locally). Update **Status** as PRs open and land.

| # | Track | PR | Covers | Depends on | Status |
|---|---|---|---|---|---|
| 1 | A: correctness | Return 503 (not 500) when the task store is unavailable | R1 | – | Not started |
| 2 | A: correctness | Make the SQL `sqlTransaction`/`sqlRead` retries actually run | S6 | – | Not started |
| 3 | A: correctness | Redis: persist saga IDs, TTL on `kato:taskmap:*`, null-safe `list()` | R5, R6, R7 | – | Not started |
| 4 | B: SQL foundation | Schema: per-task `seq`, `current_state`/`completed_at`, online indexes, `request_id` dedupe + unique index, backfill agent | S1–S3, S7, S9 (schema) | – | Not started |
| 5 | B: SQL foundation | Repository: row lock, `seq` ordering, `current_state` maintenance, terminal immutability, `addResultObjects` guard, duplicate-key `create()`. **Plus** processor `break` after failure (must land together) | S1–S5, S7, S8, P1 | 4 | Not started |
| 6 | B: SQL foundation | Cleanup driven by `tasks(current_state, completed_at)`, `FAILED_RETRYABLE` TTL | S9 | 5 | Not started |
| 7 | B: SQL foundation | Processor: repository-side atomic "complete if not terminal"; bounded executor with 503 + `Retry-After` when saturated | P2, R2 (SQL side) | 5 | Not started |
| 8 | C: reads | Version-checked poll cache; optional read-pool routing for task polling | Phase 6 (reads) | 5 | Not started |
| 9 | D: task queue | `clouddriver_instances` + per-instance leases, fencing on every write, `progress` checkpoints, reaper, graceful-shutdown release (inline mode) | 5a, P3 | 5, 2 | Not started |
| 10 | D: task queue | Refactor: extract the processor's execution body into an executor component (no behaviour change) | 5b prep | 7 | Not started |
| 11 | D: task queue | `task_payloads`, `PENDING`, local-first claim, claim loop, `PENDING`-depth 503, `clouddriver.operations.dispatch: queued` flag (default `inline`) | 5b | 9, 10 | Not started |
| 12 | D: task queue | Multi-instance chaos test + medium-tier load test; operator docs (TTL vs failover tradeoff, migrating Redis → SQL) | 5b exit criteria | 11 | Not started |
| – | Later | Status-write coalescing (medium scale only) | Phase 6 (writes) | 5 | Deferred |
| – | Later | Real-queue transport via transactional outbox (Streams/SQS through kork-pubsub), optional per-account partitioning | 5c | 11, #8031 | Deferred |
| – | Later | Per-account / per-resource claim fairness | 5b extension | 11 | Deferred |
| – | Later | Task-completion events → Orca `RescheduleExecution`; longer `MonitorKatoTask` fallback backoff (needs Orca change) | 5d | 5, #8031 | Deferred |
| – | Later | Orca: `ETag`/304 on task polling (needs Orca change; less valuable if 5d lands) | Read load | 8 | Deferred |
| – | Later | Redis repository hardening | Phase 3 | – | Deprioritized |

**Critical path:** 4 → 5 → 9 → 11 → 12. Track A PRs are small and independent, so land them first.

## Follow-up outside this plan: event-driven waiting across Orca

Not needed for clouddriver task operations. Noted here as a project-wide improvement to pick up
separately.

Orca has **82 `RetryableTask` implementations** that wait by polling (`Monitor*`, `WaitFor*`, …) on a
fixed backoff. The 5d pattern generalizes: a system that knows when something changed publishes a
best-effort event, Orca pushes `RescheduleExecution`, and polling stays as a slower fallback.
Starting points:

| Waiting on | Today | Event-driven option |
|---|---|---|
| Manual judgment | **Already event-driven.** The stage `PATCH` → `updateStage` → `RescheduleExecution`. | None needed. A model for the rest. |
| Child pipelines (`MonitorPipelineTask`, `MonitorMultiplePipelinesTask`) | Polls the child execution | Orca-internal: on child completion, reschedule the parent. No external dependency. |
| Bakes (`MonitorBakeTask`) | Polls rosco | Rosco publishes bake completion. |
| CI builds (`MonitorJenkinsJobTask`, `MonitorQueuedJenkinsJobTask`, Concourse, GCB, CodeBuild) | Polls igor | Igor already detects build completion for triggers. Publish it to Orca too. |
| Webhooks (`MonitorWebhookTask`) | Polls the target URL | Callback endpoint → `RescheduleExecution`. |
| Clouddriver cache convergence (`WaitForUpInstanceHealthTask`, `WaitForManifestStableTask`, …) | Polls clouddriver caches | Harder: these wait for state to converge, not for a discrete event. Probably stays polling. |

Shared pieces worth designing once:
- An event envelope keyed by execution ID.
- An Orca consumer that maps events to `RescheduleExecution`.
- Per-task configuration for the fallback backoff.

