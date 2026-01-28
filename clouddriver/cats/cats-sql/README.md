# cats-sql agent scheduler

This module provides the SQL-based agent scheduler used when `sql.scheduler.enabled=true` and `sql.enabled=true`.

## SqlClusteredAgentScheduler
- Coordination: row-level locks in `cats_agent_locks` (or `cats_agent_locks_<namespace>` when `sql.table-namespace` is set). Acquisition is an `INSERT` with `lock_expiry`; release updates or deletes the row.
- Loop cadence: scheduled poll (default every 1s via `agentLockAcquisitionIntervalSeconds`) attempts to acquire work; agent intervals still come from `AgentIntervalProvider`.
- Concurrency: bounded by dynamic config `sql.agent.max-concurrent-agents` (default 100); candidates are shuffled before acquisition. Agents can also be filtered by regex (`sql.agent.enabled-pattern`) and disabled list (`sql.agent.disabled-agents`).
- Cleanup: expired DB locks are removed during candidate selection; in-memory zombie detection uses `sql.agent.zombie-threshold-ms` (default 1h) and cancels futures. Lock TTL updates are skipped and rows deleted when `sql.agent.release-threshold-ms` (default 500ms) would otherwise be too small.
- Table namespace: optional `sql.table-namespace` creates a namespaced lock table by cloning the reference schema on startup.

## Notes
- Row-level locks add database writes for each acquisition/release; observed to handle production cache loads.
- Redis schedulers (priority, clustered, clustered-sort) live in [`cats/cats-redis`](../cats-redis/README.md).

