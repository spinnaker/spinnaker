# orca-core

Contains the core API of Orca.

## Internal stage locks

The Redis implementation of the internal stage lock renews its lease while the
stage callback is running. The former one-second heartbeat budget could stop
renewal before the first heartbeat (five seconds by default), allowing another
worker to acquire the lock during a long callback. Every active lock now receives
heartbeats, and completion waits for any heartbeat of that lock before releasing
its latest version.

This behavior does not enable the internal lock. Existing configuration controls
whether Orca uses it. Other users of Kork's `LockManager` retain duration-limited
renewal through `withMaximumLockDuration`; callback-lifetime renewal is an explicit
opt-in with `LockOptions.withHeartbeatsUntilReleased()`.

Acquisition contention can be retried. Once a callback has started, callback
exceptions and detected ownership loss are surfaced to the caller without
retrying the callback as part of lock acquisition.

These are expiring Redis leases. They protect long callbacks while heartbeats
succeed; a process pause, Redis failure, or network partition can still allow a
lease to expire while user code is running. Renewal does not provide fencing or
exactly-once side effects, and cannot cancel a callback that has lost ownership.
