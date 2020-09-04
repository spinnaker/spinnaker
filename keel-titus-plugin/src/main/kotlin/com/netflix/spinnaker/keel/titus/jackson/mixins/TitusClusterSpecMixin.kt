package com.netflix.spinnaker.keel.titus.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.docker.ContainerProvider
import java.time.Duration

interface TitusClusterSpecMixin {
  @get:JsonIgnore
  val id: String

  @get:JsonInclude(NON_EMPTY)
  val overrides: Map<String, TitusServerGroupSpec>

  @get:JsonIgnore
  val artifactType: ArtifactType?

  @get:JsonIgnore
  val artifactName: String?

  @get:JsonIgnore
  val artifactReference: String?

  @get:JsonIgnore
  val artifactVersion: String?

  @get:JsonIgnore
  val container: ContainerProvider

  @get:JsonIgnore
  val maxDiffCount: Int?

  @get:JsonIgnore
  val unhappyWaitTime: Duration?

  @get:JsonUnwrapped
  val defaults: TitusServerGroupSpec
}
