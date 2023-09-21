package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.actuation.ExecutionSummary
import com.netflix.spinnaker.keel.actuation.ExecutionSummaryService
import com.netflix.spinnaker.keel.actuation.RolloutLocation
import com.netflix.spinnaker.keel.actuation.RolloutStatus
import com.netflix.spinnaker.keel.actuation.RolloutStep
import com.netflix.spinnaker.keel.actuation.RolloutTarget
import com.netflix.spinnaker.keel.actuation.RolloutTargetWithStatus
import com.netflix.spinnaker.keel.actuation.Stage
import com.netflix.spinnaker.keel.api.TaskStatus.CANCELED
import com.netflix.spinnaker.keel.api.TaskStatus.FAILED_CONTINUE
import com.netflix.spinnaker.keel.api.TaskStatus.RUNNING
import com.netflix.spinnaker.keel.api.TaskStatus.SKIPPED
import com.netflix.spinnaker.keel.api.TaskStatus.STOPPED
import com.netflix.spinnaker.keel.api.TaskStatus.TERMINAL
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
    val KICKOFF_STAGE = "startManagedRollout"
    val DEPLOY_STAGE = "deploy"
  }

  override fun getSummary(executionId: String): ExecutionSummary {
    val taskDetails = runBlocking {
      orcaService.getOrchestrationExecution(executionId)
    }

    val typedStages: List<OrcaStage> = taskDetails.execution?.stages?.map { mapper.convertValue(it) } ?: emptyList()
    val currentStage = typedStages
      .filter { it.status == RUNNING }
      .maxByOrNull { it.refId.length } //grab the longest ref id, which will be the most nested running stage
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
      getTargetStatusManagedRollout(typedStages)
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
    typedStages: List<OrcaStage>
  ): Map<RolloutStatus, List<RolloutTarget>> {
    val targets: MutableMap<RolloutStatus, List<RolloutTarget>> = mutableMapOf()

    // completed targets will be listed in the outputs of this type of stage
    targets[RolloutStatus.SUCCEEDED] = typedStages
      .filter { it.type == COMPLETED_TARGETS_STAGE }
      .mapNotNull { it.outputs["completedRolloutStep"] }
      .map<Any, RolloutStep> { mapper.convertValue(it) }
      .flatMap { it.targets }

    // deploying targets will be listed in the context of the deploy stage,
    // so we filter for running deploy stages
    targets[RolloutStatus.RUNNING] = typedStages
      .filter {
        it.type == DEPLOY_STAGE &&
          it.status == RUNNING
      }
      .map { stage ->
        val runningTargets = stage.context["targets"] as? List<Map<*, *>> ?: emptyList()
        mapper.convertValue<List<RolloutTarget>>(runningTargets)
      }
      .flatten()

    targets[RolloutStatus.FAILED] = typedStages
      .filter {
        it.type == DEPLOY_STAGE &&
          listOf(FAILED_CONTINUE, TERMINAL, CANCELED, SKIPPED, STOPPED).contains(it.status)
      }
      .map { stage ->
        val runningTargets = stage.context["targets"] as? List<Map<*, *>> ?: emptyList()
        mapper.convertValue(runningTargets)
      }

    targets[RolloutStatus.NOT_STARTED] = (typedStages
      .firstOrNull {
        it.type == KICKOFF_STAGE
      }
      ?.let { stage ->
        val allTargets = stage.context["targets"] as? List<Map<*, *>> ?: emptyList()
        mapper.convertValue<List<RolloutTarget>>(allTargets)
      } ?: emptyList())
      .filter { target ->
        target.notIn(targets[RolloutStatus.SUCCEEDED] as List<RolloutTarget>) &&
          target.notIn(targets[RolloutStatus.FAILED] as List<RolloutTarget>) &&
          target.notIn(targets[RolloutStatus.RUNNING] as List<RolloutTarget>)
      }

    return targets
  }

  /**
   * Normal equals/comparison doesn't work when we use the java objects, so I must
   * write my own.
   */
  fun RolloutTarget.notIn(targets: List<RolloutTarget>): Boolean {
    targets.forEach { target ->
      if (target.cloudProvider == cloudProvider &&
        target.location.region == location.region &&
        target.location.account == location.account &&
        target.location.sublocations == location.sublocations
      ) {
        return false
      }
    }
    return true
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
        cloudProvider,
        RolloutLocation(account, region, emptyList()),
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
