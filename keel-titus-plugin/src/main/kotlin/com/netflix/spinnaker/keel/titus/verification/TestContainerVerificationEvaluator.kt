package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.keel.titus.ContainerRunner
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.titus.TestContainerVerification
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaService
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * A [VerificationEvaluator] that runs a test container to verify an environment.
 *
 * @param linkStrategy because links associated with test containers will have details that depend on the specific
 * environment where keel is deployed, we optionally inject a [LinkStrategy] that contains the logic for calculating the URL.
 *
 */
@Component
class TestContainerVerificationEvaluator(
  private val linkStrategy: LinkStrategy? = null,
  private val containerRunner: ContainerRunner
) : VerificationEvaluator<TestContainerVerification> {

  override val supportedVerification: Pair<String, Class<TestContainerVerification>> =
    TestContainerVerification.TYPE to TestContainerVerification::class.java

  override fun evaluate(
    context: ArtifactInEnvironmentContext,
    verification: Verification,
    oldState: ActionState
  ): ActionState =
    runBlocking { containerRunner.getNewState(oldState, linkStrategy) }

  override fun start(context: ArtifactInEnvironmentContext, verification: Verification): Map<String, Any?> {
    require(verification is TestContainerVerification) {
      "Expected a ${TestContainerVerification::class.simpleName} but received a ${verification.javaClass.simpleName}"
    }
    return runBlocking {
      containerRunner.launchContainer(
        imageId = verification.imageId,
        subjectLine = "container integration test for ${context.deliveryConfig.application}.${context.environmentName}",
        description = "Verifying ${context.version} in environment ${context.environmentName} with test container ${verification.imageId}",
        serviceAccount = context.deliveryConfig.serviceAccount,
        application = context.deliveryConfig.application,
        containerApplication = verification.application ?: context.deliveryConfig.application,
        environmentName = context.environmentName,
        location = verification.location
      )
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

internal const val TASKS = "tasks"
internal const val JOB_STATUS = "jobStatus"
