package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.model.Moniker

fun ClusterSpec.resolve(): Set<ServerGroupSpec> {
  return locations.regions.map {
    ServerGroupSpec(
      moniker = moniker,
      location = Location(
        locations.accountName,
        it.region,
        it.subnet,
        it.availabilityZones
      ),
      launchConfiguration = LaunchConfigurationSpec(
        imageProvider = imageProvider,
        instanceType = checkNotNull(overrides[it.region]?.launchConfiguration?.instanceType ?: defaults.launchConfiguration.instanceType),
        ebsOptimized = checkNotNull(overrides[it.region]?.launchConfiguration?.ebsOptimized ?: defaults.launchConfiguration.ebsOptimized),
        iamRole = checkNotNull(overrides[it.region]?.launchConfiguration?.iamRole ?: defaults.launchConfiguration.iamRole),
        keyPair = checkNotNull(overrides[it.region]?.launchConfiguration?.keyPair ?: defaults.launchConfiguration.keyPair),
        instanceMonitoring = overrides[it.region]?.launchConfiguration?.instanceMonitoring ?: defaults.launchConfiguration.instanceMonitoring ?: false,
        ramdiskId = overrides[it.region]?.launchConfiguration?.ramdiskId ?: defaults.launchConfiguration.ramdiskId
      ),
      capacity = overrides[it.region]?.capacity ?: defaults.capacity ?: Capacity(1, 1, 1),
      // TODO: need to be able to merge these rather than treating them as blocks
      dependencies = overrides[it.region]?.dependencies ?: defaults.dependencies ?: Dependencies(),
      health = overrides[it.region]?.health ?: defaults.health ?: Health(),
      scaling = overrides[it.region]?.scaling ?: defaults.scaling ?: Scaling(),
      tags = overrides[it.region]?.tags ?: defaults.tags ?: emptyMap()
    )
  }
    .toSet()
}

data class ClusterSpec(
  override val moniker: Moniker,
  val imageProvider: ImageProvider,
  val locations: ClusterLocations,
  val defaults: ClusterServerGroupSpec,
  val overrides: Map<String, ClusterServerGroupSpec> = emptyMap()
) : Monikered, ResourceSpec {
  @JsonIgnore
  override val id = "${locations.accountName}:${moniker.name}"
}

data class ClusterLocations(
  val accountName: String,
  val regions: Set<ClusterRegion>
)

data class ClusterRegion(
  val region: String,
  val subnet: String,
  val availabilityZones: Set<String>
)

data class ClusterServerGroupSpec(
  val launchConfiguration: ClusterLaunchConfigurationSpec = ClusterLaunchConfigurationSpec(),
  val capacity: Capacity? = null,
  val dependencies: Dependencies? = null,
  val health: Health? = null,
  val scaling: Scaling? = null,
  val tags: Map<String, String>? = null
)

data class ClusterLaunchConfigurationSpec(
  val instanceType: String? = null,
  val ebsOptimized: Boolean? = null,
  val iamRole: String? = null,
  val keyPair: String? = null,
  val instanceMonitoring: Boolean? = null,
  val ramdiskId: String? = null
)
