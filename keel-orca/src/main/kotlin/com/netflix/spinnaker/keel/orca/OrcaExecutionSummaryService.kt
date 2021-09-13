package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.api.TaskStatus.CANCELED
import com.netflix.spinnaker.keel.api.TaskStatus.FAILED_CONTINUE
import com.netflix.spinnaker.keel.api.TaskStatus.NOT_STARTED
import com.netflix.spinnaker.keel.api.TaskStatus.RUNNING
import com.netflix.spinnaker.keel.api.TaskStatus.SKIPPED
import com.netflix.spinnaker.keel.api.TaskStatus.STOPPED
import com.netflix.spinnaker.keel.api.TaskStatus.SUCCEEDED
import com.netflix.spinnaker.keel.api.TaskStatus.TERMINAL
import com.netflix.spinnaker.keel.api.actuation.ExecutionSummary
import com.netflix.spinnaker.keel.api.actuation.ExecutionSummaryService
import com.netflix.spinnaker.keel.api.actuation.RolloutLocation
import com.netflix.spinnaker.keel.api.actuation.RolloutStatus
import com.netflix.spinnaker.keel.api.actuation.RolloutStep
import com.netflix.spinnaker.keel.api.actuation.RolloutTarget
import com.netflix.spinnaker.keel.api.actuation.RolloutTargetWithStatus
import com.netflix.spinnaker.keel.api.actuation.Stage
import kotlinx.coroutines.runBlocking
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

  override fun getSummary(executionId: String): ExecutionSummary {
    val taskDetails = runBlocking {
      orcaService.getOrchestrationExecution(executionId)
    }

    val typedStages: List<OrcaStage> = taskDetails.execution?.stages?.map { mapper.convertValue(it) } ?: emptyList()
    val currentStage = typedStages.firstOrNull { it.status == RUNNING }
    val targets = getTargets(taskDetails, typedStages)

    return ExecutionSummary(
      name = taskDetails.name,
      id = taskDetails.id,
      status = taskDetails.status,
      currentStage = currentStage?.toStage(),
      stages = typedStages.map { it.toStage() },
      deployTargets = targets,
      error = taskDetails.execution?.stages.getFailureMessage(mapper)
    )
  }

  /**
   * Have to get the status of all of them...
   */
  fun getTargets(execution: ExecutionDetailResponse, typedStages: List<OrcaStage>): List<RolloutTargetWithStatus> {
    val targetsWithStatus: MutableList<RolloutTargetWithStatus> = mutableListOf()
    val statusTargetMap = if (execution.isManagedRollout()) {
      getTargetStatusManagedRollout(execution, typedStages)
    } else {
      getTargetStatusDeployStage(execution, typedStages)
    }

     statusTargetMap.forEach { (status, targets) ->
      targetsWithStatus.addAll(
        targets.map {
          RolloutTargetWithStatus(
            rolloutTarget = it,
            status = status
          )
        }
      )
    }

    return targetsWithStatus
  }

  fun ExecutionDetailResponse.isManagedRollout(): Boolean =
    variables?.find { it.key == "selectionStrategy" } != null

  fun getTargetStatusManagedRollout(
    execution: ExecutionDetailResponse,
    typedStages: List<OrcaStage>
  ): Map<RolloutStatus, List<RolloutTarget>> {
    val targets: MutableMap<RolloutStatus, List<RolloutTarget>> = mutableMapOf()
    targets[RolloutStatus.SUCCEEDED] = typedStages
      .filter { it.type == COMPLETED_TARGETS_STAGE}
      .mapNotNull { it.outputs["completedRolloutStep"] }
      .map<Any, RolloutStep> { mapper.convertValue(it) }
      .flatMap { it.targets }

    targets[RolloutStatus.RUNNING] = typedStages
      .filter { it.type == COMPLETED_TARGETS_STAGE &&
        !it.outputs.containsKey("completedRolloutStep") &&
        it.status == RUNNING
      }
      .map { stage ->
        val input = stage.context["input"] as Map<*, *>
        val runningTargets = input["targets"] as? Map<*, *> ?: emptyList<RolloutTarget>()
        mapper.convertValue(runningTargets)
      }

    targets[RolloutStatus.NOT_STARTED] = typedStages
      .filter {
        it.type == COMPLETED_TARGETS_STAGE &&
          it.status == NOT_STARTED
      }
      .map { stage ->
        val input = stage.context["input"] as Map<*, *>
        val runningTargets = input["targets"] as? Map<*, *> ?: emptyList<RolloutTarget>()
        mapper.convertValue(runningTargets)
      }

    targets[RolloutStatus.FAILED] = typedStages
      .filter {
        it.type == COMPLETED_TARGETS_STAGE &&
          listOf(FAILED_CONTINUE, TERMINAL, CANCELED, SKIPPED, STOPPED).contains(it.status)
      }
      .map { stage ->
        val input = stage.context["input"] as Map<*, *>
        val runningTargets = input["targets"] as? Map<*, *> ?: emptyList<RolloutTarget>()
        mapper.convertValue(runningTargets)
      }

    return targets
  }

  fun getTargetStatusDeployStage(
    execution: ExecutionDetailResponse,
    typedStages: List<OrcaStage>
  ): Map<RolloutStatus, List<RolloutTarget>> {
    val deployStage = typedStages.firstOrNull { it.type == "createServerGroup" }
    val allTargets = createTargetFromDeployStage(deployStage)

    val targets: MutableMap<RolloutStatus, List<RolloutTarget>> = mutableMapOf()

    when {
      execution.status.isSuccess() -> targets[RolloutStatus.SUCCEEDED] = allTargets
      execution.status.isFailure() -> targets[RolloutStatus.FAILED] = allTargets
      execution.status.isIncomplete() -> targets[RolloutStatus.RUNNING] = allTargets
    }

    return targets
  }

  fun createTargetFromDeployStage(deployStage: OrcaStage?): List<RolloutTarget> {
    if (deployStage == null) {
      return emptyList()
    }

    var account: String = deployStage.context["credentials"] as? String ?: "unable to find account"
    val cloudProvider: String = deployStage.context["cloudProvider"] as? String ?: "unable to find cloud provider"
    val serverGroups = deployStage.context["deploy.server.groups"] as? Map<*, *>
    val regions: MutableSet<String> = serverGroups?.keys as? MutableSet<String> ?: mutableSetOf()

    if (regions.isEmpty()) {
      // deploy stage is hasn't created the server group yet,
      // let's see if we can find the info from the source server group
      val source = deployStage.context["source"] as? Map<*, *> ?: emptyMap<String, String>()
      source["region"]?.let { regions.add(it as String) }
      source["account"]?.let { account = it as String }
    }

    return regions.map { region ->
      RolloutTarget(
        cloudProvider = cloudProvider,
        location = RolloutLocation(account = account, region = region),
      )
    }
  }

  fun OrcaStage.toStage() =
    Stage(
      id = id,
      type = type,
      name = name,
      startTime = startTime,
      endTime = endTime,
      status = status,
      refId = refId,
      requisiteStageRefIds = requisiteStageRefIds,
    )
}
