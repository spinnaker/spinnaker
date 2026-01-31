# Redis Priority Scheduler

The Redis Priority Scheduler is a distributed agent scheduler for Spinnaker Clouddriver. It coordinates agent execution across pods using Redis sorted sets and Lua scripts for atomic transitions.

**Requirements**: Redis 6.2+ (for ZMSCORE support)

## Quick Start

```yaml
redis:
  scheduler:
    enabled: true
    type: priority
  agent:
    max-concurrent-agents: 100  # Instance-wide limit; ≤0 = unbounded mode
```

## Architecture

### Components

```
┌─────────────────────────────────────────────────────┐
│                PriorityAgentScheduler                │
│  (Main orchestrator - runs periodic scheduling loop) │
└────────────────┬───────────────────────────────────┘
                 │
    ┌────────────┼────────────┬─────────────┬──────────┐
    ▼            ▼            ▼             ▼          ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│  Agent    │ │  Redis   │ │  Zombie  │ │  Orphan  │ │ Priority │
│Acquisition│ │  Script  │ │ Cleanup  │ │ Cleanup  │ │ Scheduler│
│  Service  │ │ Manager  │ │ Service  │ │ Service  │ │  Metrics │
└──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘
                              │
                         Redis Backend
                    ┌──────────────────┐
                    │ waiting (ZSET)   │  Agents ready to run
                    │ working (ZSET)   │  Agents currently executing
                    │ cleanup-leader   │  Leadership for orphan cleanup
                    └──────────────────┘
```

### Redis Data Model

| Set | Score | Purpose |
|-----|-------|---------|
| **waiting** | Next execution time (epoch seconds) | Agents ready to run. Lower scores = higher priority |
| **working** | Completion deadline (acquire_time + timeout) | Agents currently executing. Used for zombie detection |

All operations use atomic Lua scripts to prevent race conditions. Scripts self-heal on NOSCRIPT errors by logging a warning and transparently reloading and retrying. If retry fails, falls back to EVAL with a degraded performance warning.

### Key Features

- **Safety**: Dual circuit breakers, rejection handling with fair requeueing, automatic cleanup
- **Atomicity**: All state transitions via Lua scripts
- **Scalability**: Batch operations, bounded ready scans, cached Redis TIME calls
- **Observability**: Comprehensive metrics, health states, periodic diagnostics

### Time Synchronization

The scheduler uses Redis TIME to coordinate scoring across pods and handle clock skew:

- Computes a `serverClientOffset` by comparing Redis server time to the local clock
- Offset is cached for `time-cache-duration-ms` (default 10s) to reduce Redis calls
- All scores are computed as **seconds since epoch** (not milliseconds) for ZSET consistency
- Scores round up for non-negative delays to avoid scheduling in the past due to truncation

## Agent Lifecycle

### States and Transitions

1. **Registration**: Agent added to waiting set with score = now (+ optional jitter)
2. **Acquisition**: Atomic move from waiting -> working with deadline score
3. **Execution**: Thread pool submission with semaphore-based concurrency control
4. **Completion**: Remove from working, reschedule to waiting with next run time
5. **Cleanup**: Zombie and orphan detection/recovery

### Completion Behavior

| Outcome | Action |
|---------|--------|
| Success | Reschedule: `next_run = acquire_time + interval` |
| 403/AccessDenied | Long backoff (e.g., 30 minutes) |
| Throttled (429) | Exponential backoff with cap |
| Transient/Server error | Error interval with optional retries |

#### Failure Classification

Failures are classified to determine backoff strategy:

| Class | Detection | Behavior |
|-------|-----------|----------|
| **PERMANENT_FORBIDDEN** | HTTP 403, AccessDenied | Fixed long backoff (`permanent-forbidden-backoff-ms`) |
| **THROTTLED** | HTTP 429, rate limit errors, `OutOfMemoryError` | Exponential: `base × multiplier^(streak-1)`, capped |
| **TRANSIENT** | Socket/IO errors, connection timeouts | Immediate retry up to `max-immediate-retries`, then error interval |
| **SERVER_ERROR** | HTTP 5xx | Same as TRANSIENT |
| **UNKNOWN** | Other failures | Agent's configured `errorInterval` |

