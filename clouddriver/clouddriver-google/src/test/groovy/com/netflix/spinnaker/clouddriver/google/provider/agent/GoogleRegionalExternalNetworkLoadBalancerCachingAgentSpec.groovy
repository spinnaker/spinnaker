/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.Backend
import com.google.api.services.compute.model.BackendServiceList
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.google.api.services.compute.model.HealthCheck
import com.google.api.services.compute.model.HealthCheckList
import com.google.api.services.compute.model.ResourceGroupReference
import com.google.api.services.compute.model.TCPHealthCheck
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
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

  void "constructLoadBalancers lists regional external network passthrough load balancers"() {
    given:
    def compute = Mock(Compute)
    def forwardingRules = Mock(Compute.ForwardingRules)
    def forwardingRulesList = Mock(Compute.ForwardingRules.List)
    def regionBackendServices = Mock(Compute.RegionBackendServices)
    def backendServicesList = Mock(Compute.RegionBackendServices.List)
    def regionHealthChecks = Mock(Compute.RegionHealthChecks)
    def healthChecksList = Mock(Compute.RegionHealthChecks.List)
    def agent = createBatchExecutingAgent(compute)

    when:
    def loadBalancers = agent.constructLoadBalancers()

    then:
    1 * compute.regionBackendServices() >> regionBackendServices
    1 * regionBackendServices.list(PROJECT, REGION) >> backendServicesList
    1 * backendServicesList.execute() >> new BackendServiceList(items: [
      new BackendService(
        name: "backend-service",
        loadBalancingScheme: "EXTERNAL",
        sessionAffinity: "NONE",
        healthChecks: ["projects/${PROJECT}/regions/${REGION}/healthChecks/tcp-hc"]
      )
    ])
    1 * compute.regionHealthChecks() >> regionHealthChecks
    1 * regionHealthChecks.list(PROJECT, REGION) >> healthChecksList
    1 * healthChecksList.setPageToken(null) >> healthChecksList
    1 * healthChecksList.execute() >> new HealthCheckList(items: [
      new HealthCheck(
        name: "tcp-hc",
        checkIntervalSec: 10,
        timeoutSec: 5,
        unhealthyThreshold: 3,
        healthyThreshold: 2,
        tcpHealthCheck: new TCPHealthCheck(port: 8080)
      )
    ])
    1 * compute.forwardingRules() >> forwardingRules
    1 * forwardingRules.list(PROJECT, REGION) >> forwardingRulesList
    1 * forwardingRulesList.setPageToken(null) >> forwardingRulesList
    1 * forwardingRulesList.execute() >> new ForwardingRuleList(items: [
      new ForwardingRule(
        name: "lb-name",
        region: "projects/${PROJECT}/regions/${REGION}",
        loadBalancingScheme: "EXTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/backend-service",
        IPProtocol: "TCP"
      ),
      new ForwardingRule(
        name: "internal-lb",
        loadBalancingScheme: "INTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/backend-service",
        IPProtocol: "TCP"
      )
    ])

    loadBalancers.size() == 1
    loadBalancers[0].name == "lb-name"
    loadBalancers[0].backendService.name == "backend-service"
    loadBalancers[0].backendService.healthCheck.name == "tcp-hc"
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
    callback.healthCheckContext = [
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

  void "cache graph queues backend group health through batch request"() {
    given:
    def compute = Mock(Compute)
    def regionBackendServices = Mock(Compute.RegionBackendServices)
    def getHealth = Mock(Compute.RegionBackendServices.GetHealth)
    def groupHealthRequest = Mock(GoogleBatchRequest)
    def groupUrl = "projects/${PROJECT}/zones/${REGION}-a/instanceGroups/server-group"
    def callback = createForwardingRuleCallbacks(createAgent(compute))
    callback.loadBalancers = []
    callback.failedLoadBalancers = []
    callback.groupHealthRequest = groupHealthRequest
    callback.projectRegionBackendServices = [
        new BackendService(
          name: "backend-service",
          loadBalancingScheme: "EXTERNAL",
          sessionAffinity: "NONE",
          backends: [new Backend(group: groupUrl)]
        )
      ]
    callback.healthCheckContext = []

    when:
    callback.cacheRemainderOfLoadBalancerResourceGraph(
      new ForwardingRule(
        name: "lb-name",
        region: "projects/${PROJECT}/regions/${REGION}",
        loadBalancingScheme: "EXTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/backend-service",
        IPProtocol: "TCP"
      )
    )

    then:
    1 * compute.regionBackendServices() >> regionBackendServices
    1 * regionBackendServices.getHealth(PROJECT, REGION, "backend-service", {
      ResourceGroupReference reference -> reference.group == groupUrl
    }) >> getHealth
    1 * groupHealthRequest.queue(getHealth, _)
  }

  private GoogleRegionalExternalNetworkLoadBalancerCachingAgent createAgent(Compute compute = null) {
    compute = compute ?: Mock(Compute)
    new GoogleRegionalExternalNetworkLoadBalancerCachingAgent(
      "clouddriver",
      new GoogleNamedAccountCredentials.Builder()
        .name(ACCOUNT)
        .project(PROJECT)
        .compute(compute)
        .credentials(Mock(GoogleCredentials))
        .build(),
      new ObjectMapper(),
      new DefaultRegistry(),
      REGION
    )
  }

  private GoogleRegionalExternalNetworkLoadBalancerCachingAgent createBatchExecutingAgent(Compute compute) {
    new TestGoogleRegionalExternalNetworkLoadBalancerCachingAgent(
      "clouddriver",
      new GoogleNamedAccountCredentials.Builder()
        .name(ACCOUNT)
        .project(PROJECT)
        .compute(compute)
        .credentials(Mock(GoogleCredentials))
        .build(),
      new ObjectMapper(),
      new DefaultRegistry(),
      REGION
    )
  }

  private static Object createForwardingRuleCallbacks(GoogleRegionalExternalNetworkLoadBalancerCachingAgent agent) {
    def callbacksClass = Class.forName("${AbstractGoogleRegionalPassthroughLoadBalancerCachingAgent.name}\$ForwardingRuleCallbacks")
    def constructor = callbacksClass.getDeclaredConstructor(AbstractGoogleRegionalPassthroughLoadBalancerCachingAgent)
    constructor.accessible = true
    constructor.newInstance(agent)
  }

  static class TestGoogleRegionalExternalNetworkLoadBalancerCachingAgent
    extends GoogleRegionalExternalNetworkLoadBalancerCachingAgent {

    TestGoogleRegionalExternalNetworkLoadBalancerCachingAgent(
      String clouddriverUserAgentApplicationName,
      GoogleNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      DefaultRegistry registry,
      String region) {
      super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region)
    }

    @Override
    def <T> T timeExecute(AbstractGoogleClientRequest<T> request, String api, String... tags) {
      request.execute()
    }

    @Override
    GoogleBatchRequest buildGoogleBatchRequest() {
      new FakeGoogleBatchRequest()
    }

    @Override
    def executeIfRequestsAreQueued(GoogleBatchRequest googleBatchRequest, String instrumentationContext) {
      if (googleBatchRequest.size()) {
        ((FakeGoogleBatchRequest) googleBatchRequest).executeQueued()
      }
    }
  }

  static class FakeGoogleBatchRequest extends GoogleBatchRequest {
    List<QueuedRequest> queuedRequests = []

    FakeGoogleBatchRequest() {
      super(null, "clouddriver")
    }

    @Override
    void queue(ComputeRequest request, JsonBatchCallback callback) {
      queuedRequests << new QueuedRequest(request: request, callback: callback)
    }

    @Override
    Integer size() {
      queuedRequests.size()
    }

    void executeQueued() {
      queuedRequests.each { QueuedRequest queuedRequest ->
        queuedRequest.callback.onSuccess(queuedRequest.request.execute(), new HttpHeaders())
      }
    }
  }

  static class QueuedRequest {
    ComputeRequest request
    JsonBatchCallback callback
  }
}
