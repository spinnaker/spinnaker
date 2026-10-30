# orca-core

Contains the core API of Orca.

## Internal stage locks

The Redis implementation of the internal stage lock renews its lease while the
stage callback is running. The former one-second heartbeat budget could stop
renewal before the first heartbeat (five seconds by default), allowing another
worker to acquire the lock during a long callback. Every active lock now receives
heartbeats independently, with at most one pending renewal per request. Completion
stops new renewals, waits for that request's pending heartbeat, and releases its
latest confirmed version. Coordination and release run on a virtual thread with a
combined wait bounded by one configured lease period (ten seconds by default).
Interruption stops waiting immediately and preserves the interrupt flag. Neither
renewal nor release holds the request monitor during Redis I/O.

If cleanup times out or is interrupted, its worker and any pending heartbeat are
canceled. A Redis operation already in progress may still finish if the driver
ignores interruption, but no further heartbeats are dispatched.
An unresolved renewal is not followed by a release using a guessed version; any
remaining lease expires. A release already in progress still checks the exact
owner and version, so it cannot release a later acquisition with another version.

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
still use public `releaseLock` overrides, now on the cleanup virtual thread, and
can report callback success with `isReleased() == false`; failed release does not
become a callback exception. The legacy `FIND_SCRIPT` constant is retained for
compatibility, but completion no
longer uses it.

Acquisition contention can be retried. Once a callback has started, callback
exceptions are surfaced as nonretryable `LockCallbackException`s. A callback that
completed before cleanup or ownership verification failed instead produces a
nonretryable `LockCleanupException`. This distinction applies even when
`throwOnAcquireFailure` is false.
`RetriableLock` tracks callback execution itself, including when the ShedLock
adapter throws during unlock instead of returning a failure result. Exceptions
while obtaining a ShedLock remain retryable acquisition failures.

Ownership loss or a cleanup timeout can be reported after the callback's changes
have been stored. `CompoundExecutionOperator` logs `LockCleanupException` and
continues rescheduling without repeating the stored update. `RunTaskHandler` logs
it and lets Keiko acknowledge the completed delivery, preventing cleanup failures
from causing task redelivery. Callback failures still propagate to Keiko and leave
the delivery unacknowledged. Normal queue delivery remains at least once, so tasks
must still tolerate redelivery for other reasons, including a lost acknowledgement.

These are expiring Redis leases. They protect long callbacks while heartbeats
succeed; a process pause, Redis failure, or network partition can still allow a
lease to expire while user code is running. Renewal does not provide fencing or
exactly-once side effects, and cannot cancel a callback that has lost ownership.
