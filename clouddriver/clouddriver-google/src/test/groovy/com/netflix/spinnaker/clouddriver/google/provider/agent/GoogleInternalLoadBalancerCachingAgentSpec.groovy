package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.google.api.services.compute.model.ForwardingRule
import spock.lang.Specification

class GoogleInternalLoadBalancerCachingAgentSpec extends Specification {

  void "internal passthrough ownership rejects regional external backend-service forwarding rules"() {
    expect:
    !GoogleInternalLoadBalancerCachingAgent.isInternalPassthroughRule(
      new ForwardingRule(
        loadBalancingScheme: "EXTERNAL",
        backendService: "projects/test/regions/us-central1/backendServices/external-backend",
        IPProtocol: "TCP"
      )
    )
  }

  void "internal passthrough ownership accepts only internal backend-service forwarding rules without targets"() {
    expect:
    GoogleInternalLoadBalancerCachingAgent.isInternalPassthroughRule(
      new ForwardingRule(
        loadBalancingScheme: "INTERNAL",
        backendService: "projects/test/regions/us-central1/backendServices/internal-backend",
        IPProtocol: "TCP"
      )
    )

    and:
    !GoogleInternalLoadBalancerCachingAgent.isInternalPassthroughRule(
      new ForwardingRule(
        loadBalancingScheme: "INTERNAL",
        backendService: "projects/test/regions/us-central1/backendServices/internal-backend",
        target: "projects/test/regions/us-central1/targetHttpProxies/proxy",
        IPProtocol: "TCP"
      )
    )
  }
}
