# Caching agent scheduler at scale: Streams vs SQL

**Status**: Deferred. Initial analysis only; no work scheduled. Come back to this with a fuller
comparison against the existing schedulers (see [Open questions](#open-questions-for-the-follow-up))
before proposing any change.

Related: [`clouddriver-task-repository.md`](clouddriver-task-repository.md). Its Phase 5 builds the
SQL claim, per-instance lease and reaper machinery that a SQL scheduler would reuse.

## Context

Clouddriver has four `AgentScheduler` implementations:

| Scheduler | Module | Coordination |
|---|---|---|
| `DefaultAgentScheduler` | `cats/cats-core` | Single instance, no coordination |
| `ClusteredAgentScheduler` | `cats/cats-redis` | Redis locks per agent |
| `SqlClusteredAgentScheduler` | `cats/cats-sql` | SQL lock table |
| `PubSubAgentScheduler` + `PubSubAgentRunner` | `cats/cats-pubsub` (`@Alpha`) | SQL state machine (`pubsub_agent_state`) for state, claiming and recovery; Redis Streams consumer group for dispatch only |

This analysis started from the question "should `cats-pubsub` have used pure SQL instead of
Streams?" It only covers `cats-pubsub` in depth so far.

## Target scale

- 2,000+ AWS/ECS accounts × 15–20 agents ≈ **80,000 agents**.
- Agent durations range from seconds to several minutes. The default interval is 30 s
  (`RedisConfigurationProperties.intervalSeconds`).
- Each agent runs about once per (duration + interval):

| Average duration | Executions per second | Concurrent executions (Little's law) |
|---|---|---|
| 60 s | about 890 | about 53,000 |
| 2 min | about 530 | about 64,000 |

- About 40–60 replicas, each running hundreds to thousands of agents at once.

## How `cats-pubsub` works today

- **State and claiming are SQL.** `pubsub_agent_state` holds each agent's state. A conditional UPDATE
  (`StateMachine.tryTransition`) is the single-execution mutex. The stuck-RUNNING and stale-PENDING
  sweeps are the retry authority.
- **Dispatch is Redis Streams.** The scheduler sets an agent to PENDING in SQL, then `XADD`s it.
  Runners read through the consumer group, gated by a semaphore sized to `maxConcurrentAgents`
  (default 100), and `XACK` after the claim decision.
- Every replica runs the scheduler every 15 s. There's no leader election.

## Initial findings (code path, not load tested)

1. **The scheduler, not the dispatcher, breaks first at this scale.**
   - **Full scans:** every replica reads every idle row each cycle (`listAgentsFilteredWhereIn` over
     all queueable states) and works out in Java which agents are due. At 50 replicas that's
     **70–130k rows/s read**.
   - **Claim races:** every replica races `tryTransition` → PENDING for each due agent. That's about
     50 UPDATE attempts per execution, roughly **25–50k UPDATEs/s**, almost all matching zero rows,
     and bursty because the cycles run in lockstep.
2. **The SQL write and the `XADD` aren't atomic.** If the `XADD` fails after the PENDING transition,
   that agent stalls until the stale-PENDING sweep, **20 minutes** by default
   (`minutesBeforeReQueueOfAgents`). At about 1,000 dispatches/s, even a brief Redis blip affects many
   agents.
3. **Streams' latency advantage goes unused.** Work enters the stream once per 15 s cycle, for agents
   that run every 30 s or more.
4. **A stuck-RUNNING requeue can run the same agent twice.** The comment at
   `PubSubAgentScheduler.java:163` says the original execution "will continue to hold the redis lock",
   but `cats-pubsub` has no lock. If the original is only slow, the agent runs twice, and the late
   completion write overwrites the newer state. There's no fencing. Caching agents are mostly safe to
   repeat, but the comment is wrong.
5. **State timestamps use the pod clock** (`System.currentTimeMillis()` in `tryTransition` and the
   sweeps). This is less serious than in the task repository because the windows are minutes long,
   but it has the same class of problem.

## What Streams genuinely offers at this scale

- **Contention-free hand-out.** Redis is single-threaded, so each record goes to exactly one consumer,
  and the follow-up SQL claim almost always succeeds on the first try.
- **Work-conserving load balancing** across agents whose durations vary a lot: runners pull only
  when they have free slots.
- **No database polling.** Runners block on Redis instead.

## What a pure-SQL design would need to match it

1. **Store when each agent is next due.** Add `next_run_at`, written when an agent finishes (last run
   plus its interval, or plus its error interval after a failure). Index on (state, `next_run_at`).
   This replaces the full scan per replica with an indexed range read, and removes the separate
   scheduler pass and PENDING state.
2. **Claim in batches with `SKIP LOCKED`.** Use `SELECT … WHERE state = idle AND next_run_at <= now()
   ORDER BY next_run_at LIMIT <free slots> FOR UPDATE SKIP LOCKED`, then UPDATE those rows in the same
   transaction, at READ COMMITTED (no gap locks). At 1,000 claims/s in batches of 20, that's about 50
   transactions/s.
3. **Batch completion writes**, flushed per replica about once a second: about 50 statements/s.
4. **Lease per instance, not per agent.** Each replica renews one row (`instance_id`, `epoch`,
   `expires_at`, using DB time). Claimed rows record that owner and epoch. When a replica's lease
   expires, a single statement reclaims all of its work, and writes are fenced on the epoch.
   Heartbeats drop from about 2–3k writes/s (per-agent leases) to about 2/s.

**Estimated total:** about 100–200 statements/s touching about 2k rows/s, on an 80k-row table. That
should be well within MySQL 8 / Aurora, and small next to the caching writes those agents already make
if the cache is in SQL.

**Things SQL does that Streams can't:**
- Most-overdue-first ordering.
- Caps per account or provider across 2,000 accounts.
- On-demand refreshes jumping the queue (`next_run_at = now()`).
- A single store, with no lost-message window.

## Tentative conclusion

At 80k agents, both "Streams dispatch + SQL state" and "batched `SKIP LOCKED` SQL" can work. The
current bottleneck is the per-replica full scan and the race to claim every agent, not the transport.
Pure SQL is the leading option: one store, no lost-message window, and priority/fairness for free.
**This needs a load test before any decision.**

## Open questions for the follow-up

- **Compare against `ClusteredAgentScheduler` (cats-redis) and `SqlClusteredAgentScheduler`
  (cats-sql).** How each one decides what's due, claims work, detects dead owners and handles agents
  that run longer than their interval. Measure each one's cost per execution at the target scale.
  Find out which installs run which scheduler today, and why `cats-pubsub` was started.
- **Load test design:** an 80k-row state table, about 50 simulated replicas, about 1k claims/s, with
  mixed durations from seconds to several minutes. Measure claim latency, deadlocks or lock waits, and
  replication lag, on MySQL 8, Aurora MySQL and Postgres 16.
- **Per-account fairness.** Does one 2,000-account provider starve others today?
- **Sharding.** Is it worth assigning replicas a hash range of agents, with work stealing, to reduce
  contention further? The cost is load imbalance from long-running agents.
- **Overlap with task Phase 5.** Should the claim, per-instance lease and reaper code be one shared
  kork-sql component used by both tasks and agents?
- **The double-execution gap** (finding 4). Fix it with fencing in the current `cats-pubsub` first,
  regardless of the bigger decision.
