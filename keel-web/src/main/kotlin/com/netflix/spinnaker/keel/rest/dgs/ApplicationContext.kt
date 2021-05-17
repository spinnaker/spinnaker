package com.netflix.spinnaker.keel.rest.dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.core.api.PublishedArtifactInEnvironment
import com.netflix.spinnaker.keel.graphql.types.MdArtifactStatusInEnvironment
import com.netflix.spinnaker.kork.exceptions.SystemException

@DgsComponent
class ApplicationContextBuilder : DgsCustomContextBuilder<ApplicationContext?> {
  override fun build(): ApplicationContext {
    return ApplicationContext()
  }
}

class ApplicationContext() {
  var deliveryConfig: DeliveryConfig? = null
  var requestedStatuses: Set<MdArtifactStatusInEnvironment>? = null
  var requestedVersionIds: Set<String>? = null
  var allVersions: Map<ArtifactAndEnvironment, List<PublishedArtifactInEnvironment>> = emptyMap()

  fun getArtifactVersions(deliveryArtifact: DeliveryArtifact, environmentName: String): List<PublishedArtifactInEnvironment>? {
    return allVersions[ArtifactAndEnvironment(artifact = deliveryArtifact, environmentName = environmentName)]
  }

  fun getConfig(): DeliveryConfig {
    return deliveryConfig ?: throw SystemException("Delivery config context is missing")
  }
}
