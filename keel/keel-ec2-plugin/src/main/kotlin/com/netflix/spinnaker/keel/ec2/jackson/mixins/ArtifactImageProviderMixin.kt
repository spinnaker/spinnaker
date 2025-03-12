package com.netflix.spinnaker.keel.ec2.jackson.mixins

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus

interface ArtifactImageProviderMixin {
  @get:JsonInclude(NON_EMPTY)
  val artifactStatuses: List<ArtifactStatus>
}
