package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.clouddriver.MemoryCloudDriverCache
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.test.resource
import org.apache.commons.lang3.RandomStringUtils
import java.time.Duration

internal class ClusterAvailabilityZonesResolverTests : AvailabilityZonesResolverTests<ClusterSpec>() {
  override fun createFixture(
    eastAvailabilityZones: Set<String>?,
    westAvailabilityZones: Set<String>?
  ): Fixture<ClusterSpec> =
    object : Fixture<ClusterSpec>(
      resource(
        apiVersion = SPINNAKER_API_V1.subApi("ec2"),
        kind = "cluster",
        spec = ClusterSpec(
          moniker = Moniker(
            app = "fnord",
            stack = "test"
          ),
          imageProvider = ArtifactImageProvider(DeliveryArtifact("fnord", ArtifactType.DEB)),
          locations = Locations(
            accountName = "test",
            vpcName = "vpc0",
            regions = setOf(
              SubnetAwareRegionSpec(
                region = "us-east-1",
                subnet = "internal (vpc0)",
                availabilityZones = eastAvailabilityZones ?: emptySet()
              ),
              SubnetAwareRegionSpec(
                region = "us-west-2",
                subnet = "internal (vpc0)",
                availabilityZones = westAvailabilityZones ?: emptySet()
              )
            )
          ),
          _defaults = ClusterSpec.ServerGroupSpec(
            launchConfiguration = ClusterSpec.LaunchConfigurationSpec(
              instanceType = "m5.large",
              ebsOptimized = true,
              iamRole = "fnordInstanceProfile",
              instanceMonitoring = false
            ),
            capacity = Capacity(2, 2, 2),
            dependencies = ClusterDependencies(
              loadBalancerNames = setOf("fnord-internal"),
              securityGroupNames = setOf("fnord", "fnord-elb")
            ),
            health = ClusterSpec.HealthSpec(
              warmup = Duration.ofSeconds(120)
            )
          ),
          overrides = mapOf(
            "us-east-1" to ClusterSpec.ServerGroupSpec(
              launchConfiguration = ClusterSpec.LaunchConfigurationSpec(
                iamRole = "fnordEastInstanceProfile",
                keyPair = "fnord-keypair-325719997469-us-east-1"
              ),
              capacity = Capacity(5, 5, 5),
              dependencies = ClusterDependencies(
                loadBalancerNames = setOf("fnord-external"),
                securityGroupNames = setOf("fnord-ext")
              ),
              health = ClusterSpec.HealthSpec(
                healthCheckType = HealthCheckType.ELB
              )
            ),
            "us-west-2" to ClusterSpec.ServerGroupSpec(
              launchConfiguration = ClusterSpec.LaunchConfigurationSpec(
                keyPair = "fnord-keypair-${RandomStringUtils.randomNumeric(12)}-us-west-2"
              )
            )
          )
        )
      )
    ) {
      override val subject: ClusterAvailabilityZonesResolver = ClusterAvailabilityZonesResolver(
        MemoryCloudDriverCache(cloudDriverService)
      )
    }
}
