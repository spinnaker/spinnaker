package com.netflix.spinnaker.keel.api

class NoKnownArtifactVersions(artifact: DeliveryArtifact) : RuntimeException("No versions for ${artifact.type} artifact ${artifact.name} are known")
