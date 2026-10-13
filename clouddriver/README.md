Spinnaker Cloud Provider Service
------------------------------------
[![Build Status](https://github.com/spinnaker/spinnaker/actions/workflows/clouddriver.yml/badge.svg)](https://github.com/spinnaker/spinnaker/actions/workflows/clouddriver.yml)

This service is the main integration point for Spinnaker cloud providers like AWS, GCE, CloudFoundry, Azure etc.
It lives in this monorepo at `clouddriver/`; commands below are run from the monorepo root, not
from within this directory — see [CLAUDE.md](../CLAUDE.md) for the full set of build/test commands.

### Developing with Intellij

To configure this repo as an Intellij project, run `./gradlew idea` from the monorepo root.

Some of the modules make use of [Lombok](https://projectlombok.org/), which will compile correctly on its own. However, for Intellij to make sense of the Lombok annotations, you'll need to install the [Lombok plugin](https://plugins.jetbrains.com/plugin/6317-lombok-plugin) as well as [check 'enable' under annotation processing](https://www.jetbrains.com/help/idea/configuring-annotation-processing.html#3).

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true` (run from the monorepo root):
```
./gradlew clouddriver -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 7102.  The JVM will _not_ wait for
the debugger to be attached before starting Clouddriver; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.

### SQL account refresh through broadcast notifications

SQL-backed accounts can opt in to broadcast-triggered credential loading on every Clouddriver
replica. Apply the SQL migrations, then enable this configuration on all replicas that share the
account database:

```yaml
sql:
  enabled: true
account:
  storage:
    enabled: true
    pubsub:
      enabled: true
      topic: clouddriver-account-definitions
spring:
  data:
    redis:
      host: redis
      port: 6379
```

The Redis connection uses `spring.data.redis.*`, including its authentication, TLS, and timeout
settings. Use the same Redis database and topic on all replicas. Deployments with different
account databases should use different topics. The option works independently of
`cats.pubsub.enabled`; the agent scheduler need not be enabled.

When enabled, committed account `create`, `save`, `update`, and `delete` operations add an entry to
a transactional SQL outbox. A dispatcher broadcasts `accounts-changed` after commit and removes
the outbox entry after successful publication. Failed writes and rolled-back transactions leave no
notification. Redis failures retain committed entries for retry. Notifications contain only a
constant reload signal; no account definition, credentials, secret values, or secret references
are sent through Redis.

Every replica independently reloads its credential loaders from their normal sources and
acknowledges only after loading succeeds. Failed loads leave notifications pending for recovery.
Duplicate notifications are safe because loading reconciles the current definitions. Startup
loading remains enabled, with an additional load after subscription startup to cover changes
between initial loading and readiness. A replica starting later loads current SQL state rather
than replaying earlier broadcasts.

| Property | Default | Meaning |
|----------|---------|---------|
| `account.storage.pubsub.enabled` | `false` | Enable SQL outbox publication and broadcast refresh, replacing periodic credential polling. |
| `account.storage.pubsub.topic` | `clouddriver-account-definitions` | Shared broadcast topic for this account database. |
| `account.storage.pubsub.publish-interval-ms` | `1000` | Delay between outbox publication attempts. |
| `account.storage.pubsub.maintenance-interval-ms` | `60000` | Delay between pending-message recovery and stream trimming. |
| `account.storage.pubsub.reclaim-idle-ms` | `60000` | Minimum idle time before retrying an unacknowledged notification; must be nonnegative. |
| `account.storage.pubsub.stream-max-length` | `100000` | Approximate retained stream length; must be positive and cover the expected recovery backlog. |

When the option is disabled, the existing `credentials.poller.enabled` and
`credentials.poller.types.<type>.reload-frequency-ms` settings continue to control polling.
Polling also remains available if SQL or account storage is disabled. Startup loading is preserved
in every configuration. Enable broadcast mode only after all account writers and replicas have
been upgraded; older writers do not populate the outbox. To roll back, disable the option on all
replicas and restore the usual polling settings. Keep the outbox table while queued notifications
remain or any enabled writer still runs.

The shared transport and its delivery/recovery semantics are documented in
[Kork pubsub](../kork/kork-pubsub/README.md). Relevant tests (Docker required for Redis and MySQL):

```bash
./gradlew :kork:kork-pubsub:test :clouddriver:cats:cats-pubsub:test
./gradlew :clouddriver:clouddriver-sql:test --tests '*SqlAccountDefinitionNotificationsTest'
./gradlew :clouddriver:clouddriver-core:test --tests '*AccountDefinitionRefreshConfigurationTest'
```
