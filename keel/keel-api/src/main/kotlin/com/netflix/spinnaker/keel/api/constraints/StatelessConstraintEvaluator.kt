package com.netflix.spinnaker.keel.api.constraints

import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator

/**
 * A [ConstraintEvaluator] that deals with constraints that don't need to check the database for their status.
 *
 * This class should be implemented if a constraint has a user-facing (or api-facing)
 * summary. This does not apply to implicit constraints.
 */
interface StatelessConstraintEvaluator<T: Constraint, A : ConstraintStateAttributes>
  : ConstraintEvaluator<T>{
  /**
   * The type of the metadata saved about the constraint, surfaced here to automatically register it
   * for serialization
   */
  val attributeType: SupportedConstraintAttributesType<A>

  /**
   * @return a constraint state object that captures the current state of the constraint.
   * This will be used for stateless constraints to construct and save a summary of the state
   * when they are passing.
   *
   * @param currentStatus the status of the constraint if it has already been evaluated.
   * If this is null this function should call [canPromote] and get the current status.
   *
   * If a summary of the constraint will not be shown to the user (like with an implicit constraint)
   * this function does not need to be implemented.
   */
  @JvmDefault
  fun generateConstraintStateSnapshot(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    currentStatus: ConstraintStatus? = null
  ): ConstraintState? = null
}
