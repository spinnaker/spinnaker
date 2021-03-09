package com.netflix.spinnaker.keel.api.verification

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import java.time.Duration
import java.time.Instant

interface VerificationRepository {
  /**
   * @return the current state of [verification] as run against [context], or `null` if it has not
   * yet been run.
   */
  fun getState(
    context: VerificationContext,
    verification: Verification
  ): VerificationState?

  /**
   * @return a map of the current states of all verifications as run against [context]. The key of the map
   * is the id of the Verification object
   */
  fun getStates(
    context: VerificationContext,
  ) : Map<String, VerificationState>


  /**
   * Query the repository for the states of multiple contexts.
   *
   * This call is semantically equivalent to
   *    val repo: VerificationRepository = ...
   *    val contexts : List<VerificationContext> = ...
   *    contexts.map { context -> this.getStates(context) }
   *
   * It exists as a separate call because it can be much more efficient to query the underlying repository as a batch.
   *
   * @param contexts a list of verification contexts to query fo rstate
   *
   * @return a list of maps of verification ids to states, in the same order as the contexts
   */
  fun getStatesBatch(contexts: List<VerificationContext>) : List<Map<String, VerificationState>>

  /**
   * Updates the state of [verification] as run against [context].
   *
   * @param metadata if non-empty this will overwrite any existing metadata.
   */
  fun updateState(
    context: VerificationContext,
    verification: Verification,
    status: ConstraintStatus,
    metadata: Map<String, Any?> = emptyMap()
  )

  fun nextEnvironmentsForVerification(minTimeSinceLastCheck: Duration, limit: Int) : Collection<VerificationContext>
}

data class VerificationState(
  val status: ConstraintStatus,
  val startedAt: Instant,
  val endedAt: Instant?,
  /**
   * Used for storing any contextual information (such as task ids).
   */
  val metadata: Map<String, Any?> = emptyMap()
)

data class VerificationContext(
  val deliveryConfig: DeliveryConfig,
  val environmentName: String,
  val artifactReference: String,
  val version: String
) {
  constructor(deliveryConfig: DeliveryConfig, environment: Environment, artifact: PublishedArtifact) :
    this(deliveryConfig, environment.name, artifact.reference, artifact.version)

  val environment: Environment =
    deliveryConfig.environments.first { it.name == environmentName }

  val artifact: DeliveryArtifact =
    deliveryConfig.artifacts.first { it.reference == artifactReference }

  val verifications: Collection<Verification> = environment.verifyWith

  fun verification(id: String): Verification? = verifications.firstOrNull { it.id == id }
}
