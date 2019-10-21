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
package com.netflix.spinnaker.keel.api.titus.cluster

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.netflix.spinnaker.keel.api.Capacity
import com.netflix.spinnaker.keel.api.ClusterDependencies
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.MultiRegion
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.clouddriver.model.Constraints
import com.netflix.spinnaker.keel.clouddriver.model.MigrationPolicy
import com.netflix.spinnaker.keel.clouddriver.model.Resources
import com.netflix.spinnaker.keel.model.Moniker

/**
 * "Simplified" representation of
 * https://github.com/Netflix/titus-api-definitions/blob/master/src/main/proto/netflix/titus/titus_job_api.proto
 */
data class TitusClusterSpec(
  override val moniker: Moniker,
  override val locations: SimpleLocations,
  val container: ContainerSpec,
  private val _defaults: TitusServerGroupSpec,
  val overrides: Map<String, TitusServerGroupSpec> = emptyMap()
) : MultiRegion, Locatable<SimpleLocations> {

  @JsonIgnore
  override val id = "${locations.account}:${moniker.name}"

  override val regionalIds = locations.regions.map { clusterRegion ->
    "${locations.account}:${clusterRegion.name}:${moniker.name}"
  }.sorted()

  val defaults: TitusServerGroupSpec
    @JsonUnwrapped get() = _defaults

  @JsonCreator
  constructor(
    moniker: Moniker,
    locations: SimpleLocations,
    container: ContainerSpec,
    capacity: Capacity?,
    constraints: Constraints?,
    containerOptions: ContainerOptions?,
    dependencies: ClusterDependencies?,
    tags: Map<String, String>?,
    overrides: Map<String, TitusServerGroupSpec> = emptyMap()
  ) : this(
    moniker,
    locations,
    container,
    TitusServerGroupSpec(
      capacity,
      constraints,
      containerOptions,
      dependencies,
      container,
      tags
    ),
    overrides
  )
}

data class Container(
  val image: String,
  val digest: String,
  val tag: String
)

data class ContainerSpec(
  val organization: String,
  val registry: String,
  val image: String,
  val digest: String,
  val tag: String
)

data class ContainerOptions(
  val iamProfile: String,
  val entryPoint: String = "",
  val resources: Resources = Resources(),
  val env: Map<String, String> = emptyMap(),
  val constraints: Constraints = Constraints(),
  val capacityGroup: String,
  val migrationPolicy: MigrationPolicy = MigrationPolicy()
)

data class TitusServerGroupSpec(
  val capacity: Capacity? = null,
  val constraints: Constraints? = null,
  val containerOptions: ContainerOptions? = null,
  val dependencies: ClusterDependencies? = null,
  val container: ContainerSpec? = null,
  val tags: Map<String, String>? = null,
  val deferredInitialization: Boolean? = null,
  val delayBeforeDisableSec: Int? = null,
  val delayBeforeScaleDownSec: Int? = null
)

private fun TitusClusterSpec.resolveCapacity(region: String) =
  overrides[region]?.capacity ?: defaults.capacity ?: Capacity(1, 1, 1)

private fun TitusClusterSpec.resolveContainer(region: String): Container =
  checkNotNull(overrides[region]?.container?.toContainer() ?: defaults.container?.toContainer()) {
    "No docker container supplied for $region"
  }

private fun TitusClusterSpec.resolveContainerOptions(region: String, application: String): ContainerOptions =
  overrides[region]?.containerOptions ?: defaults.containerOptions ?: ContainerOptions(
    iamProfile = "${application}InstanceProfile",
    capacityGroup = application
  )

private fun TitusClusterSpec.resolveDependencies(region: String): ClusterDependencies =
  ClusterDependencies(
    loadBalancerNames = defaults.dependencies?.loadBalancerNames + overrides[region]?.dependencies?.loadBalancerNames,
    securityGroupNames = defaults.dependencies?.securityGroupNames + overrides[region]?.dependencies?.securityGroupNames,
    targetGroups = defaults.dependencies?.targetGroups + overrides[region]?.dependencies?.targetGroups
  )

private fun ContainerSpec.toContainer() =
  Container(
    image = image,
    digest = digest,
    tag = tag
  )

fun TitusClusterSpec.resolve(): Set<TitusServerGroup> =
  locations.regions.map {
    TitusServerGroup(
      name = moniker.name,
      location = Location(
        account = locations.account,
        region = it.name
      ),
      capacity = resolveCapacity(it.name),
      container = resolveContainer(it.name),
      containerOptions = resolveContainerOptions(it.name, moniker.app),
      dependencies = resolveDependencies(it.name),
      tags = defaults.tags + overrides[it.name]?.tags
    )
  }
    .toSet()

private operator fun <E> Set<E>?.plus(elements: Set<E>?): Set<E> =
  when {
    this == null || isEmpty() -> elements ?: emptySet()
    elements == null || elements.isEmpty() -> this
    else -> mutableSetOf<E>().also {
      it.addAll(this)
      it.addAll(elements)
    }
  }

private operator fun <K, V> Map<K, V>?.plus(map: Map<K, V>?): Map<K, V> =
  when {
    this == null || isEmpty() -> map ?: emptyMap()
    map == null || map.isEmpty() -> this
    else -> mutableMapOf<K, V>().also {
      it.putAll(this)
      it.putAll(map)
    }
  }
