package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.clouddriver.MemoryCloudDriverCache
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.test.resource

internal class ApplicationLoadBalancerAvailabilityZonesResolverTests : AvailabilityZonesResolverTests<ApplicationLoadBalancerSpec>() {
  override fun createFixture(eastAvailabilityZones: Set<String>?, westAvailabilityZones: Set<String>?): Fixture<ApplicationLoadBalancerSpec> =
    object : Fixture<ApplicationLoadBalancerSpec>(
      resource(
        apiVersion = SPINNAKER_EC2_API_V1,
        kind = "application-load-balancer",
        spec = ApplicationLoadBalancerSpec(
          Moniker(
            app = "fnord",
            stack = "test"
          ),
          Locations(
            accountName = "test",
            vpcName = "vpc0",
            subnet = "internal (vpc0)",
            regions = setOf(
              SubnetAwareRegionSpec(
                region = "us-east-1",
                availabilityZones = eastAvailabilityZones ?: emptySet()
              ),
              SubnetAwareRegionSpec(
                region = "us-west-2",
                availabilityZones = westAvailabilityZones ?: emptySet()
              )
            )
          ),
          listeners = emptySet(),
          targetGroups = emptySet()
        )
      )
    ) {
      override val subject: ApplicationLoadBalancerAvailabilityZonesResolver = ApplicationLoadBalancerAvailabilityZonesResolver(
        MemoryCloudDriverCache(cloudDriverService)
      )
    }
}
