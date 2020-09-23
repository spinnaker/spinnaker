/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.api.titus

import com.netflix.spinnaker.keel.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.UnhappyControl
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.schema.Factory
import com.netflix.spinnaker.keel.api.schema.Optional
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.docker.VersionedTagProvider
import java.time.Duration

/**
 * "Simplified" representation of
 * https://github.com/Netflix/titus-api-definitions/blob/master/src/main/proto/netflix/titus/titus_job_api.proto
 */
data class TitusClusterSpec(
  override val moniker: Moniker,
  val deployWith: ClusterDeployStrategy = RedBlack(),
  @param:Optional override val locations: SimpleLocations,
  private val _defaults: TitusServerGroupSpec,
  val overrides: Map<String, TitusServerGroupSpec> = emptyMap(),
  override val artifactType: ArtifactType? = DOCKER,
  private val _artifactName: String? = null, // Custom backing field for artifactName, used by resolvers
  override val artifactVersion: String? = null,
  val container: ContainerProvider,
  override val maxDiffCount: Int? = 2,
  // Once clusters go unhappy, only retry when the diff changes, or if manually unvetoed
  override val unhappyWaitTime: Duration? = null
) : ComputeResourceSpec, Monikered, Locatable<SimpleLocations>, UnhappyControl {

  @Factory
  constructor(
    moniker: Moniker,
    deployWith: ClusterDeployStrategy = RedBlack(),
    @Optional locations: SimpleLocations,
    container: ContainerProvider,
    capacity: Capacity?,
    constraints: TitusServerGroup.Constraints?,
    env: Map<String, String>?,
    containerAttributes: Map<String, String>?,
    resources: ResourcesSpec?,
    iamProfile: String?,
    entryPoint: String?,
    capacityGroup: String?,
    migrationPolicy: TitusServerGroup.MigrationPolicy?,
    dependencies: ClusterDependencies?,
    tags: Map<String, String>?,
    overrides: Map<String, TitusServerGroupSpec> = emptyMap()
  ) : this(
    moniker = moniker,
    deployWith = deployWith,
    locations = locations,
    _defaults = TitusServerGroupSpec(
      capacity = capacity,
      capacityGroup = capacityGroup,
      constraints = constraints,
      dependencies = dependencies,
      entryPoint = entryPoint,
      env = env,
      containerAttributes = containerAttributes,
      iamProfile = iamProfile,
      migrationPolicy = migrationPolicy,
      resources = resources,
      tags = tags
    ),
    overrides = overrides,
    container = container
  )

  override val id = "${locations.account}:$moniker"

  val defaults: TitusServerGroupSpec
    get() = _defaults

  // Returns the artifact name set by resolvers, or attempts to find the artifact name from the container provider.
  override val artifactName: String?
    get() = _artifactName
      ?: when (container) {
        is DigestProvider -> container.repository()
        is VersionedTagProvider -> container.repository()
        else -> null
      }

  // Provides a hint as to cluster -> artifact linkage even _without_ resolvers being applied, by delegating to the
  // image provider.
  override val artifactReference: String?
    get() = when (container) {
      is ReferenceProvider -> container.reference
      else -> null
    }
}

data class TitusServerGroupSpec(
  val capacity: Capacity? = null,
  val capacityGroup: String? = null,
  val constraints: TitusServerGroup.Constraints? = null,
  val dependencies: ClusterDependencies? = null,
  val entryPoint: String? = null,
  val env: Map<String, String>? = null,
  val containerAttributes: Map<String, String>? = null,
  val iamProfile: String? = null,
  val migrationPolicy: TitusServerGroup.MigrationPolicy? = null,
  val resources: ResourcesSpec? = null,
  val tags: Map<String, String>? = null
)

data class ResourcesSpec(
  val cpu: Int? = null,
  val disk: Int? = null,
  val gpu: Int? = null,
  val memory: Int? = null,
  val networkMbps: Int? = null
) {
  // titus limits documented here:
  // https://github.com/Netflix/titus-control-plane/blob/master/titus-api/src/main/java/com/netflix/titus/api/jobmanager/model/job/sanitizer/JobConfiguration.java
  init {
    require(cpu == null || cpu in 1..64) { "cpu not within titus limits of 1 to 64" }
    require(disk == null || disk in 10000..999000) { "disk not within titus limits of 10000 to 999000" }
    require(gpu == null || gpu in 0..16) { "gpu not within titus limits of 0 to 16" }
    require(memory == null || memory in 512..472000) { "memory not within titus limits of 512 to 472000" }
    require(networkMbps == null || networkMbps in 128..40000) { "networkMbps not within titus limits of 128 to 40000" }
  }
}
