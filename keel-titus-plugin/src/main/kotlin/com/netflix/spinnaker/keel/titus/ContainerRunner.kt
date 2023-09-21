package com.netflix.spinnaker.keel.titus

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.actuation.SubjectType.VERIFICATION
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.telemetry.safeIncrement
import com.netflix.spinnaker.keel.titus.batch.ContainerJobConfig
import com.netflix.spinnaker.keel.titus.batch.createRunJobStage
import com.netflix.spinnaker.keel.titus.verification.LinkStrategy
import com.netflix.spinnaker.keel.titus.verification.TASKS
import com.netflix.spinnaker.keel.titus.verification.getLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ContainerRunner(
  private val taskLauncher: TaskLauncher,
  private val orca: OrcaService,
  private val spectator: Registry
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
  companion object {
    private const val CONTAINER_LAUNCHED_COUNTER_ID = "keel.titus.run-job.launched"
  }

  suspend fun getNewState(
    oldState: ActionState,
    linkStrategy: LinkStrategy?
  ): ActionState {
    @Suppress("UNCHECKED_CAST")
    val taskId = (oldState.metadata[TASKS] as Iterable<String>?)?.last()
    log.debug("Checking status for task $taskId")
    require(taskId is String) {
      "No task id found in previous container state"
    }

    val response = withContext(Dispatchers.IO) {
        orca.getOrchestrationExecution(taskId)
      }

    log.debug("Container test task $taskId status: ${response.status.name}")

    val status = when {
      response.status.isSuccess() -> ConstraintStatus.PASS
      response.status.isIncomplete() -> ConstraintStatus.PENDING
      else -> ConstraintStatus.FAIL
    }

    return oldState.copy(status=status, link= getLink(response, linkStrategy))
  }

  /**
   * Launches the container, increments a metric, returns the task ids.
   */
  suspend fun launchContainer(
    imageId: String,
    description: String,
    serviceAccount: String,
    application: String,
    environmentName: String,
    location: TitusServerGroup.Location,
    environmentVariables: Map<String, String> = emptyMap(),
    containerApplication: String = application,
    entrypoint: String = ""
  ): Map<String, Any?> {
      return withContext(Dispatchers.IO) {
        taskLauncher.submitJob(
          type = VERIFICATION,
          environmentName = environmentName,
          resourceId = null,
          description = description,
          user = serviceAccount,
          application = application,
          notifications = emptySet(),
          stages = listOf(
            ContainerJobConfig(
              application = containerApplication,
              location = location,
              credentials = location.account,
              image = imageId,
              environmentVariables = environmentVariables,
              entrypoint = entrypoint
            ).createRunJobStage()
          )
        )
        .let { task ->
          log.debug("Launched container task ${task.id} for $application environment $environmentName")
          incrementContainerLaunchedCounter(application, environmentName, imageId)
          mapOf(TASKS to listOf(task.id))
        }
    }
  }

  private fun incrementContainerLaunchedCounter(application: String, environmentName: String, imageId: String) {
    spectator.counter(
      CONTAINER_LAUNCHED_COUNTER_ID,
      listOf(
        BasicTag("application", application),
        BasicTag("environmentName", environmentName),
        BasicTag("imageId", imageId)
      )
    ).safeIncrement()
  }
}
