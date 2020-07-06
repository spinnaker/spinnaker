package com.netflix.spinnaker.keel.ec2.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import java.time.Duration

interface ClusterSpecMixin {
  @get:JsonInclude(NON_EMPTY)
  val overrides: Map<String, ServerGroupSpec>

  @get:JsonIgnore
  val artifactType: ArtifactType?

  @get:JsonIgnore
  val artifactVersion: String?

  @get:JsonIgnore
  val maxDiffCount: Int?

  @get:JsonIgnore
  val unhappyWaitTime: Duration?

  @get:JsonIgnore
  val id: String

  @get:JsonUnwrapped
  val defaults: ServerGroupSpec

  @get:JsonIgnore
  val artifactName: String?

  @get:JsonIgnore
  val artifactReference: String?
}
