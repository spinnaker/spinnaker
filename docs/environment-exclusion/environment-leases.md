# Environment leases

As described in the [overview](overview.md), the enforcer needs to ensure that only one process at a time can take the following two actions:
1. check that a verification / resource actuation can safely be run in an environment, E
2. record that E is now in use (e.g., a version is recorded as DEPLOYING, a verification is recorded as PENDING).

Once that the environment has been recorded as being in use, it is safe to release the lock.

In keel, this locking mechanism is implemented as a *lease*, which behaves just like a lock, except that it also has an expiration time.
Once a lease expires, another process can take the lock. 
The reason we use a lease instead of a lock is to recover from the situation where an instance crashes while holding a lock.

## Interface

A lease is acquired by calling the `EnvironmentLeaseRepository.tryAcquireLease` method.

```kotlin
val repository : EnvironmentLeaseRepository = ...
val lease = repository.tryAcquireLease(...)

// do stuff here while holding the lease

lease.close()
```

Because the `Lease` interface implements Java's *Autocloseable* interface, you can use Kotlin's *use* function to ensure that the lease always gets closed, as explained in this [Baeldung article](https://www.baeldung.com/kotlin/try-with-resources).

```kotlin
repository.tryAcquireLease(...).use{
    // do stuff here while holding the lease
}
```

## Implementation

The `SqlEnvironmentLeaseRepository` class implements the environment leases.
A lease is represented by a row in the `environment_lease` table, with the following columns:

* `uid` - lease id
* `environment_uid` - environment being locked
* `leased_by` - a string that describes who is currently holding the lease. We use hostnames for this description.
* `leased_at` - the time the lease was taken
* `comment` - human readable info to help with debugging if a human needs to examine the record (e.g., "actuation" if the lease was taken for actuation, "verification" if lease was taken for verification).

When the `tryAcquireLease` method is called, the repository checks to see if there is a record in the database that hasn't expired yet.

The duration of the lease is controlled by the `keel.environment-exclusion.lease.duration` property (see [configuration values](../config.md) for more details.

### Primary key rationale

We never expect more than one lease per environment.
Therefore, we could theoretically use the envrionment_uid as a primary key.
However, we use as separate id to handle the following scenario:

1. Instance A requests a lease for environment E.
2. The repository grants A a lease for E.
3. Instance A gets wedged.
4. Time passes that exceeds the lease duration
5. Instance B requests a lease for E.
6. The repository grants B a lease for E because A's lease is expired.
7. Instance A becomes unstuck.
8. Instance A return its lease.
9. The repository deletes the record associated with E.
10. Instance C requests a lease for E.
11. The repository grants C a lease for E(!).

Now B and C both think they have the lease.

By giving each lease its own identifier, it is safe to return an expired lease.

In addition, if we ever decide to increase the granularity of the locks, we will need to have a primary key that is not the environment uid.

