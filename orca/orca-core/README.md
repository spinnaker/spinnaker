# orca-core

Contains the core API of Orca.

## Internal stage locks

The Redis implementation of the internal stage lock renews its lease while the
stage callback is running. The former one-second heartbeat budget could stop
renewal before the first heartbeat (five seconds by default), allowing another
worker to acquire the lock during a long callback. Every active lock now receives
heartbeats independently, with at most one pending renewal per request. Completion
waits for that request's pending heartbeat before releasing its latest confirmed
version. Neither renewal nor release holds the request monitor during Redis I/O.

Renewals run on virtual threads and share the configured Redis connection pool.
There is no fixed limit on active locks in the manager. Size the pool and lease for
the instance's maximum concurrent callbacks and Redis latency: with the defaults
of a five-second heartbeat interval and a ten-second lease, scheduling delay,
connection-pool wait, and renewal must fit within the remaining five seconds.

This behavior does not enable the internal lock. Existing configuration controls
whether Orca uses it. Other users of Kork's `LockManager` retain duration-limited
renewal through `withMaximumLockDuration`; callback-lifetime renewal is an explicit
opt-in with `LockOptions.withHeartbeatsUntilReleased()`.
The last call to `withMaximumLockDuration` or `withHeartbeatsUntilReleased` selects
the renewal policy.

Release now uses the request's latest confirmed version for both policies. The
former owner-only Redis lookup could select a later acquisition by the same owner.
If Redis accepts a heartbeat but its response is lost, the local version can be
stale, so release can fail and the lease remains until expiry. Bounded callers
still use public `releaseLock` overrides and can report callback success with
`isReleased() == false`; failed release does not become a callback exception. The
legacy `FIND_SCRIPT` constant is retained for compatibility, but completion no
longer uses it.

Acquisition contention can be retried. Once a callback has started, callback
exceptions and detected ownership loss are surfaced to the caller without
retrying the callback as part of lock acquisition.
The existing ShedLock adapter already returns callback failures with
`lockAcquired=true`; `RetriableLock` now recognizes that failure result. Exceptions
while obtaining a ShedLock are still acquisition failures and can be retried.

Ownership loss can be reported after the callback's changes have been stored.
`CompoundExecutionOperator` logs the failure and returns `null` without repeating
the stage update or rescheduling the execution. `RunTaskHandler` lets the exception
reach Keiko's `QueueProcessor`, which logs it, publishes `HandlerThrewError`, and
leaves the message unacknowledged. Queue redelivery after the acknowledgement
timeout can therefore run task effects again, even though acquisition retries do
not repeat the callback.

These are expiring Redis leases. They protect long callbacks while heartbeats
succeed; a process pause, Redis failure, or network partition can still allow a
lease to expire while user code is running. Renewal does not provide fencing or
exactly-once side effects, and cannot cancel a callback that has lost ownership.
