package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.config.GitLinkConfig
import com.netflix.spinnaker.config.PromoteJarConfig
import com.netflix.spinnaker.keel.titus.ContainerRunner
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.titus.TestContainerVerification
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.titus.postdeploy.repoUrl
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * A [VerificationEvaluator] that runs a test container to verify an environment.
 *
 * @param linkStrategy because links associated with test containers will have details that depend on the specific
 * environment where keel is deployed, we optionally inject a [LinkStrategy] that contains the logic for calculating the URL.
 *
 */
@Component
@EnableConfigurationProperties(PromoteJarConfig::class)
class TestContainerVerificationEvaluator(
  private val linkStrategy: LinkStrategy? = null,
  private val containerRunner: ContainerRunner,
  private val keelRepository: KeelRepository,
  private val gitLinkConfig: GitLinkConfig
) : VerificationEvaluator<TestContainerVerification> {

  private val PREFIX = "TEST_"

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

    val deliveryArtifact = context.deliveryConfig.matchingArtifactByReference(context.artifactReference)
    requireNotNull(deliveryArtifact) { "Artifact reference (${context.artifactReference}) in config (${context.deliveryConfig.application}) must correspond to a valid artifact" }

    val fullArtifact = keelRepository.getArtifactVersion(
      artifact = deliveryArtifact,
      version = context.version,
      status = null
    )
    requireNotNull(fullArtifact) { "No artifact details found for artifact reference ${context.artifactReference} and version ${context.version} in config ${context.deliveryConfig.application}" }

    return runBlocking {
      containerRunner.launchContainer(
        imageId = verification.imageId,
        subjectLine = "container integration test for ${context.deliveryConfig.application}.${context.environmentName}",
        description = "Verifying ${context.version} in environment ${context.environmentName} with test container ${verification.imageId}",
        serviceAccount = context.deliveryConfig.serviceAccount,
        application = context.deliveryConfig.application,
        containerApplication = verification.application ?: context.deliveryConfig.application,
        environmentName = context.environmentName,
        location = verification.location,
        entrypoint = verification.entrypoint ?: "",
        environmentVariables = mapOf(
          "${PREFIX}ENV" to context.environmentName,
          "${PREFIX}REPO_URL" to fullArtifact.repoUrl(gitLinkConfig.gitUrlPrefix),
          "${PREFIX}BUILD_NUMBER" to "${fullArtifact.buildNumber}",
          "${PREFIX}ARTIFACT_VERSION" to fullArtifact.version,
          "${PREFIX}BRANCH_NAME" to "${fullArtifact.gitMetadata?.branch}",
          "${PREFIX}COMMIT_SHA" to "${fullArtifact.gitMetadata?.commit}",
          "${PREFIX}COMMIT_URL" to "${fullArtifact.gitMetadata?.commitInfo?.link}",
          "${PREFIX}PR_NUMBER" to "${fullArtifact.gitMetadata?.pullRequest?.number}",
          "${PREFIX}PR_URL" to "${fullArtifact.gitMetadata?.pullRequest?.url}",
        )
      )
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

internal const val TASKS = "tasks"
internal const val JOB_STATUS = "jobStatus"
