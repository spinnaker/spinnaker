# Environment exclusion

This doc describes the purpose and behavior of environment exclusion, as implemented by the  `EnvironmentExclusionEnforcer` class.

For related configuration paramters, see [config.md](config.md).
For related metrics, see [metrics.md](metrics.md).

## Context

Two [safety properties][1] we want to enforce:

P1. Two verifications should never execute concurrently against the same environment.

P2. Verification against an environment should never happen concurrently with the actuation of any resources in an environment (including any artifact being deployed to that environment).

The `EnvironmentExclusionEnforcer` class (the enforcer) exists to enforce these properties.

Note: these properties are more coarse grained than we really need. For example, it may be safe to run two different types of verifications in the same environment, or to run a verification while a resource like a security group is being updated.
In the future, we may increase the granularity of these checks in order to improve performance.

[1]: https://buttondown.email/hillelwayne/archive/safety-and-liveness-properties/


## Basic strategy

* Before starting a verification, check if any other verifications or actuations are happening in the environment.
* Before actuating a resource, check if any verifications are happening in the environment.

Our basic strategy looks like this:

```
status = get_status()
if (status == "free") {
    take_action()
    set_status("busy")
}
```

The problem is that this block of code must be executed atomically.
Otherwise, there's a risk that two separate processes both call `take_action()`.

For example, if you had two processes, P1, and P2, and they were scheduled like this:

```
Time
 |
 |    P1: status = get_status()
 |        if (status == "free") {
 |
 |                                      P2: status = get_status()
 |                                          if (status == "free") {
 |                                            take_action()
 |                                            set_status("busy")
 |          take_action()
 |          set_status("busy")
 ↓
```

We need to treat the block of code as a [critical section][2].
The enforcer implements a locking mechanism to ensure that only one process can be in the above block of code, per environment.

See [environment leases](environment-leases.md) for details on how the locking mechanism is implemented.

[2]: https://en.wikipedia.org/wiki/Critical_section


## How the enforcer is used

The enforcer works by granting leases. A client must hold a lease before either:
* starting a verification in an environemnt, by calling `EnvironmentExclusionEnforcer.withVerificationLease`
* deploying an artifact version into an environment, by calling `EnvironmentExclusionEnforcer.withActuationLease`


For how the enforcer is used, see:
* [Verifications and environment execlusion](verifications.md)
* [Resource actuation and environment exclusion](resource-actuation.md)

## How the enforcer works

The enforcer relies on an `EnvironmentLeaseRepository` implementation to implement the actual locking mechanism.
We'll call that the "low-level lease" here.

The enforcer does the following:

1. Try to get a low-level lease from the `EnvironmentLeaseRepository`.
2. If successful, check to see if the proposed action is safe.
3. If safe, execute the action.
4. Release the lease.

See [environment-leases.md](environment-leases.md) for more details.