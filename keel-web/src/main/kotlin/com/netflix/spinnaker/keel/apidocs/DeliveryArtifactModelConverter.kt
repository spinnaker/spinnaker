package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import org.springframework.stereotype.Component

@Component
class DeliveryArtifactModelConverter : SubtypesModelConverter<DeliveryArtifact>(DeliveryArtifact::class.java) {
  override val subTypes = listOf(
    DebianArtifact::class.java,
    DockerArtifact::class.java
  )

  override val discriminator: String? = DeliveryArtifact::type.name

  // TODO: can we just work this out automatically?
  override val mapping: Map<String, Class<out DeliveryArtifact>> = mapOf(
    ArtifactType.deb.name to DebianArtifact::class.java,
    ArtifactType.docker.name to DockerArtifact::class.java
  )
}
