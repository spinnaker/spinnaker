package com.netflix.spinnaker.keel.titus

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.titus.batch.ContainerJobConfig
import com.netflix.spinnaker.keel.titus.batch.createRunJobStage
import com.netflix.spinnaker.keel.titus.verification.TASKS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

abstract class AbstractContainerRunner(
  private val taskLauncher: TaskLauncher,
  private val spectator: Registry
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
  companion object {
    private const val CONTAINER_LAUNCHED_COUNTER_ID = "keel.titus.run-job.launched"
  }

  /**
   * Launches the container, increments a metric, returns the task ids.
   */
  fun launchContainer(
    imageId: String,
    subjectLine: String,
    description: String,
    serviceAccount: String,
    application: String,
    environmentName: String,
    location: TitusServerGroup.Location,
    environmentVariables: Map<String, String> = emptyMap()
  ): Map<String, Any?> {
    return runBlocking {
      withContext(Dispatchers.IO) {
        taskLauncher.submitJob(
          type = SubjectType.VERIFICATION,
          subject = subjectLine,
          description = description,
          user = serviceAccount,
          application = application,
          notifications = emptySet(),
          stages = listOf(
            ContainerJobConfig(
              application = application,
              location = location,
              credentials = location.account,
              image = imageId,
              environmentVariables = environmentVariables
            ).createRunJobStage()
          )
        )
      }
        .let { task ->
          log.debug("Launched container task ${task.id} for $application environment $environmentName by ${javaClass.simpleName}")
          incrementContainerLaunchedCounter(application, environmentName)
          mapOf(TASKS to listOf(task.id))
        }
    }
  }

  private fun incrementContainerLaunchedCounter(application: String, environmentName: String) {
    spectator.counter(
      CONTAINER_LAUNCHED_COUNTER_ID,
      listOf(
        BasicTag("application", application),
        BasicTag("environmentName", environmentName),
        BasicTag("runner", javaClass.simpleName)
      )
    ).safeIncrement()
  }

  private fun Counter.safeIncrement() =
  try {
    increment()
  } catch (ex: Exception) {
    log.error("Exception incrementing {} counter: {}", id().name(), ex.message)
  }
}
