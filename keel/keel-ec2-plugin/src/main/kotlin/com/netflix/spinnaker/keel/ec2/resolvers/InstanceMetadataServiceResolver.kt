package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.InstanceMetadataServiceVersion
import com.netflix.spinnaker.keel.api.ec2.InstanceMetadataServiceVersion.V1
import com.netflix.spinnaker.keel.api.ec2.InstanceMetadataServiceVersion.V2
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.ec2.optics.clusterSpecDefaultsLens
import com.netflix.spinnaker.keel.ec2.optics.launchConfigurationSpecInstanceMetadataServiceVersionLens
import com.netflix.spinnaker.keel.ec2.optics.serverGroupSpecLaunchConfigurationSpecLens
import com.netflix.spinnaker.keel.environments.DependentEnvironmentFinder
import com.netflix.spinnaker.keel.optics.resourceSpecLens
import com.netflix.spinnaker.keel.persistence.FeatureRolloutRepository
import com.netflix.spinnaker.keel.rollout.RolloutAwareResolver

/**
 * Resolves the [LaunchConfigurationSpec.instanceMetadataServiceVersion] value if it is not explicitly specified.
 *
 * If the cluster already uses [InstanceMetadataServiceVersion.V2], or the setting has been applied to all clusters in
 * dependent environments, and those environments are stable, this resolver will select v2. Otherwise it will select v1.
 */
class InstanceMetadataServiceResolver(
  dependentEnvironmentFinder: DependentEnvironmentFinder,
  resourceToCurrentState: suspend (Resource<ClusterSpec>) -> Map<String, ServerGroup>,
  featureRolloutRepository: FeatureRolloutRepository,
  eventPublisher: EventPublisher
) : RolloutAwareResolver<ClusterSpec, Map<String, ServerGroup>>(
  dependentEnvironmentFinder,
  resourceToCurrentState,
  featureRolloutRepository,
  eventPublisher
) {
  override val supportedKind = EC2_CLUSTER_V1_1
  override val featureName = "imdsv2"

  override fun isAppliedTo(actualResource: Map<String, ServerGroup>) =
    actualResource.values.all { it.launchConfiguration.requireIMDSv2 }

  override fun isExplicitlySpecified(resource: Resource<ClusterSpec>) =
    clusterResourceInstanceMetadataServiceVersionLens.get(resource) != null

  override fun activate(resource: Resource<ClusterSpec>) =
    clusterResourceInstanceMetadataServiceVersionLens.set(resource, V2)

  override fun deactivate(resource: Resource<ClusterSpec>) =
    clusterResourceInstanceMetadataServiceVersionLens.set(resource, V1)

  override val Map<String, ServerGroup>.exists: Boolean
    get() = isNotEmpty()

  /**
   * Composed lens that lets us set the deeply nested [LaunchConfigurationSpec.instanceMetadataServiceVersion] property
   * directly on the [Resource].
   */
  private val clusterResourceInstanceMetadataServiceVersionLens =
    resourceSpecLens<ClusterSpec>() compose clusterSpecDefaultsLens compose serverGroupSpecLaunchConfigurationSpecLens compose launchConfigurationSpecInstanceMetadataServiceVersionLens
}
