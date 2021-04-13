package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.actuation.SubjectType.VERIFICATION
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.titus.TestContainerVerification
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationState
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.titus.batch.ContainerJobConfig
import com.netflix.spinnaker.keel.titus.batch.createRunJobStage
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
/**
 * A [VerificationEvaluator] that runs a test container to verify an environment.
 *
 * @param linkStrategy because links associated with test containers will have details that depend on the specific
 * environment where keel is deployed, we optionally inject a [LinkStrategy] that contains the logic for calculating the URL.
 *
 */
class TestContainerVerificationEvaluator(
  private val orca: OrcaService,
  private val taskLauncher: TaskLauncher,
  private val linkStrategy: LinkStrategy? = null

) : VerificationEvaluator<TestContainerVerification> {

  override val supportedVerification: Pair<String, Class<TestContainerVerification>> =
    TestContainerVerification.TYPE to TestContainerVerification::class.java

  override fun evaluate(
    context: VerificationContext,
    verification: Verification,
    oldState: VerificationState
  ): VerificationState {
    @Suppress("UNCHECKED_CAST")
    val taskId = (oldState.metadata[TASKS] as Iterable<String>?)?.last()
    require(taskId is String) {
      "No task id found in previous verification state"
    }

    val response = runBlocking {
      withContext(IO) {
        orca.getOrchestrationExecution(taskId)
      }
    }

    log.debug("Container test task $taskId status: ${response.status.name}")

    val status = when {
      response.status.isSuccess() -> PASS
      response.status.isIncomplete() -> PENDING
      else -> FAIL
    }

    return oldState.copy(status=status, link=getLink(response))
  }

  /**
   * Determine the UI link to show the user about the status of the verification
   */
  fun getLink(response: ExecutionDetailResponse) : String? =
    response.getJobStatus()?.let { linkStrategy?.url(it) }

  /**
   * Retrieve the value of the "jobStatus" variable from an Orca task response
   */
  @Suppress("UNCHECKED_CAST")
  fun ExecutionDetailResponse.getJobStatus() : Map<String, Any?>? =
    variables?.firstOrNull { it.key == JOB_STATUS }?.value as Map<String, Any?>?


  override fun start(context: VerificationContext, verification: Verification): Map<String, Any?> {
    require(verification is TestContainerVerification) {
      "Expected a ${TestContainerVerification::class.simpleName} but received a ${verification.javaClass.simpleName}"
    }

    return runBlocking {
      withContext(IO) {
        taskLauncher.submitJob(
          type = VERIFICATION,
          subject = "container integration test for ${context.deliveryConfig.application}.${context.environmentName}",
          description = "Verifying ${context.version} in environment ${context.environmentName} with test container ${verification.imageId}",
          user = context.deliveryConfig.serviceAccount,
          application = context.deliveryConfig.application,
          notifications = emptySet(),
          stages = listOf(
            ContainerJobConfig(
              application = verification.application ?: context.deliveryConfig.application,
              location = verification.location,
              credentials = verification.location.account,
              image = verification.imageId,
            ).createRunJobStage()
          )
        )
      }
        .let { task ->
          log.debug("Launched container test task ${task.id} for ${context.deliveryConfig.application} environment ${context.environmentName}")
          mapOf(TASKS to listOf(task.id))
        }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

internal const val TASKS = "tasks"
internal const val JOB_STATUS = "jobStatus"
