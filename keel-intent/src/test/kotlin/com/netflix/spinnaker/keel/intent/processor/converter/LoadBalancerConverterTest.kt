package com.netflix.spinnaker.keel.intent.processor.converter

import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.ElasticLoadBalancer
import com.netflix.spinnaker.keel.clouddriver.model.ElasticLoadBalancer.ListenerDescription
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.intent.AmazonElasticLoadBalancerSpec
import com.netflix.spinnaker.keel.intent.AvailabilityZoneConfig.Automatic
import com.netflix.spinnaker.keel.intent.HealthCheckSpec
import com.netflix.spinnaker.keel.intent.HealthEndpoint.Http
import com.netflix.spinnaker.keel.intent.HealthEndpoint.Https
import com.netflix.spinnaker.keel.model.Listener
import com.netflix.spinnaker.keel.model.Protocol.*
import com.netflix.spinnaker.keel.model.Scheme.internal
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.fail
import org.junit.jupiter.api.Test

object LoadBalancerConverterTest {

  val cloudDriver = mock<CloudDriverCache>()
  val converter = LoadBalancerConverter(cloudDriver)

  val spec = AmazonElasticLoadBalancerSpec(
    vpcName = "vpcName",
    application = "covfefe",
    name = "covfefe-elb",
    cloudProvider = "aws",
    accountName = "prod",
    region = "us-west-2",
    securityGroupNames = setOf("covfefe", "nf-infrastructure", "nf-datacenter"),
    availabilityZones = Automatic,
    scheme = internal,
    listeners = setOf(Listener(TCP, 80, TCP, 7001), Listener(SSL, 443, SSL, 7002)),
    healthCheck = HealthCheckSpec(Http(7001, "/healthcheck"))
  )

  val elb = ElasticLoadBalancer(
    vpcid = "vpc-1",
    loadBalancerName = "covfefe-elb",
    securityGroups = setOf("1", "2", "3"),
    availabilityZones = setOf("us-west-2a", "us-west-2b", "us-west-2c"),
    scheme = internal,
    healthCheck = ElasticLoadBalancer.HealthCheck("HTTPS:7002/healthcheck", 3, 30, 5, 20),
    listenerDescriptions = setOf(
      ListenerDescription(Listener(HTTP, 80, HTTP, 7001)),
      ListenerDescription(Listener(HTTPS, 443, HTTPS, 7002))
    )
  )

  val vpc = Network(spec.cloudProvider, elb.vpcid!!, spec.vpcName, spec.accountName, spec.region)
  val zones = elb.availabilityZones
  val securityGroups = setOf(
    SecurityGroup(type = "aws", id = "1", name = "nf-infrastructure", description = null, accountName = "prod", region = "us-west-2", vpcId = "vpc-1", moniker = Moniker("covfefe")),
    SecurityGroup(type = "aws", id = "2", name = "nf-datacenter", description = null, accountName = "prod", region = "us-west-2", vpcId = "vpc-1", moniker = Moniker("covfefe")),
    SecurityGroup(type = "aws", id = "3", name = "covfefe", description = null, accountName = "prod", region = "us-west-2", vpcId = "vpc-1", moniker = Moniker("covfefe"))
  )

  @Test
  fun `converts spec to system state`() {
    whenever(cloudDriver.networkBy(spec.vpcName!!, spec.accountName, spec.region)) doReturn vpc
    whenever(cloudDriver.availabilityZonesBy(spec.accountName, vpc.id, spec.region)) doReturn zones

    converter.convertToState(spec)
      .apply {
        vpcid shouldEqual vpc.id
        loadBalancerName shouldEqual spec.name
        healthCheck.target shouldEqual spec.healthCheck.target.toString()
        healthCheck.healthyThreshold shouldEqual spec.healthCheck.healthyThreshold
        healthCheck.interval shouldEqual spec.healthCheck.interval
        healthCheck.timeout shouldEqual spec.healthCheck.timeout
        healthCheck.unhealthyThreshold shouldEqual spec.healthCheck.unhealthyThreshold
        availabilityZones shouldEqual zones
      }
  }

  @Test
  fun `converts system state to spec`() {
    whenever(cloudDriver.networkBy(elb.vpcid!!)) doReturn vpc
    whenever(cloudDriver.securityGroupBy(eq(vpc.account), any())) doAnswer { invocation ->
      securityGroups.firstOrNull { it.id == invocation.arguments[1] }
    }
    whenever(cloudDriver.availabilityZonesBy(vpc.account, vpc.id, vpc.region)) doReturn zones

    converter.convertFromState(elb)
      .let { spec ->
        spec.accountName shouldEqual vpc.account
        spec.cloudProvider shouldEqual vpc.cloudProvider
        spec.name shouldEqual elb.loadBalancerName
        spec.region shouldEqual vpc.region
        spec.securityGroupNames shouldEqual securityGroups.map { it.name }.toSet()
        if (spec is AmazonElasticLoadBalancerSpec) {
          spec.vpcName shouldEqual vpc.name
          spec.availabilityZones shouldEqual Automatic
          spec.healthCheck shouldEqual HealthCheckSpec(Https(7002, "/healthcheck"), 3, 30, 5, 20)
          spec.listeners shouldEqual elb.listenerDescriptions.map { it.listener }.toSet()
          spec.scheme shouldEqual internal
        } else {
          fail("Expected ${AmazonElasticLoadBalancerSpec::class.simpleName} but found ${spec.javaClass}")
        }
      }
  }
}
