package com.netflix.spinnaker.keel

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.Action
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.telemetry.TelemetryListener
import org.slf4j.LoggerFactory

/**
 * A base class that defines the logic for running a series of actions on an environment.
 *
 */
abstract class BaseActionRunner<T: Action> {
  abstract val actionRepository: ActionRepository
  private val log by lazy { LoggerFactory.getLogger(javaClass) }
  abstract val spectator: Registry

  companion object {
    const val ACTION_BLOCKED_COUNTER = "keel.action-runner.blocked.count"
  }

  /**
   * Word used to prefix logs to make them more searchable
   */
  abstract fun logSubject(): String

  /**
   * Fetches the list of actions from the environment
   */
  abstract fun ArtifactInEnvironmentContext.getActions(): List<T>

  /**
   * @return true if things should be run one at a time,
   * false if things can be run in parallel
   */
  abstract fun runInSeries(): Boolean

  /**
   * @return true if the action cannot start because it is blocked
   * Should log about why it is blocked if true
   */
  abstract fun actionBlocked(context: ArtifactInEnvironmentContext): Boolean

  /**
   * Calls the handler that can start the action.
   */
  abstract suspend fun start(context: ArtifactInEnvironmentContext, action: T)

  /**
   * Calls the handler that can evaluate the action, returns the status
   */
  abstract suspend fun evaluate(context: ArtifactInEnvironmentContext, action: T, oldState: ActionState): ActionState

  /**
   * Publishes an event when the action is complete
   */
  abstract fun publishStartEvent(context: ArtifactInEnvironmentContext, action: T)

  /**
   * Publishes an event when the action is complete
   */
  abstract fun publishCompleteEvent(context: ArtifactInEnvironmentContext, action: T, state: ActionState)

  /**
   * Evaluates the state of any currently running actions and launches the next, against a
   * particular environment and artifact version.
   */
  suspend fun runFor(context: ArtifactInEnvironmentContext) {
    with(context) {
      val statuses = getActions()
        .also { log.debug("Checking status for ${context.shortName()}: $it") }
        .map { action ->
          action to latestStatus(context, action)
        }

      log.debug("Status for ${context.shortName()}: $statuses")

      if (actionBlocked(context)) {
        log.debug("${logSubject()} is blocked for ${shortName()}, skipping.")
        incrementBlockedCounter(context)
        return
      }

      if (runInSeries() && statuses.anyStillRunning) {
        log.debug("${logSubject()} already running for ${context.shortName()}")
        return
      }

      statuses.firstOutstanding?.let { action ->
        log.debug("Starting action ${action.type} ${action.id} for $context")
        start(context, action)
        publishStartEvent(context, action)
      } ?: log.debug("${logSubject()} complete for ${context.shortName()}")
    }
  }


  private suspend fun latestStatus(context: ArtifactInEnvironmentContext, action: T): ConstraintStatus? {
    val oldState = getPreviousState(context, action)
    log.debug("Old state for ${context.shortName()}: $oldState")
    val newState = if (oldState?.status == PENDING) {
      evaluate(context, action, oldState)
        .also { newState ->
          val newStatus = newState.status
          if (newStatus.complete) {
            log.debug("${logSubject()} {} completed with status {} for environment {} of application {}",
              action, newStatus, context.environment.name, context.deliveryConfig.application)
            actionRepository.updateState(context, action, newStatus, link=newState.link)
            publishCompleteEvent(context, action, newState)
          } else if (oldState.link != newState.link) {
            actionRepository.updateState(context, action, newStatus, link=newState.link)
          }
        }
    } else {
      oldState
    }
    return newState?.status
  }

  private fun incrementBlockedCounter(context: ArtifactInEnvironmentContext) {
    spectator.counter(
      ACTION_BLOCKED_COUNTER,
      listOf(
        BasicTag("type", logSubject()),
        BasicTag("application", context.deliveryConfig.application)
      )

    ).increment()
  }

  private fun getPreviousState(context: ArtifactInEnvironmentContext, action: T): ActionState? =
    actionRepository.getState(context, action)

  val Collection<Pair<T, ConstraintStatus?>>.firstOutstanding: T?
    get() = firstOrNull { (_, status) -> status in listOf(null, NOT_EVALUATED) }?.first

  val Collection<Pair<*, ConstraintStatus?>>.anyStillRunning: Boolean
    get() = any { (_, status) -> status == PENDING }
}
