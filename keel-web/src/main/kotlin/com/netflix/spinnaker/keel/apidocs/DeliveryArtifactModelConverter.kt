package com.netflix.spinnaker.keel.apidocs

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
}
