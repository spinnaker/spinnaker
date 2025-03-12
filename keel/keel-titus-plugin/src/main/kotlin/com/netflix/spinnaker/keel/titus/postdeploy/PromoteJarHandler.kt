package com.netflix.spinnaker.keel.titus.postdeploy

import com.netflix.spinnaker.config.GitLinkConfig
import com.netflix.spinnaker.config.PromoteJarConfig
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.plugins.PostDeployActionHandler
import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction
import com.netflix.spinnaker.keel.api.postdeploy.SupportedPostDeployActionType
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.core.api.PromoteJarPostDeployAction
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.titus.ContainerRunner
import com.netflix.spinnaker.keel.titus.verification.LinkStrategy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * A runner than launches a specific docker container (defined in [PromoteJarConfig]).
 * That container needs to promote the candidate jar identified by the
 * repo (REPO_URL) and jenkins build number (BUILD_NUM) passed in as env
 * variables to the container.
 */
@EnableConfigurationProperties(PromoteJarConfig::class, GitLinkConfig::class)
@Component
class PromoteJarHandler(
  override val eventPublisher: EventPublisher,
  val config: PromoteJarConfig,
  val gitConfig: GitLinkConfig,
  val keelRepository: KeelRepository,
  val containerRunner: ContainerRunner,
  private val linkStrategy: LinkStrategy? = null
) : PostDeployActionHandler<PromoteJarPostDeployAction>{

  override val supportedType = SupportedPostDeployActionType<PromoteJarPostDeployAction>("promote-candidate-jar")

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override suspend fun evaluate(
    context: ArtifactInEnvironmentContext,
    action: PostDeployAction,
    oldState: ActionState
  ): ActionState =
    containerRunner.getNewState(oldState, linkStrategy)

  override suspend fun start(context: ArtifactInEnvironmentContext, action: PostDeployAction): Map<String, Any?> {
    requireNotNull(config.imageId) { "Titus image must be set in the promote jar config"}

    val deliveryArtifact = context.deliveryConfig.matchingArtifactByReference(context.artifactReference)
    requireNotNull(deliveryArtifact) { "Artifact reference (${context.artifactReference}) in config (${context.deliveryConfig.application}) must correspond to a valid artifact" }

    val fullArtifact = keelRepository.getArtifactVersion(
      artifact = deliveryArtifact,
      version = context.version,
      status = null
    )
    requireNotNull(fullArtifact) { "No artifact details found for artifact reference ${context.artifactReference} and version ${context.version} in config ${context.deliveryConfig.application}" }

    return containerRunner.launchContainer(
      config.imageId!!,
      description = "(${context.deliveryConfig.application}) Promoting jar for ${context.artifact.reference} ${context.version} in env ${context.environmentName}",
      serviceAccount = DEFAULT_SERVICE_ACCOUNT,
      application = "keel", // runs in the keel app always so it's hidden from the user and because it will always have the same permissions
      environmentName = context.environmentName,
      location = config.location(),
      environmentVariables = mapOf(
        "REPO_URL" to fullArtifact.repoUrl(gitConfig.gitUrlPrefix),
        "BUILD_NUMBER" to fullArtifact.buildNumber()
      )
    )
  }

  fun PublishedArtifact.buildNumber(): String =
    "$buildNumber"

  private fun PromoteJarConfig.location() : TitusServerGroup.Location {
    requireNotNull(account) { "Titus account must be set in the promote jar config"}
    requireNotNull(region) { "Titus region must be set in the promote jar config" }
    return TitusServerGroup.Location(account!!, region!!)
  }
}

fun PublishedArtifact.repoUrl(gitUrlPrefix: String?): String =
  gitUrlPrefix + gitMetadata?.project + "/" + gitMetadata?.repo?.name + ".git"
