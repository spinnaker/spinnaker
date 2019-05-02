package com.netflix.spinnaker.keel.bakery

import com.netflix.spinnaker.keel.api.DeliveryArtifact

class NoKnownArtifactVersions(artifact: DeliveryArtifact) : RuntimeException("No versions for ${artifact.type} artifact ${artifact.name} are known")
