package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact

interface ArtifactHandler {
  val name: String
    get() = javaClass.simpleName

  suspend fun handle(artifact: DeliveryArtifact)
}
