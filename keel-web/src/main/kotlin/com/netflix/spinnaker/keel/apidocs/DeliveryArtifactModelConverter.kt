package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import org.springframework.stereotype.Component

@Component
class DeliveryArtifactModelConverter(
  artifactSuppliers: List<ArtifactSupplier<*>>
) : SubtypesModelConverter<DeliveryArtifact>(DeliveryArtifact::class.java) {

  override val discriminator: String? = DeliveryArtifact::type.name

  override val subTypes: List<Class<out DeliveryArtifact>> =
    artifactSuppliers.map {
      it.supportedArtifact.artifactClass
    }

  override val mapping: Map<String, Class<out DeliveryArtifact>> =
    artifactSuppliers.map {
      it.supportedArtifact.name to it.supportedArtifact.artifactClass
    }.toMap()
}
