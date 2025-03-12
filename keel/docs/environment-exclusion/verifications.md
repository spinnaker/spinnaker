
# Verifications and environment execlusion

## Behavior

The `EnvironmentExclusionEnforcer.withVerificationLease` method has the following preconditions and postconditions:

**precondition**

 * The client requests a verification lease for an environment E before attempting to start a verification in E

**postcondition**

 * if client is granted a verification lease for an environment E, there are no active verifications or deployments in E
 * if client is not granted a verification lease, the enforcer will throw an EnvironmentCurrentlyBeingActedOn exception subclass

## Implementation

The VerificationRunner.runVerificationsFor method uses the enforcer to protect the `VerificationContext.start` extension function.

```kotlin
class VerificationRunner {

  private fun VerificationContext.start(...) {
        ...

        enforcer.withVerificationLease(this) {
            start(verification, ...)
        }
```

The CheckScheduler.verifyEnvironments method handles the exception:

```kotlin
class CheckScheduler {

  fun verifyEnvironments() {
    ...
    try {
        verificationRunner.runVerificationsFor(it)
    } catch (e: EnvironmentCurrentlyBeingActedOn) {
        ...
    }

```