Local failure streaks are tracked per-agent and reset on success. Jitter is applied to all non-zero backoff delays when configured.

#### Schedule Recovery Queue

When Redis scheduling fails after retry exhaustion (e.g., during completion rescheduling), agents are queued for recovery on the next scheduler cycle. This prevents silent agent loss:

- Failed agents are queued to `scheduleRecoveryQueue` with retry attempt tracking
- Recovery is attempted during `saturatePool` Phase 2 (before acquisition)
- Maximum 3 recovery attempts to prevent infinite loops during Redis outages
- After 3 failures, agents are dropped (recovered by periodic repopulation when Redis returns)
- Circuit breaker protection: recovery is skipped entirely when breaker is OPEN
- Metrics: `schedule.retryExhausted` (failures), `schedule.recovery` (success/fail tagged)

### Cleanup Services

**Zombie Cleanup** (per-pod): Detects agents running beyond `deadline + threshold` on the local instance. Cancels the future and removes from working set.

**Orphan Cleanup** (cluster-wide): Leader-elected cleanup of agents from crashed pods. Working orphans are moved back to waiting; invalid orphans are removed.

#### Dead-man Timeout

When zombie cleanup is enabled, each acquired agent schedules a proactive cancellation timer at `deadline + threshold`. If the agent is still running when this fires, it's interrupted and its permit released immediately—before the periodic zombie scan runs. This bounds maximum agent runtime more precisely than periodic scanning alone.

### Graceful Shutdown

On shutdown (`@PreDestroy`), the scheduler:

1. Signals shutdown to prevent new work acquisition
2. Interrupts running agent futures (permits released when threads exit)
3. Conditionally moves owned working-set entries back to waiting (with optional jitter)
4. Waits for executor termination with configurable timeouts

Agents interrupted during shutdown are re-queued for pickup by other pods, enabling zero-downtime rolling deployments.

### Dynamic Reconciliation

The scheduler periodically (every `refresh-period-seconds`) reconciles agent registrations with the current shard/enablement state:

- Newly-owned agents (from shard changes) are registered without restart
- No-longer-owned agents are unregistered automatically
- Budget-limited to avoid blocking the main scheduler loop

## Configuration Reference

### Agent Settings (`redis.agent.*`)

```yaml
redis:
  agent:
    max-concurrent-agents: 100    # Instance-wide semaphore limit; ≤0 = unbounded mode
    enabled-pattern: ".*"         # Regex for agent inclusion
    disabled-pattern: ""          # Regex for agent exclusion (takes precedence)
```

**Unbounded Mode** (`max-concurrent-agents: 0` or negative): Disables the semaphore entirely. All eligible agents run immediately without concurrency limits. Use with caution—memory and network pressure are uncapped. This mode is useful for testing or environments where external rate limiting is already in place.

### Scheduler Settings (`redis.scheduler.*`)

