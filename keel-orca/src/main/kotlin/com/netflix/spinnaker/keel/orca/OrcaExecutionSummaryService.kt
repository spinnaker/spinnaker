package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.TaskStatus.RUNNING
import com.netflix.spinnaker.keel.api.actuation.ExecutionSummary
import com.netflix.spinnaker.keel.api.actuation.ExecutionSummaryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Service for translating a task into a nice summary
 */
@Component
class OrcaExecutionSummaryService(
  private val orcaService: OrcaService,
  private val mapper: ObjectMapper
) : ExecutionSummaryService {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    val COMPLETED_TARGETS_STAGE = "initManagedRolloutStep"
  }

  override suspend fun getSummary(executionId: String): ExecutionSummary {
    val taskDetails = orcaService.getOrchestrationExecution(executionId)

    if (taskDetails.variables?.find { it.key == "selectionStrategy" } != null) {
      return constructManagedRolloutSummary(taskDetails)
    } else {
      val typedStages: List<OrcaStage> = taskDetails.execution?.stages?.map { mapper.convertValue(it) } ?: emptyList()
      val currentStage = typedStages.filter { it.status == RUNNING }

      return ExecutionSummary(
        name = taskDetails.name,
        id = taskDetails.id,
        status = taskDetails.status,
        currentStep = currentStage.firstOrNull()?.name,
        summaryText = taskDetails.name, //todo eb: better summary
        error = taskDetails.execution?.stages.getFailureMessage(mapper)
      )
    }
  }

  /**
   * Constructs the specific summary for a managed rollout
   */
  fun constructManagedRolloutSummary(execution: ExecutionDetailResponse): ExecutionSummary {
    val stages = execution.execution?.stages ?: emptyList()
    val typedStages: List<OrcaStage> = stages.map { mapper.convertValue(it) }

    val targets: List<RolloutTarget> = execution
      .variables
      ?.find { it.key == "targets" }
      ?.value
      ?.let { mapper.convertValue<List<RolloutTarget>>(it) } ?: emptyList()

    val completedTargets: List<RolloutTarget> = typedStages
      .filter { it.type == COMPLETED_TARGETS_STAGE}
      .mapNotNull { it.outputs["completedRolloutStep"] }
      .map<Any, RolloutStep> { mapper.convertValue(it) }
      .flatMap { it.targets }

    //todo eb: can there be more than one?
    val currentStage = typedStages.filter { it.status == RUNNING }

    var summary = "${completedTargets.size}/${targets.size} locations deployed. "
    summary += "Deploy completed in ${completedTargets.joinToString(", ") { it.location.region }}. "
    if (targets.size != completedTargets.size) {
      summary += "Waiting to deploy in ${targets.minus(completedTargets).joinToString(", ") { it.location.region }}. "
    }

    return ExecutionSummary(
      name = execution.name,
      id = execution.id,
      status = execution.status,
      currentStep = currentStage.firstOrNull()?.name,
      summaryText = summary,
      error = execution.execution?.stages.getFailureMessage(mapper)
    )
  }

}


//todo eb: take from buoy?
data class RolloutTarget(
  val cloudProvider: String,
  val location: RolloutLocation
)
data class RolloutLocation(
  val account: String,
  val region: String,
  val sublocations: List<String>
)

data class RolloutStep(
  val id: String,
  val targets: List<RolloutTarget>
)
