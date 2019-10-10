package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerHealthCheck
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.clouddriver.MemoryCloudDriverCache
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.test.resource

internal class ClassicLoadBalancerAvailabilityZonesResolverTests : AvailabilityZonesResolverTests<ClassicLoadBalancerSpec>() {
  override fun createFixture(eastAvailabilityZones: Set<String>?, westAvailabilityZones: Set<String>?): Fixture<ClassicLoadBalancerSpec> =
    object : Fixture<ClassicLoadBalancerSpec>(
      resource(
        apiVersion = SPINNAKER_API_V1.subApi("ec2"),
        kind = "classic-load-balancer",
        spec = ClassicLoadBalancerSpec(
          Moniker(
            app = "fnord",
            stack = "test"
          ),
          Locations(
            accountName = "test",
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
          vpcName = "vpc0",
          healthCheck = ClassicLoadBalancerHealthCheck(
            target = "/healthcheck"
          )
        )
      )
    ) {
      override val subject: ClassicLoadBalancerAvailabilityZonesResolver = ClassicLoadBalancerAvailabilityZonesResolver(
        MemoryCloudDriverCache(cloudDriverService)
      )
    }
}