```yaml
redis:
  scheduler:
    enabled: true
    type: priority
    interval-ms: 1000               # Scheduling loop frequency (default: 1s)
    refresh-period-seconds: 30      # Agent sync to Redis (default: 30s)
    health-summary-period-seconds: 600  # Health log interval (default: 10min, ≤0 disables)
    time-cache-duration-ms: 10000   # Redis TIME cache (default: 10s)
    
    batch-operations:
      enabled: true                 # Enable batch Redis operations
      batch-size: 0                 # Agents per batch (≤0 = unlimited)
      chunk-attempt-multiplier: 0.0 # Extra scan attempts for filtered agents
    
    jitter:
      initial-registration-seconds: 0    # Default: disabled; set >0 to spread new agents
      shutdown-seconds: 0                # Default: disabled; set >0 to spread re-queued agents on shutdown
      failure-backoff-ratio: 0.1         # Default: ±10% randomization on retries
    
    failure-backoff:
      enabled: false                     # Default: false (disabled); set true to enable failure-aware backoff
      max-immediate-retries: 0           # Default: 0; immediate retries before applying errorInterval
      permanent-forbidden-backoff-ms: 1800000  # Default: 30 min backoff for 403/AccessDenied
      throttled:
        base-ms: 30000                   # Default: 30s; starting backoff for throttled errors
        multiplier: 2.0                  # Default: 2.0; exponential factor per consecutive failure
        cap-ms: 600000                   # Default: 10 min; maximum backoff cap
```

### Circuit Breaker (`redis.scheduler.circuit-breaker.*`)

```yaml
redis:
  scheduler:
    circuit-breaker:
      enabled: true           # Protection against cascading failures
      failure-threshold: 5    # Failures to trip
      failure-window-ms: 10000
      cooldown-ms: 30000      # Wait before testing recovery
      half-open-duration-ms: 5000
```

### Zombie Cleanup (`redis.scheduler.zombie-cleanup.*`)

```yaml
redis:
  scheduler:
    zombie-cleanup:
      enabled: true
      threshold-ms: 30000     # Buffer beyond deadline (default: 30s)
      interval-ms: 300000     # Scan frequency (default: 5min)
      run-budget-ms: 0        # Max runtime per pass; 0 disables budget
      executor-shutdown-await-ms: 10000      # Graceful shutdown wait
      executor-shutdown-force-await-ms: 5000 # Forced shutdown wait
      exceptional-agents:
        pattern: ""            # Regex for long-running agents (empty = disabled)
        threshold-ms: 3600000   # Buffer for exceptional agents (default: 60m)
```

### Orphan Cleanup (`redis.scheduler.orphan-cleanup.*`)

```yaml
redis:
  scheduler:
    orphan-cleanup:
      enabled: true
      threshold-ms: 600000       # Orphan age threshold (default: 10min)
      interval-ms: 300000        # Scan frequency (default: 5min)
      leadership-ttl-ms: 120000  # Leadership lock duration
      force-all-pods: false      # All pods run cleanup (bypasses election; shard gating preserved)
      remove-numeric-only-agents: true  # Repair corrupted entries
      run-budget-ms: 0           # Max runtime per pass; 0 disables budget
      executor-shutdown-await-ms: 10000      # Graceful shutdown wait
      executor-shutdown-force-await-ms: 5000 # Forced shutdown wait

### Reconcile Executor (`redis.scheduler.reconcile.*`)

```yaml
redis:
  scheduler:
    reconcile:
      run-budget-ms: 0                 # Max runtime per reconcile pass; 0 disables
      executor-shutdown-await-ms: 5000 # Graceful shutdown wait
      executor-shutdown-force-await-ms: 2000 # Forced shutdown wait
```
```

### Redis Key Configuration

Customize Redis key names and namespacing:

```yaml
redis:
  scheduler:
    keys:
      prefix: ""                         # Optional namespace prefix (e.g., "myapp:")
      waiting-set: waiting               # Base name for waiting set (default)
      working-set: working               # Base name for working set (default)
      cleanup-leader-key: cleanup-leader # Leadership key for orphan cleanup (default)
      hash-tag: ""                       # Hash tag for Redis Cluster (e.g., "ps")
```

**Redis Cluster Support**: For Redis Cluster, set `hash-tag` to ensure multi-key Lua scripts operate within a single slot. Example with `hash-tag: ps` and `prefix: myapp:`:

- `myapp:waiting{ps}`
- `myapp:working{ps}`
- `myapp:cleanup-leader{ps}`

Without hash tags, multi-key operations will fail with CROSSSLOT errors.

### JedisPool Configuration (External)

