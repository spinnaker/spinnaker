package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.config.GitLinkConfig
import com.netflix.spinnaker.config.PromoteJarConfig
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.DependencyType.LOAD_BALANCER
import com.netflix.spinnaker.keel.api.Dependent
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.titus.TestContainerVerification
import com.netflix.spinnaker.keel.network.NetworkEndpoint
import com.netflix.spinnaker.keel.network.NetworkEndpointProvider
import com.netflix.spinnaker.keel.network.NetworkEndpointType.EUREKA_CLUSTER_DNS
import com.netflix.spinnaker.keel.network.NetworkEndpointType.EUREKA_VIP_DNS
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.titus.ContainerRunner
import com.netflix.spinnaker.keel.titus.postdeploy.repoUrl
import kotlinx.coroutines.runBlocking
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
  private val gitLinkConfig: GitLinkConfig,
  private val networkEndpointProvider: NetworkEndpointProvider
) : VerificationEvaluator<TestContainerVerification> {

  companion object {
    const val ENV_VAR_PREFIX = "TEST_"
  }

  override val supportedVerification: Pair<String, Class<TestContainerVerification>> =
    TestContainerVerification.TYPE to TestContainerVerification::class.java

  override fun evaluate(
    context: ArtifactInEnvironmentContext,
    verification: Verification,
    oldState: ActionState
  ): ActionState =
    runBlocking {
      log.debug("Getting new verification state for ${context.shortName()}")
      containerRunner.getNewState(oldState, linkStrategy)
    }

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

    val vipDns = eurekaVipDns(context, verification)
    val clusterDns = eurekaClusterDns(context, verification)
    val loadBalancerDns = loadBalancerDns(context, verification)

    return runBlocking {
      containerRunner.launchContainer(
        imageId = verification.imageId,
        description = "Verifying ${context.version} in environment ${context.environmentName} with test container ${verification.imageId}",
        serviceAccount = context.deliveryConfig.serviceAccount,
        application = context.deliveryConfig.application,
        containerApplication = verification.application ?: context.deliveryConfig.application,
        environmentName = context.environmentName,
        location = verification.location,
        entrypoint = verification.entrypoint ?: "",
        environmentVariables = mutableMapOf(
          "${ENV_VAR_PREFIX}ENV" to context.environmentName,
          "${ENV_VAR_PREFIX}REPO_URL" to fullArtifact.repoUrl(gitLinkConfig.gitUrlPrefix),
          "${ENV_VAR_PREFIX}BUILD_NUMBER" to "${fullArtifact.buildNumber}",
          "${ENV_VAR_PREFIX}ARTIFACT_VERSION" to fullArtifact.version,
          "${ENV_VAR_PREFIX}BRANCH_NAME" to "${fullArtifact.gitMetadata?.branch}",
          "${ENV_VAR_PREFIX}COMMIT_SHA" to "${fullArtifact.gitMetadata?.commit}",
          "${ENV_VAR_PREFIX}COMMIT_URL" to "${fullArtifact.gitMetadata?.commitInfo?.link}",
          "${ENV_VAR_PREFIX}PR_NUMBER" to "${fullArtifact.gitMetadata?.pullRequest?.number}",
          "${ENV_VAR_PREFIX}PR_URL" to "${fullArtifact.gitMetadata?.pullRequest?.url}",
        ).apply {
          if (vipDns != null) put("${ENV_VAR_PREFIX}EUREKA_VIP", "${vipDns.address}")
          if (clusterDns != null) put ("${ENV_VAR_PREFIX}EUREKA_CLUSTER", "${clusterDns.address}")
          if (loadBalancerDns != null) put("${ENV_VAR_PREFIX}LOAD_BALANCER", "${loadBalancerDns.address}")
          putAll(verification.env)
        }
      )
    }
  }

  private fun clusterEndpoints(context: ArtifactInEnvironmentContext, verification: TestContainerVerification) =
     context.deliveryConfig
      .resourcesUsing(context.artifactReference, context.environmentName)
      .find { it.spec is ComputeResourceSpec<*> }
        ?.let {
          runBlocking {
            networkEndpointProvider.getNetworkEndpoints(it).filter { endpoint ->
              endpoint.region == verification.location.region
            }
          }
        } ?: emptyList()

  private fun eurekaVipDns(context: ArtifactInEnvironmentContext, verification: TestContainerVerification): NetworkEndpoint? {
    val clusterVip = clusterEndpoints(context, verification).find { it.type == EUREKA_VIP_DNS }
    if (clusterVip == null) {
      log.debug("Unable to find cluster VIP endpoint for ${context.artifact} " +
        "in environment ${context.environmentName} of application ${context.deliveryConfig.application}")
    }
    return clusterVip
  }

  private fun eurekaClusterDns(context: ArtifactInEnvironmentContext, verification: TestContainerVerification): NetworkEndpoint? {
    val clusterEureka = clusterEndpoints(context, verification).find { it.type == EUREKA_CLUSTER_DNS }
    if (clusterEureka == null) {
      log.debug("Unable to find cluster Eureka endpoint for ${context.artifact} " +
        "in environment ${context.environmentName} of application ${context.deliveryConfig.application}")
    }
    return clusterEureka
  }

  private fun loadBalancerDns(context: ArtifactInEnvironmentContext, verification: TestContainerVerification): NetworkEndpoint? {
    val cluster = context.deliveryConfig
      .resourcesUsing(context.artifactReference, context.environmentName)
      .find { it.spec is ComputeResourceSpec<*> } as? Resource<Dependent>

    val clusterLoadBalancers = cluster?.spec?.dependsOn
      ?.filter { it.type == LOAD_BALANCER }
      ?.map { it -> it.name }
      ?: emptyList()

    val loadBalancerDns = if (cluster != null) {
      context.deliveryConfig.resources.find { resource ->
        resource.name in clusterLoadBalancers
      }?.let { loadBalancer ->
        runBlocking {
          networkEndpointProvider.getNetworkEndpoints(loadBalancer).find {
            it.region == verification.location.region
          }
        }
      }
    } else {
      null
    }

    if (clusterLoadBalancers.isNotEmpty() && loadBalancerDns == null) {
      log.debug("Unable to find load balancer endpoint for ${context.artifact} " +
        "in environment ${context.environmentName} of application ${context.deliveryConfig.application}")
    }

    return loadBalancerDns
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

internal const val TASKS = "tasks"
internal const val JOB_STATUS = "jobStatus"
