package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.clouddriver.ClouddriverService
import com.netflix.spinnaker.keel.clouddriver.model.ElasticLoadBalancer.HealthCheck
import com.netflix.spinnaker.keel.clouddriver.model.ElasticLoadBalancer.ListenerDescription
import com.netflix.spinnaker.keel.model.Listener
import com.netflix.spinnaker.keel.model.Protocol.HTTP
import com.netflix.spinnaker.keel.model.Protocol.TCP
import com.netflix.spinnaker.keel.model.Scheme.internal
import java.net.URL

object ElasticLoadBalancerTest : BaseModelParsingTest<ElasticLoadBalancer>() {

  override val json: URL
    get() = javaClass.getResource("/elb.json")

  override val call: ClouddriverService.() -> ElasticLoadBalancer?
    get() = {
      getElasticLoadBalancer(
        "aws",
        "mgmt",
        "us-west-2",
        "covfefe-main-vpc0"
      ).firstOrNull()
    }

  override val expected: ElasticLoadBalancer
    get() = ElasticLoadBalancer(
      loadBalancerName = "covfefe-test-vpc0",
      scheme = internal,
      vpcid = "vpc-ljycv6ep",
      availabilityZones = setOf("us-west-2a", "us-west-2b", "us-west-2c"),
      securityGroups = setOf("sg-skerlbt5", "sg-epos7i16", "sg-feuxpxqk", "sg-k6cc85a1"),
      healthCheck = HealthCheck(
        target = "HTTP:7001/health",
        interval = 10,
        timeout = 5,
        unhealthyThreshold = 2,
        healthyThreshold = 10
      ),
      listenerDescriptions = setOf(
        ListenerDescription(
          listener = Listener(
            protocol = HTTP,
            loadBalancerPort = 80,
            instanceProtocol = HTTP,
            instancePort = 7001
          )
        ),
        ListenerDescription(
          listener = Listener(
            protocol = TCP,
            loadBalancerPort = 443,
            instanceProtocol = TCP,
            instancePort = 7002
          )
        )
      )
    )
}
