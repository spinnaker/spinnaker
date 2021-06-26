package com.netflix.spinnaker.keel.api.action

import com.netflix.spinnaker.keel.api.ActionStateUpdateContext
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import java.time.Duration
import java.time.Instant

interface ActionRepository {

  /**
   * @return the current state of [action] as run against [context], or `null` if it has not
   * yet been run.
   */
  fun getState(
    context: ArtifactInEnvironmentContext,
    action: Action
  ): ActionState?

  /**
   * @return a map of the current states of all [type] as run against [context]. The key of the map
   * is the id of the Verification object
   */
  fun getStates(
    context: ArtifactInEnvironmentContext,
    type: ActionType
  ): Map<String, ActionState>

  /**
   * @return true if there are no [type] actions defined,
   * or if all have been run and have passed
   */
  fun allPassed(context: ArtifactInEnvironmentContext, type: ActionType): Boolean

  /**
   * @return true if there are no [type] actions defined,
   * or if all have been started (failed/passed)
   */
  fun allStarted(context: ArtifactInEnvironmentContext, type: ActionType): Boolean

  /**
   * Query the repository for the states of multiple contexts.
   *
   * This call is semantically equivalent to
   *    val repo: ActionRepository = ...
   *    val contexts : List<ArtifactInEnvironemntContext> = ...
   *    contexts.map { context -> this.getStates(context) }
   *
   * It exists as a separate call because it can be much more efficient to query the underlying repository as a batch.
   *
   * @param contexts a list of artifact in environment contexts to query for state
   *
   * @return a list of maps of action ids to states, in the same order as the contexts
   */
  fun getStatesBatch(contexts: List<ArtifactInEnvironmentContext>, type: ActionType) : List<Map<String, ActionState>>

  /**
   * same as getStatesBatch, but returns all actions.
   */
  fun getAllStatesBatch(contexts: List<ArtifactInEnvironmentContext>): List<List<ActionStateFull>>

  /**
   * Updates the state of [action] run against [context]
   * @param metadata if non-empty this will overwrite any existing metadata.
   */
  fun updateState(
    context: ArtifactInEnvironmentContext,
    action: Action,
    status: ConstraintStatus,
    metadata: Map<String, Any?> = emptyMap(),
    link: String? = null,
  )

  /**
   * Update the action status of all artifact versions associated with [context]
   */
  fun updateState(context: ActionStateUpdateContext, status: ConstraintStatus)

  /**
   * Resets the state of [action] run against [context]
   */
  fun resetState(
    context: ArtifactInEnvironmentContext,
    action: Action,
    user: String,
  ): ConstraintStatus

  fun nextEnvironmentsForVerification(minTimeSinceLastCheck: Duration, limit: Int) : Collection<ArtifactInEnvironmentContext>

  /**
   * Return verifications contexts in the [environment] of [deliveryConfig] with [status]
   */
  fun getVerificationContextsWithStatus(deliveryConfig: DeliveryConfig, environment: Environment, status: ConstraintStatus): Collection<ArtifactInEnvironmentContext>

  /**
   * @return the next [limit] environments to check
   */
  fun nextEnvironmentsForPostDeployAction(minTimeSinceLastCheck: Duration, limit: Int) : Collection<ArtifactInEnvironmentContext>
}

data class ActionState(
  val status: ConstraintStatus,
  val startedAt: Instant,
  val endedAt: Instant?,

  /**
   * Used for storing any contextual information (such as task ids).
   */
  val metadata: Map<String, Any?> = emptyMap(),

  /**
   * User-meaningful url that points to additional detail about the state of the verification
   */
  val link: String? = null
)

/**
 * Holds the state plus the id and type of an action
 */
data class ActionStateFull(
  val state: ActionState,
  val type: ActionType,
  val id: String
)

