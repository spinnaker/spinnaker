package com.netflix.spinnaker.keel.core

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.kork.exceptions.IntegrationException

class NoKnownArtifactVersions(artifact: DeliveryArtifact) :
  IntegrationException("No versions for ${artifact.type} artifact ${artifact.name} are known")
