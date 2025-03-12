package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.model.OrchestrationRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment

/**
 * An implementation of [OrcaService] that delegates all operations to an actual implementation,
 * but checks whether dry-run mode is disabled before making any write calls.
 */
class DryRunCapableOrcaService(
  private val delegate: OrcaService,
  val springEnv: Environment
) : OrcaService by delegate {

  companion object {
    val log: Logger by lazy { LoggerFactory.getLogger(DryRunCapableOrcaService::class.java) }
  }

  private val dryRunEnabled: Boolean
    get() = springEnv.getProperty<Boolean>("keel.dryRun.enabled", Boolean::class.java, false)

  override suspend fun orchestrate(user: String, request: OrchestrationRequest): TaskRefResponse {
    return if (dryRunEnabled) {
      log.warn("[DRY RUN] Skipping call to Orca to submit orchestration: {}", request)
      TaskRefResponse("orchestration/dryRun")
    } else {
      delegate.orchestrate(user, request)
    }
  }

  override suspend fun triggerPipeline(user: String, pipelineConfigId: String, trigger: HashMap<String, Any>): TaskRefResponse {
    return if (dryRunEnabled) {
      log.warn("[DRY RUN] Skipping call to Orca to trigger pipeline {}", pipelineConfigId)
      TaskRefResponse("pipeline/dryRun")
    } else {
      delegate.triggerPipeline(user, pipelineConfigId, trigger)
    }
  }

  override suspend fun cancelOrchestration(id: String, user: String) {
    if (dryRunEnabled) {
      log.warn("[DRY RUN] Skipping call to Orca to cancel orchestration {}", id)
    } else {
      delegate.cancelOrchestration(id, user)
    }
  }

}
