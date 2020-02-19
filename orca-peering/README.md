# Orca Peering

This is an semi-experimental approach to solve the problem of having multiple `orca` installations (each with its own database) communicate changes with each other, for instance in a multi-region Spinnaker installation or during a database migration.
 
**Definitions:**
* `peer`
    An `orca` cluster whose database (can be a read replica) we copy data from. Each `orca` cluster has an ID, for example `us-east-1` or `us-west-2`.  
    A peer is defined by specifying its database connection AND its ID (in the yaml config).  
    For example, `orca` cluster with ID `us-west-2` could peer `orca` cluster with ID `us-east-1`, and vice-versa

* `partition`
    The executions stored in a DB are tagged with a partition, this is synonymous with peer ID described above.  
    When an execution is "peered" (copied) from a peer with ID `us-east-1` that execution will be persisted in our local database with the `partition` set to `us-east-1`.  
    *Note:* for historical reasons, the partition has been omitted in the executions.
    Therefore, an `orca` cluster will consider executions with `partition = NULL` OR `partition = MY_PARTITION_ID` to be owned by this cluster. 

* `foreign executions`
    Foreign executions are executions that show up in the local database but are marked with `partition` of our peer.  
    These executions are essentially read-only and the current `orca` cluster can't perform any actions on these executions.


The peering mechanism accomplishes a few things:
1. (complete) Peer (copy) executions (both pipelines and orchestrations) from a database of a peer to the local cluster database
2. (in-progress) Allow for an `orca` cluster to perform actions on a foreign execution (e.g. executions running on a peer)
3. (still to come) Take ownership and resume an execution previously operated on by a peer


### Execution peering
Execution peering is essentially copying of executions from one database to another.  
In a typical topology for `orca` a single `orca` cluster will use a single [sql] database.
The database stores all execution history as well as the execution queue.
The history needs to be peered but the queue not be peered/replicated as that would cause issues with duplicate executions, etc.
(additionally, the queue is extremely high bandwidth/change rate so replicating it would be difficult/require a lot of overhead on the DB)

Logic for peering lives in [PeeringAgent.kt](./src/main/kotlin/com/netflix/spinnaker/orca/peering/PeeringAgent.kt), see comments for details on the algorithm.  
At a high level the idea is:
* Given a peer ID and its database connection (can be pointed to readonly replica)
* Mirror all foreign executions with the specified peer ID to the local database
* During copy, all executions get annotated as coming from the specified peer (`partition` column)
* Any attempt to operate on a foreign execution (one with `partition != our ID`) will fail 


### Taking actions on foreign executions
The user can perform the following actions on an execution via the UI/API (`orca` mutates the execution based on these actions):  
* *cancel* an execution
* *pause* an execution
* *resume* an execution
* *pass judgement* on an execution
* *delete* an execution

These operations must take place on the cluster/instance that owns the execution.
TBD 


### Taking ownership of an execution 
TBD


### Caveats
* Only MySQL is supported at this time, but this could easily be extended by a new [SqlRawAccess](./src/main/kotlin/com/netflix/spinnaker/orca/peering/SqlRawAccess.kt) implementation for the given DB engine
* It is recommended that only one instance run the `peering` agent/profile. This will likely be improved on in the future but today, cross instance locking is not there


## Operating notes
Consider this reference `peering` profile in `orca.yml`:

```yaml
spring:
  profiles: peering

pollers:
  peering:
    enabled: true
    poolName: foreign
    id: us-west-2
    intervalMs: 5000   # This is the default value
    threadCount: 30    # This is the default value 
    chunkSize: 100     # This is the default value
    clockDriftMs: 5000 # This is the default value

queue:
  redis:
    enabled: false

keiko:
  queue:
    enabled: false

sql:
  enabled: true
  foreignBaseUrl: URL_OF_MYSQL_DB_TO_PEER_FROM:3306
  partitionName: LOCAL_PARTITION_NAME

  connectionPools:
    foreign:
      jdbcUrl: jdbc:mysql://${sql.foreignBaseUrl}/orca?ADD_YOUR_PREFFERED_CONNECTION_STRING_PARAMS_HERE
      user: orca_service
      password: ${sql.passwords.orca_service}
      connectionTimeoutMs: 5000
      validationTimeoutMs: 5000
      maxPoolSize: ${pollers.peering.threadCount}
``` 

| Parameter | Default | Notes |
|-----------|---------|-------|
|`pollers.peering.enabled`          | `false`    | used to enabled or disable peering |
|`pollers.peering.poolName`         | [REQUIRED] | name of the pool to use for foreign database, see `sql.connectionPools.foreign` above |
|`pollers.peering.id`               | [REQUIRED] | id of the peer, this must be unique for each database |
|`pollers.peering.intervalMs`       | `5000`     | interval to run migrations at (each run performs a delta copy).<br> Shorter = less lag but more CPU and DB load |
|`pollers.peering.threadCount`      | `30`       | number of threads to use to perform bulk migration. A large number here only helps with the initial bulk import. After that, the delta is usually small enough that anything above 2 is unlikely to make a difference |
|`pollers.peering.chunkSize`        | `100`      | chunk size used when copying data (this is the max number of rows that will be modified at a time) |
|`pollers.peering.clockDriftMs`     | `5000`     | allows for this much clock drift across `orca` instances operating on a single DB|

### Emitted metrics
The following metrics are emitted by the peering agent and can/should be used for monitoring health of the peering system.

| Parameter | Notes |
|-----------|-------|
|`pollers.peering.lag`              | Timer (seconds) of how long it takes to perform a single migration loop, this + the agent `intervalMs` is the effective lag. This should be a fairly steady number | 
|`pollers.peering.numPeered`        | Counter of number of copied executions (should look fairly steady - i.e. mirror the number of active executions) | 
|`pollers.peering.numDeleted`       | Counter of number of deleted executions | 
|`pollers.peering.numStagesDeleted` | Counter of number of stages deleted during copy, purely informational| 
|`pollers.peering.numErrors`        | Counter of errors encountered during execution copying (this should be alerted on) | 

If using the peering feature, it is recommended that you configure alerts for the following metrics:  
* `pollers.peering.numErrors > 0`
* `pollers.peering.numPeered == 0` for some period of time (depends on your steady stage of active executions)
* `pollers.peering.lag > 60` for some period of time (~3 minutes)


### Dynamic properties
The following dynamic properties are exposed and can be controlled at runtime via `DynamicConfigService`.

| Property | Default | Notes |
|----------|---------|-------|
|`pollers.peering.enabled`          | `true` | if set to `false` turns off all peering |
|`pollers.peering.<PEERID>.enabled` | `true` | if set to `false` turns off all peering for peer with given ID | 
