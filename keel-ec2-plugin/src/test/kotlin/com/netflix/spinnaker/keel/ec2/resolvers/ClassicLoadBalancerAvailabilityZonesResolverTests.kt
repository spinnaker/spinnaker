package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerHealthCheck
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.clouddriver.MemoryCloudDriverCache
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.test.resource

internal class ClassicLoadBalancerAvailabilityZonesResolverTests : AvailabilityZonesResolverTests<ClassicLoadBalancerSpec>() {
  override fun createFixture(eastAvailabilityZones: Set<String>?, westAvailabilityZones: Set<String>?): Fixture<ClassicLoadBalancerSpec> =
    object : Fixture<ClassicLoadBalancerSpec>(
      resource(
        apiVersion = SPINNAKER_EC2_API_V1,
        kind = "classic-load-balancer",
        spec = ClassicLoadBalancerSpec(
          moniker = Moniker(
            app = "fnord",
            stack = "test"
          ),
          locations = SubnetAwareLocations(
            account = "test",
            vpc = "vpc0",
            subnet = "internal (vpc0)",
            regions = setOf(
              SubnetAwareRegionSpec(
                name = "us-east-1",
                availabilityZones = eastAvailabilityZones ?: emptySet()
              ),
              SubnetAwareRegionSpec(
                name = "us-west-2",
                availabilityZones = westAvailabilityZones ?: emptySet()
              )
            )
          ),
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
