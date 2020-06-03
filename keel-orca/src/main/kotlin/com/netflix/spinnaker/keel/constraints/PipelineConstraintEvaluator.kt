package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintRepository
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.core.api.PipelineConstraint
import com.netflix.spinnaker.keel.model.toOrcaNotification
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaService
import java.time.Clock
import java.util.HashMap
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory.getLogger
import org.springframework.stereotype.Component

/**
 * An environment promotion constraint to gate promotions on the successful execution
 * of a Spinnaker pipeline. The target pipeline is launched via orca the first time
 * a pipeline constraint is evaluated for a given artifact version and [Environment].
 *
 * The state of that pipeline execution is then assessed on each subsequent evaluation
 * and can optionally be retried on failure.
 *
 * Example env constraint:
 *
 * constraints:
 *  - type: pipeline
 *    pipelineId: (the id visible when editing a pipeline, not its name)
 *    retries: (OPTIONAL, the number of times to retry a failure, defaults to 0)
 *    parameters: (OPTIONAL, Map of trigger parameters to pass along, `user` and `type` are reserved)
 *
 */
@Component
class PipelineConstraintEvaluator(
  private val orcaService: OrcaService,
  repository: ConstraintRepository,
  override val eventPublisher: EventPublisher,
  private val clock: Clock
) : StatefulConstraintEvaluator<PipelineConstraint>(repository) {
  override val supportedType = SupportedConstraintType<PipelineConstraint>("pipeline")
  private val log by lazy { getLogger(javaClass) }

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    constraint: PipelineConstraint,
    state: ConstraintState
  ): Boolean {
    var attributes = state.attributes as PipelineConstraintStateAttributes?
    val status = pipelineStatus(deliveryConfig.serviceAccount, attributes)

    if (attributes != null && status != null && attributes.lastExecutionStatus != status.toString()) {
      attributes = attributes.copy(lastExecutionStatus = status.toString())
    }

    val judge = "pipeline:${constraint.pipelineId}"

    // TODO: if the constraint has timed out but the pipeline is still running, should we cancel it?
    if (timedOut(constraint, state, attributes)) {
      repository
        .storeConstraintState(
          state.copy(
            status = FAIL,
            attributes = attributes,
            judgedBy = judge,
            judgedAt = clock.instant(),
            comment = "Timed out after ${constraint.timeout} and ${attributes?.attempt ?: "0"} attempt" +
              if (attributes?.attempt != 1) {
                "s"
              } else {
                ""
              }))
      // TODO: Emit event
      return false
    }

    if (shouldTrigger(constraint, attributes)) {
      try {
        val executionId = runBlocking {
          startPipeline(targetEnvironment, constraint, deliveryConfig.serviceAccount)
        }

        attributes = PipelineConstraintStateAttributes(
          executionId = executionId,
          attempt = (attributes?.attempt ?: 0) + 1,
          latestAttempt = clock.instant()
        )
      } catch (e: Exception) {
        log.warn("Failed triggering pipeline ${constraint.pipelineId} for " +
          "${deliveryConfig.application}:$deliveryConfig/$targetEnvironment", e)

        attributes = PipelineConstraintStateAttributes(
          executionId = null,
          attempt = attributes?.attempt ?: 0 + 1,
          latestAttempt = clock.instant()
        )
      }

      repository.storeConstraintState(state.copy(attributes = attributes))
      return false
    } else if (attributes?.executionId == null) {
      // If we don't have an executionId or available retries, fail
      repository
        .storeConstraintState(
          state.copy(
            status = FAIL,
            comment = "Failed to trigger pipeline ${constraint.pipelineId}, please review pipeline constraint " +
              "configuration in delivery-config ${deliveryConfig.name} for ${targetEnvironment.name}",
            judgedAt = clock.instant(),
            judgedBy = "keel"))

      return false
    }

    if (status == null || status.isIncomplete()) {
      // Persist the pipeline status if changed
      if (attributes.lastExecutionStatus !=
        (state.attributes as PipelineConstraintStateAttributes?)?.lastExecutionStatus) {
        repository
          .storeConstraintState(
            state.copy(attributes = attributes))
      }

      return false
    }

    val newState = if (status.isFailure()) {
      state.copy(
        status = FAIL,
        comment = "Failed to validate environment promotion via $judge",
        judgedBy = judge,
        judgedAt = clock.instant(),
        attributes = attributes)
    } else {
      state.copy(
        status = PASS,
        comment = "Validated environment promotion via $judge",
        judgedBy = judge,
        judgedAt = clock.instant(),
        attributes = attributes)
    }

    repository.storeConstraintState(newState)
    return status.isSuccess()
  }

  private suspend fun startPipeline(
    environment: Environment,
    constraint: PipelineConstraint,
    serviceAccount: String
  ): String {
    log.info("Triggering pipeline ${constraint.pipelineId} for environment ${environment.name}")

    // using java.util.HashMap over kotlin.collections for retrofit2 compatibility
    val trigger = HashMap<String, Any>()
    trigger["type"] = "managed"
    trigger["user"] = "keel"
    trigger["parameters"] = constraint.parameters
    trigger["linkText"] = "Env ${environment.name}"

    if (environment.notifications.isNotEmpty() && !trigger.containsKey("notifications")) {
      trigger["notifications"] = environment.notifications
        .map {
          it.toOrcaNotification()
        }
    }

    return orcaService
      .triggerPipeline(serviceAccount, constraint.pipelineId, trigger)
      .taskId
  }

  private fun pipelineStatus(serviceAccount: String, attributes: PipelineConstraintStateAttributes?): OrcaExecutionStatus? {
    if (attributes?.executionId == null) {
      return null
    }

    return runBlocking {
      orcaService
        .getPipelineExecution(attributes.executionId!!, serviceAccount)
        .status
    }
  }

  private fun shouldTrigger(
    constraint: PipelineConstraint,
    attributes: PipelineConstraintStateAttributes?
  ): Boolean {
    val status = attributes?.lastExecutionStatus?.let {
      OrcaExecutionStatus.valueOf(it)
    }

    return when {
      attributes == null -> true
      attributes.executionId == null && hasRetries(constraint, attributes) -> true
      status.isFailure() && hasRetries(constraint, attributes) -> true
      else -> false
    }
  }

  private fun hasRetries(
    constraint: PipelineConstraint,
    attributes: PipelineConstraintStateAttributes
  ): Boolean =
    constraint.retries > 0 && constraint.retries < attributes.attempt - 1

  private fun timedOut(
    constraint: PipelineConstraint,
    state: ConstraintState,
    attributes: PipelineConstraintStateAttributes?
  ): Boolean {
    val now = clock.instant()
    return attributes?.latestAttempt?.plus(constraint.timeout)?.isBefore(now)
      ?: state.createdAt.plus(constraint.timeout).isBefore(now)
  }

  private fun OrcaExecutionStatus?.isFailure() = this != null && this.isFailure()
}
