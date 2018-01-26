package com.netflix.spinnaker.keel.intent.aws.loadbalancer

import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.AvailabilityZoneConfig.Automatic
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.HealthEndpoint.Http
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.HealthEndpoint.Https
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.Protocol.*
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.Scheme.internal
import com.nhaarman.mockito_kotlin.*
import org.junit.jupiter.api.Test

object ClassicLoadBalancerConverterTest {

  val cloudDriver = mock<CloudDriverCache>()
  val converter = ClassicLoadBalancerConverter(cloudDriver)

  val spec = ClassicLoadBalancerSpec(
    vpcName = "vpcName",
    application = "covfefe",
    name = "covfefe-elb",
    accountName = "prod",
    region = "us-west-2",
    securityGroupNames = sortedSetOf("covfefe", "nf-infrastructure", "nf-datacenter"),
    availabilityZones = Automatic,
    scheme = internal,
    listeners = setOf(ClassicListener(TCP, 80, TCP, 7001), ClassicListener(SSL, 443, SSL, 7002)),
    healthCheck = HealthCheckSpec(Http(7001, "/healthcheck"))
  )

  val elb = LoadBalancerDescription()
    .withVPCId("vpc-1")
    .withLoadBalancerName("covfefe-elb")
    .withSecurityGroups("1", "2", "3")
    .withAvailabilityZones("us-west-2a", "us-west-2b", "us-west-2c")
    .withScheme("internal")
    .withHealthCheck(
      HealthCheck()
        .withTarget("HTTPS:7002/healthcheck")
        .withHealthyThreshold(3)
        .withInterval(30)
        .withUnhealthyThreshold(5)
        .withTimeout(20)
    )
    .withListenerDescriptions(
      ListenerDescription()
        .withListener(
          Listener().withProtocol("HTTP").withLoadBalancerPort(80).withInstanceProtocol("HTTP").withInstancePort(7001)
        )
        .withListener(
          Listener().withProtocol("HTTPS").withLoadBalancerPort(443).withInstanceProtocol("HTTPS").withInstancePort(7002)
        )
    )

  val vpc = Network(spec.cloudProvider(), elb.vpcId!!, spec.vpcName, spec.accountName, spec.region)
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
        vpcId shouldEqual vpc.id
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
    whenever(cloudDriver.networkBy(elb.vpcId!!)) doReturn vpc
    whenever(cloudDriver.securityGroupSummaryBy(eq(vpc.account), eq(vpc.region), any())) doAnswer { invocation ->
      securityGroups.firstOrNull { it.id == invocation.arguments[2] }?.toSummary()
    }
    whenever(cloudDriver.availabilityZonesBy(vpc.account, vpc.id, vpc.region)) doReturn zones

    converter.convertFromState(elb)
      .let { spec ->
        spec.accountName shouldEqual vpc.account
        spec.cloudProvider() shouldEqual vpc.cloudProvider
        spec.name shouldEqual elb.loadBalancerName
        spec.region shouldEqual vpc.region
        spec.securityGroupNames shouldEqual securityGroups.map { it.name }.toSet()
        spec.vpcName shouldEqual vpc.name
        spec.availabilityZones shouldEqual Automatic
        spec.healthCheck shouldEqual HealthCheckSpec(Https(7002, "/healthcheck"), 30, 20, 5, 3)
        spec.listeners shouldEqual elb.listenerDescriptions.map { it.listener.toClassicListener() }.toSet()
        spec.scheme shouldEqual internal
      }
  }

  fun SecurityGroup.toSummary() : SecurityGroupSummary {
    return SecurityGroupSummary(name, id!!)
  }
}
