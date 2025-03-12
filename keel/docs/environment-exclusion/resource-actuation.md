### Resource actuation and environment exclusion

## Behavior

The `EnvironmentExclusionEnforcer.withActuationLease` method has the following preconditions and postconditions:

**precondition**

 * The client requests an actuation lease for an environment E before attempting to update a resource in E

**postconditions**

* If client is granted a deployment lease for E, there are no active verifications in E
* if client is not granted an actuation lease, the enforcer will throw an EnvironmentCurrentlyBeingActedOn exception subclass

Note: The deployment lease doesn't check for active deployments because the ArtifacctRepository already enforces only one active deployment in an environment for a given artifact.


## Implementation

The `ResourceActuator.checkResource` method uses the enforcer to protect the `ResourceHandler.update` extension function, and handles the exceptions:

```
class ResourceActuator {

    fun checkResource(...) {
        ...
        environmentExclusionEnforcer.withActuationLease(deliveryConfig, environment) {
            plugin.update(resource, diff)
            .also{ ... }
        }
        ...
      } catch (e: ActiveVerifications) {
          ...
      } catch (e: EnvironmentCurrentlyBeingActedOn) {
          ...
      }
```

## Existing race condition: asynchronous marking of artifact as deploying

In the current implementation, keel implements the APPROVED → DEPLOYING version-in-environment repository state transition like this:

1. ResourceHandler upsert methods (e.g., `ClusterHandler.upsert`, `TitusClusterHandler.upsert`) call `notifyArtifactDeploying`
2. `notifyArtifactDeploying` emits an `artifactVersionDeploying` event.
3. The `onArtifactVersionDeploying` event handler writes DEPLOYING to the database.

Keel is configured to use a [threadpool][1] so that events are handled asynchronously.
(The original motivation was to prevent metric publishing from blocking, see [PR #268][2].)

Because the database update may not happen until after the `plugin.update` method returns, we can get race conditions.

Consider the following code, with instances A,B, environment E, version V, where A is trying to promote a version, and B is trying to verify a version:

```kotlin
enforcer.requestLease(...)
 .withLease {
    plugin.update(resource, diff)
}
```

1. A: gets lease on env E
2. A: starts deploying V in E
3. A: emits ArtifactVersionDeploying event async
4. A: releases lease on E
  *  **DANGER: There is now no lease on E, and (E,V) promotion status is still APPROVED**
5. B: gets lease (!) on env V
6. A: ArtifactVersionDeploying listener fires, writes promotion status DEPLOYING to database
7. B: starts running a verification against E

To fix this, we need to enforce that DEPLOYING gets written out to the database before the lease gets released.

[1]: https://github.com/spinnaker/keel/blob/9db12ff2949db3c38501747e6cae3de3ec11fc6f/keel-web/src/main/kotlin/com/netflix/spinnaker/config/EventsConfiguration.kt#L13
[2]: https://github.com/spinnaker/keel/pull/26