package com.netflix.spinnaker.keel.network

import com.netflix.spinnaker.config.DnsConfig
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerDependencies
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType.CLASSIC
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.network.NetworkEndpointType.DNS
import com.netflix.spinnaker.keel.test.TEST_API_V1
import com.netflix.spinnaker.keel.test.computeResource
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery as every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.contains
import java.time.Duration

class NetworkEndpointProviderTests : JUnit5Minutests {
  class Fixture {
    val cloudDriverCache: CloudDriverCache = mockk()
    val dnsConfig = DnsConfig("acme.net")
    val computeResource = computeResource()
    val loadBalancerResource: Resource<LoadBalancerSpec> = resource(
      kind = TEST_API_V1.qualify("loadBalancer"),
      spec = object : LoadBalancerSpec {
        override val loadBalancerType = CLASSIC
        override val locations = SubnetAwareLocations("test", "subnet", regions = setOf(SubnetAwareRegionSpec("us-east-1")))
        override val internal = true
        override val dependencies = LoadBalancerDependencies()
        override val idleTimeout = Duration.ZERO
        override val moniker = Moniker("fnord", "dummy", "loadBalancer")
        override val id = moniker.toName()
      }
    )
    val subject = NetworkEndpointProvider(cloudDriverCache, dnsConfig)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("NetworkEndpointProvider") {
      before {
        every {
          cloudDriverCache.credentialBy("test")
        } returns Credential("test", "aws", "test", mutableMapOf("accountId" to "1234567890"))
      }

      test("returns endpoints for compute resource") {
        with(computeResource) {
          val endpoints = subject.getNetworkEndpoints(this)
          expectThat(endpoints).contains(
            NetworkEndpoint(DNS, "us-east-1", "${spec.moniker.toName()}.vip.us-east-1.test.acme.net"),
            NetworkEndpoint(DNS, "us-east-1", "${spec.moniker.toName()}.cluster.us-east-1.test.acme.net")
          )
        }
      }

      test("returns endpoints for load balancer") {
        with(loadBalancerResource) {
          val endpoints = subject.getNetworkEndpoints(this)
          expectThat(endpoints).contains(
            NetworkEndpoint(DNS, "us-east-1",  "internal-${spec.moniker.toName()}-vpc0-1234567890.us-east-1.elb.amazonaws.com")
          )
        }
      }
    }
  }
}