The scheduler uses an externally-configured `JedisPool` for Redis connections. For production deployments, ensure the pool is configured with appropriate timeouts to prevent thread starvation:

```yaml
# Example JedisPool configuration (framework-specific)
redis:
  connection:
    pool:
      max-wait-millis: 5000     # Max time to wait for a connection from the pool
      max-total: 50             # Maximum pool size
      max-idle: 10              # Maximum idle connections
      min-idle: 2               # Minimum idle connections
    timeout: 3000               # Socket timeout for read/write operations (ms)
    connect-timeout: 2000       # Connection establishment timeout (ms)
```

**Critical timeout settings:**

| Setting | Purpose | Recommended |
|---------|---------|-------------|
| `max-wait-millis` | Bounds `getResource()` blocking time | 5000ms |
| `timeout` (socket) | Bounds individual Redis operations | 3000ms |
| `connect-timeout` | Bounds initial connection establishment | 2000ms |

**Without these timeouts**, the scheduler can experience:
- **Pool exhaustion**: All threads blocked waiting for connections indefinitely
- **Socket hangs**: Operations blocked forever on unresponsive Redis (half-open TCP)
- **Cascading failures**: Slow Redis responses cause thread pool saturation

The pool configuration is typically managed by the hosting framework (e.g., `redis.connection.*` in Spinnaker's `clouddriver.yml`).

## Operations

### Health States

| State | Meaning |
|-------|---------|
| **HEALTHY** | Normal operation, queue processing within expected time |
| **DEGRADED** | Issues detected: backlog exceeds threshold or slot filling problems |

Health summary logged at `health-summary-period-seconds` interval (default: 10 minutes):
```
Scheduler health | health=HEALTHY | [agents registered=500 active=futures=45 scripts=11] [backlog ready=10 oldest_overdue=0s capacity_per_cycle=50] [permits 55/100 (55.0%)] [cleanup zombies_cleaned=2 orphans_cleaned=0] queue_depth=5
```

### Key Metrics

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `cats.priorityScheduler.scheduler.oldestOverdueSeconds` | Age of oldest waiting agent | > 2x average interval |
| `cats.priorityScheduler.scheduler.degraded` | Health state (0=healthy, 1=degraded) | = 1 for > 5 minutes |
| `cats.priorityScheduler.acquire.acquired` | Agents acquired per cycle | < expected rate |
| `cats.priorityScheduler.scheduler.readyCount` | Locally eligible agents (pod-scoped) | > 10x capacity |
| `cats.priorityScheduler.scripts.errors` | Lua script failures | > 0 |

Additional metrics:
- `cats.priorityScheduler.circuitBreaker.trip` / `.blocked` / `.recovery`
- `cats.priorityScheduler.acquire.submissionFailures`
- `cats.priorityScheduler.scheduler.capacityPerCycle`
- `cats.priorityScheduler.scheduler.semaphoreAvailable`
- `cats.priorityScheduler.schedule.retryExhausted` - Redis scheduling failures after retries
- `cats.priorityScheduler.schedule.recovery` - Recovery attempts (tagged `success=true/false`)
- `cats.priorityScheduler.cas.contention` - Permit CAS contention (tagged by `location`)

### Watchdog Signals

| Signal | Meaning | Action |
|--------|---------|--------|
| permit_leak_suspect | Few permits, idle pool, backlog | Thread dump; consider restart |
| capacity_skew | Free permits but low acquisition | Check running agents vs permits |
| zero_progress | Ready agents but none acquired | Check filters/patterns |
| redis_stall | Circuit breaker not CLOSED | Check Redis connectivity |

### Troubleshooting

| Issue | Symptoms | Resolution |
|-------|----------|------------|
| High backlog | `oldestOverdueSeconds` increasing | Add replicas or increase `max-concurrent-agents` |
| Circuit breaker trips | Acquisition failures | Check Redis connectivity and latency |
| Zombie agents | Agents stuck in working set | Tune `zombie-cleanup.threshold-ms` |
| Slot filling issues | High filter rate warnings | Audit enabled/disabled patterns |
| Memory growth | Increasing heap, OOM | Reduce `max-concurrent-agents` or batch size |

### Redis Commands

```bash
redis-cli zcard waiting                      # Waiting queue size
redis-cli zcard working                      # Working queue size
redis-cli zrange waiting 0 10 WITHSCORES     # View oldest waiting agents
redis-cli zscore waiting "agent-name"        # Check specific agent
redis-cli get cleanup-leader                 # Check cleanup leadership
```

Debug logging: `com.netflix.spinnaker.cats.redis.cluster: DEBUG`

### Operational Procedures

## Performance Tuning

Tips:
- Set `max-concurrent-agents` based on memory and network capacity
- Increase `batch-size` if network latency is high
- Use jitter for large agent counts to spread load

## Comparison with the Default Redis Scheduler

| Feature | Default Scheduler | Priority Scheduler |
|---------|------------------|-------------------|
| Coordination | Distributed locks (SET NX PX) | Sorted sets (ZADD/ZRANGEBYSCORE) |
| Scheduling | Random/FIFO | Priority-based FIFO |
| Atomicity | Basic | Lua scripts |
| Zombie Cleanup | None | Automatic |
| Orphan Cleanup | No | Yes (leader-elected) |
| Circuit Breaker | No | Yes (dual breakers) |
| Batch Operations | No | Yes |

## Agent Implementation Guidelines

### Cancellation Handling

The scheduler may cancel agent execution in these scenarios:
- **Zombie cleanup**: Agent exceeds deadline + threshold
- **Dead-man timeout**: Proactive cancellation timer fires
- **Graceful shutdown**: Pod termination interrupts active work

For cancellation to work correctly, agent implementations should:

1. **Check interrupts in loops**: Call `Thread.interrupted()` periodically in long-running operations
2. **Propagate InterruptedException**: Don't catch and swallow it — either rethrow or restore the interrupt flag with `Thread.currentThread().interrupt()`
3. **Use interruptible I/O**: Prefer NIO channels over blocking streams where possible
4. **Handle blocking calls**: Operations like `Object.wait()`, `Thread.sleep()`, and `BlockingQueue.take()` throw `InterruptedException`—handle them appropriately

Agents that ignore interrupts will continue running after cancellation, consuming resources until the thread exits and releases the permit.

## FAQ

**Q: What thread pool does the scheduler use?**  
A: Cached thread pool with `SynchronousQueue`. Concurrency controlled by semaphore (`max-concurrent-agents`).

**Q: How do I verify the scheduler is working?**  
A: Check health logs (default: every 10 minutes, configurable via `health-summary-period-seconds`) and monitor `cats.priorityScheduler.acquire.acquired`.

**Q: What happens during Redis failure?**  
A: Circuit breaker trips, blocking further Redis operations until recovery.

**Q: Can I use this with Redis Cluster?**  
A: Yes, configure hash-tagged keys (e.g., `{ps}`) for multi-key Lua scripts.

**Q: How are ties broken when multiple agents have the same score?**  
A: Redis sorted sets break ties lexicographically by member name.

**Q: What happens during a rolling deployment?**  
A: On graceful shutdown, active agents are interrupted and re-queued to the waiting set with optional jitter. Other pods pick them up immediately. No agent work is lost.

**Q: How does failure backoff work?**  
A: Failures are classified (THROTTLED, TRANSIENT, etc.) and backoff is computed per-class. Local failure streaks are tracked per-agent and reset on success. Jitter is applied to prevent thundering herds.

**Q: Do I need to restart pods for agent shard changes?**  
A: No. The scheduler reconciles agent registrations every `refresh-period-seconds`, automatically registering newly-owned agents and unregistering no-longer-owned ones.

---
