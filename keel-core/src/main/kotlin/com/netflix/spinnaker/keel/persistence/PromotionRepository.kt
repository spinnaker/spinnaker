package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig

interface PromotionRepository {

  /**
   * @return the latest version of [artifact] approved for use in [targetEnvironment].
   */
  fun latestVersionApprovedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: String
  ): String?

  /**
   * Marks [version] as approved for deployment to [targetEnvironment]. This means it has passed
   * any constraints on the environment and may be selected for use in the desired state of any
   * clusters in the environment.
   */
  fun approveVersionFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  )

  /**
   * @return `true` if [version] has (previously or currently) been deployed successfully to
   * [targetEnvironment].
   */
  fun wasSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean

  /**
   * Marks [version] as successfully deployed to [targetEnvironment] (i.e. future calls to
   * [wasSuccessfullyDeployedTo] will return `true` for that version.
   */
  fun markAsSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  )
}
