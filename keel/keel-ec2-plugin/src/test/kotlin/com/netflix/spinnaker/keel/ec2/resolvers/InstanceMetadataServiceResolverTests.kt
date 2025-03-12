package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.InstanceMetadataServiceVersion
import com.netflix.spinnaker.keel.api.ec2.InstanceMetadataServiceVersion.V1
import com.netflix.spinnaker.keel.api.ec2.InstanceMetadataServiceVersion.V2
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.Location
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.LaunchConfiguration
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.ec2.optics.clusterSpecAccountLens
import com.netflix.spinnaker.keel.ec2.optics.clusterSpecStackLens
import com.netflix.spinnaker.keel.environments.DependentEnvironmentFinder
import com.netflix.spinnaker.keel.persistence.FeatureRolloutRepository
import com.netflix.spinnaker.keel.rollout.RolloutAwareResolverTests
import strikt.api.*
import strikt.assertions.*

internal class InstanceMetadataServiceResolverTests :
  RolloutAwareResolverTests<ClusterSpec, Map<String, ServerGroup>, InstanceMetadataServiceResolver>() {

  override fun createResolver(
    dependentEnvironmentFinder: DependentEnvironmentFinder,
    resourceToCurrentState: suspend (Resource<ClusterSpec>) -> Map<String, ServerGroup>,
    featureRolloutRepository: FeatureRolloutRepository,
    eventPublisher: EventPublisher
  ) = InstanceMetadataServiceResolver(
    dependentEnvironmentFinder,
    resourceToCurrentState,
    featureRolloutRepository,
    eventPublisher
  )

  override val kind = EC2_CLUSTER_V1_1.kind

  override val spec = ClusterSpec(
    moniker = Moniker(
      app = "fnord"
    ),
    locations = SubnetAwareLocations(
      account = "prod",
      subnet = "internal",
      regions = setOf(
        SubnetAwareRegionSpec(name = "us-west-2")
      )
    )
  )

  override val previousEnvironmentSpec =
    clusterSpecAccountLens.set(clusterSpecStackLens.set(spec, "test"), "test")

  override val nonExistentResolvedResource = emptyMap<String, ServerGroup>()

  override fun ClusterSpec.withFeatureApplied() = withInstanceMetadataServiceVersion(V2)

  override fun ClusterSpec.withFeatureNotApplied() = withInstanceMetadataServiceVersion(V1)

  override fun Assertion.Builder<Resource<ClusterSpec>>.featureIsApplied() =
    apply {
      instanceMetadataServiceVersion isEqualTo V2
    }

  override fun Assertion.Builder<Resource<ClusterSpec>>.featureIsNotApplied() =
    apply {
      instanceMetadataServiceVersion isEqualTo V1
    }

  private val Assertion.Builder<Resource<ClusterSpec>>.instanceMetadataServiceVersion: Assertion.Builder<InstanceMetadataServiceVersion?>
    get() = get(Resource<ClusterSpec>::spec)
      .get(ClusterSpec::defaults)
      .get(ClusterSpec.ServerGroupSpec::launchConfiguration)
      .isNotNull()
      .get(LaunchConfigurationSpec::instanceMetadataServiceVersion)

  private fun ClusterSpec.withInstanceMetadataServiceVersion(version: InstanceMetadataServiceVersion?) =
    copy(
      _defaults = defaults.copy(
        launchConfiguration = LaunchConfigurationSpec(
          instanceMetadataServiceVersion = version
        )
      )
    )

  override fun ClusterSpec.toResolvedType(featureActive: Boolean) =
    locations.regions.map(SubnetAwareRegionSpec::name).associateWith { region ->
      ServerGroup(
        name = "${moniker}-v001",
        location = Location(
          locations.account,
          region,
          locations.vpc!!,
          locations.subnet!!,
          "abc".map { "${region}$it" }.toSet()
        ),
        launchConfiguration = LaunchConfiguration(
          imageId = "ami-001",
          appVersion = "$application-v001",
          baseImageName = "bionic-v001",
          instanceType = "m5.xl",
          iamRole = "${application}Role",
          keyPair = "${application}KeyPair",
          requireIMDSv2 = featureActive
        )
      )
    }
}
