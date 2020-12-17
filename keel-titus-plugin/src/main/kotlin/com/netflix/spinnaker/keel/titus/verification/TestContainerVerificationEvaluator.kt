package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.actuation.SubjectType.VERIFICATION
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.titus.TestContainerVerification
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationStatus
import com.netflix.spinnaker.keel.api.verification.VerificationStatus.FAILED
import com.netflix.spinnaker.keel.api.verification.VerificationStatus.PASSED
import com.netflix.spinnaker.keel.api.verification.VerificationStatus.RUNNING
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
 */
class TestContainerVerificationEvaluator(
  private val orca: OrcaService,
  private val taskLauncher: TaskLauncher
) : VerificationEvaluator<TestContainerVerification> {

  override val supportedVerification: Pair<String, Class<TestContainerVerification>> =
    TestContainerVerification.TYPE to TestContainerVerification::class.java

  override fun evaluate(
    context: VerificationContext,
    verification: Verification,
    metadata: Map<String, Any?>
  ): VerificationStatus {
    val taskId = metadata[TASK_ID]
    require(taskId is String) {
      "No task id found in previous verification state"
    }

    return runBlocking {
      withContext(IO) {
        orca.getOrchestrationExecution(taskId)
      }
        .let { response ->
          log.debug("Container test task $taskId status: ${response.status.name}")
          when {
            response.status.isSuccess() -> PASSED
            response.status.isIncomplete() -> RUNNING
            else -> FAILED
          }
        }
    }
  }

  override fun start(context: VerificationContext, verification: Verification): Map<String, Any?> {
    require(verification is TestContainerVerification) {
      "Expected a ${TestContainerVerification::class.simpleName} but received a ${verification.javaClass.simpleName}"
    }

    return runBlocking {
      withContext(IO) {
        taskLauncher.submitJob(
          type = VERIFICATION,
          subject = "container integration test for ${context.deliveryConfig.application}.${context.environmentName}",
          description = "Verifying environment ${context.environmentName} with test container ${verification.repository}/${verification.tag}",
          correlationId = "${supportedVerification.first}:${context.deliveryConfig.application}.${context.environmentName}",
          user = context.deliveryConfig.serviceAccount,
          application = context.deliveryConfig.application,
          notifications = emptySet(),
          stages = listOf(
            ContainerJobConfig(
              application = context.deliveryConfig.application,
              location = verification.location,
              repository = verification.repository,
              serviceAccount = context.deliveryConfig.serviceAccount,
              credentials = verification.location.account,
              tag = verification.tag,
              digest = null
            ).createRunJobStage()
          )
        )
      }
        .let { task ->
          log.debug("Launched container test task ${task.id} for ${context.deliveryConfig.application} environment ${context.environmentName}")
          mapOf(TASK_ID to task.id)
        }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

internal const val TASK_ID = "taskId"
