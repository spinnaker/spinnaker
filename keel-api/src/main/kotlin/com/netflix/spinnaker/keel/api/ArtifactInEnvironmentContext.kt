package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.action.Action
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction

data class ArtifactInEnvironmentContext(
  val deliveryConfig: DeliveryConfig,
  val environmentName: String,
  val artifactReference: String,
  val version: String
) {
  constructor(deliveryConfig: DeliveryConfig, environment: Environment, artifact: PublishedArtifact) :
    this(deliveryConfig, environment.name, artifact.reference, artifact.version)

  val environment: Environment =
    deliveryConfig.environments.first { it.name == environmentName }

  val artifact: DeliveryArtifact =
    deliveryConfig.artifacts.first { it.reference == artifactReference }

  val verifications: Collection<Verification> = environment.verifyWith

  val postDeployActions: Collection<PostDeployAction> = environment.postDeploy

  fun verification(id: String): Verification? = verifications.firstOrNull { it.id == id }

  fun action(type: ActionType, id: String): Action? = when (type) {
    ActionType.VERIFICATION -> verifications.firstOrNull { it.id == id }
    ActionType.POST_DEPLOY -> postDeployActions.firstOrNull { it.id == id }
  }

  fun shortName() = "${deliveryConfig.application}:$environmentName:$artifactReference:$version"
}
