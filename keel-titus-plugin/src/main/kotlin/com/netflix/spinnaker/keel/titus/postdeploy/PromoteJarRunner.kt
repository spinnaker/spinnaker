package com.netflix.spinnaker.keel.titus.postdeploy

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.plugins.PostDeployActionRunner
import com.netflix.spinnaker.keel.api.postdeploy.SupportedPostDeployActionType
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.core.api.PromoteJarPostDeployAction
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.postdeploy.PromoteJarConfig
import com.netflix.spinnaker.keel.titus.AbstractContainerRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * A runner than launches a specific docker container (defined in [PromoteJarConfig]).
 * That container needs to promote the candidate jar identified by the
 * repo (REPO_URL) and jenkins build number (BUILD_NUM) passed in as env
 * variables to the container.
 */
@EnableConfigurationProperties(PromoteJarConfig::class)
@Component
class PromoteJarRunner(
  override val eventPublisher: EventPublisher,
  val config: PromoteJarConfig,
  val keelRepository: KeelRepository,
  taskLauncher: TaskLauncher,
  spectator: Registry
) : PostDeployActionRunner<PromoteJarPostDeployAction>, AbstractContainerRunner(taskLauncher, spectator) {

  override val supportedType = SupportedPostDeployActionType<PromoteJarPostDeployAction>("promote-candidate-jar")

  override fun launch(
    artifact: DeliveryArtifact,
    artifactVersion: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ) {
    requireNotNull(config.imageId) { "Titus image must be set in the promote jar config"}

    launchContainer(
      config.imageId!!,
      subjectLine = "Promoting jar for artifact ${artifact.reference} version $artifactVersion",
      description = "Running post deploy jar promotion for environment $targetEnvironment in application ${deliveryConfig.application}",
      serviceAccount = DEFAULT_SERVICE_ACCOUNT,
      application = "keel", // runs in the keel app always so it's hidden from the user and because it will always have the same permissions
      environmentName = targetEnvironment.name,
      location = config.location(),
      environmentVariables = mapOf(
        "REPO_URL" to "",
        "BUILD_NUMBER" to ""
      )
    )
  }

  private fun PromoteJarConfig.location() : TitusServerGroup.Location {
    requireNotNull(account) { "Titus account must be set in the promote jar config"}
    requireNotNull(region) { "Titus region must be set in the promote jar config" }
    return TitusServerGroup.Location(account!!, region!!)
  }
}
