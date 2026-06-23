package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.HealthCheck
import com.google.api.services.compute.model.TCPHealthCheck
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSessionAffinity
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Unroll

class GoogleRegionalExternalNetworkLoadBalancerCachingAgentSpec extends Specification {
  private static final String ACCOUNT = "auto"
  private static final String PROJECT = "my-project"
  private static final String REGION = "us-central1"

  @Unroll
  void "regional external network ownership accepts #protocol passthrough forwarding rules"() {
    expect:
    GoogleRegionalExternalNetworkLoadBalancerCachingAgent.isRegionalExternalNetworkPassthroughRule(
      new ForwardingRule(
        loadBalancingScheme: "EXTERNAL",
        backendService: "projects/test/regions/us-central1/backendServices/backend-service",
        IPProtocol: protocol
      )
    )

    where:
    protocol << ["TCP", "UDP"]
  }

  @Unroll
  void "regional external network ownership rejects #reason"() {
    expect:
    !GoogleRegionalExternalNetworkLoadBalancerCachingAgent.isRegionalExternalNetworkPassthroughRule(forwardingRule)

    where:
    reason                    | forwardingRule
    "internal scheme"          | new ForwardingRule(loadBalancingScheme: "INTERNAL", backendService: "backend-service", IPProtocol: "TCP")
    "external managed scheme"  | new ForwardingRule(loadBalancingScheme: "EXTERNAL_MANAGED", backendService: "backend-service", IPProtocol: "TCP")
    "target proxy rule"        | new ForwardingRule(loadBalancingScheme: "EXTERNAL", backendService: "backend-service", target: "targetHttpProxies/proxy", IPProtocol: "TCP")
    "target pool network rule" | new ForwardingRule(loadBalancingScheme: "EXTERNAL", target: "targetPools/pool", IPProtocol: "TCP")
    "missing backend service"  | new ForwardingRule(loadBalancingScheme: "EXTERNAL", IPProtocol: "TCP")
    "unsupported protocol"     | new ForwardingRule(loadBalancingScheme: "EXTERNAL", backendService: "backend-service", IPProtocol: "ESP")
  }

  void "regional external network agent does not report pending on-demand requests"() {
    expect:
    createAgent().pendingOnDemandRequests(null) == []
  }

  void "on-demand singleton callback ignores not found and rejects wrong load balancer type"() {
    given:
    def callback = createForwardingRuleCallbacks(createAgent())
    callback.loadBalancers = []
    callback.failedLoadBalancers = []
    def singletonCallback = callback.newForwardingRuleSingletonCallback()

    when:
    singletonCallback.onFailure(new GoogleJsonError(code: 404), null)

    then:
    noExceptionThrown()

    when:
    singletonCallback.onSuccess(
      new ForwardingRule(loadBalancingScheme: "INTERNAL", backendService: "backend-service", IPProtocol: "TCP"),
      null
    )

    then:
    thrown(IllegalArgumentException)
  }

  void "cache graph includes regional backend service and regional health check"() {
    given:
    def loadBalancers = []
    def failedLoadBalancers = []
    def callback = createForwardingRuleCallbacks(createAgent())
    callback.loadBalancers = loadBalancers
    callback.failedLoadBalancers = failedLoadBalancers
    callback.groupHealthRequest = null
    callback.projectRegionBackendServices = [
        new BackendService(
          name: "backend-service",
          loadBalancingScheme: "EXTERNAL",
          sessionAffinity: "CLIENT_IP",
          healthChecks: ["projects/${PROJECT}/regions/${REGION}/healthChecks/tcp-hc"]
        )
      ]
    callback.projectRegionalHealthChecks = [
        new HealthCheck(
          name: "tcp-hc",
          checkIntervalSec: 10,
          timeoutSec: 5,
          unhealthyThreshold: 3,
          healthyThreshold: 2,
          tcpHealthCheck: new TCPHealthCheck(port: 8080)
        )
      ]

    when:
    callback.cacheRemainderOfLoadBalancerResourceGraph(
      new ForwardingRule(
        name: "lb-name",
        region: "projects/${PROJECT}/regions/${REGION}",
        loadBalancingScheme: "EXTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/backend-service",
        IPProtocol: "TCP",
        IPAddress: "1.2.3.4",
        ports: ["80", "443"],
        networkTier: "PREMIUM"
      )
    )

    then:
    failedLoadBalancers == []
    loadBalancers.size() == 1
    loadBalancers[0].name == "lb-name"
    loadBalancers[0].backendService.name == "backend-service"
    loadBalancers[0].backendService.sessionAffinity == GoogleSessionAffinity.CLIENT_IP
    loadBalancers[0].backendService.healthCheck.name == "tcp-hc"
    loadBalancers[0].backendService.healthCheck.healthCheckType == GoogleHealthCheck.HealthCheckType.TCP
    loadBalancers[0].backendService.healthCheck.port == 8080
  }

  private GoogleRegionalExternalNetworkLoadBalancerCachingAgent createAgent() {
    new GoogleRegionalExternalNetworkLoadBalancerCachingAgent(
      "clouddriver",
      new GoogleNamedAccountCredentials.Builder()
        .name(ACCOUNT)
        .project(PROJECT)
        .compute(Mock(Compute))
        .credentials(Mock(GoogleCredentials))
        .build(),
      new ObjectMapper(),
      new DefaultRegistry(),
      REGION
    )
  }

  private static Object createForwardingRuleCallbacks(GoogleRegionalExternalNetworkLoadBalancerCachingAgent agent) {
    def callbacksClass = Class.forName("${GoogleRegionalExternalNetworkLoadBalancerCachingAgent.name}\$ForwardingRuleCallbacks")
    def constructor = callbacksClass.getDeclaredConstructor(GoogleRegionalExternalNetworkLoadBalancerCachingAgent)
    constructor.accessible = true
    constructor.newInstance(agent)
  }
}
