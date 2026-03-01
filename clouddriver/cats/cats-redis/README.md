# cats-redis agent schedulers

Redis-backed schedulers for Clouddriver caching agents live in this module. They differ in coordination model, data structures, and safety features.

## Available schedulers

### ClusteredAgentScheduler (lock-based)
- Coordination: per-agent Redis key set with `SET NX PX <timeout>` using the agent type as the key.
- Concurrency: local bound via `redis.agent.max-concurrent-agents` (dynamic config, default 1000); agents are shuffled before acquiring locks.
- Rescheduling: lock TTL is extended or deleted on completion based on the next run time; expired locks allow another pod to take over.
- Limitations: no cross-pod zombie/orphan cleanup, non-atomic acquire/run/release flow (possible double execution if a lock expires mid-run), keys are not namespaced/hash-tagged.

### ClusteredSortAgentScheduler (sorted-set)
- Coordination: two sorted sets (`WAITZ`, `WORKZ`) managed by Lua scripts; scores use Redis `TIME` plus interval/deadline.
- Concurrency: optional semaphore (`parallelism` constructor arg) for local max in-flight agents.
- Loop cadence: single-threaded scheduler loop every 1 second scans ready work (`ZRANGEBYSCORE`).
- Notes: fixed key names (no prefix/hash tag); no built-in cleanup/circuit breaker; registration and ownership changes are script-driven.

### PriorityAgentScheduler (sorted-set with priority)
- Coordination: `waiting` and `working` sorted sets; lower scores = higher priority. All state transitions are Lua-backed and atomic.
- Safety/cleanup: dual circuit breakers, failure-aware backoff (opt-in), zombie and orphan cleanup, dead-man timeout, schedule recovery queue, dynamic agent reconciliation.
- Performance: batch Redis operations, bounded ready scans, cached Redis `TIME` offset; concurrency via semaphore with optional unbounded mode.
- Requirements: Redis 6.2+ (uses `ZMSCORE`); supports namespacing and Redis Cluster hash tags via configuration.
- Docs: see [`docs/priority-scheduler.md`](docs/priority-scheduler.md) for full behavior and configuration reference.

## Cross-module notes
- Redis options live here; SQL coordination is in [`cats/cats-sql`](../cats-sql/README.md).
- Select based on your datastore and required guarantees (cleanup, circuit breakers, backoff, namespacing).

