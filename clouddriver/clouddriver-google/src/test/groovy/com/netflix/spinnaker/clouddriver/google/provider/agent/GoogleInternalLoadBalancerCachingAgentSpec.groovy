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
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.Backend
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.BackendServiceGroupHealth
import com.google.api.services.compute.model.BackendServiceList
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.google.api.services.compute.model.HealthCheck
import com.google.api.services.compute.model.HealthCheckList
import com.google.api.services.compute.model.HealthStatus
import com.google.api.services.compute.model.HttpHealthCheckList
import com.google.api.services.compute.model.HttpsHealthCheckList
import com.google.api.services.compute.model.TCPHealthCheck
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification

class GoogleInternalLoadBalancerCachingAgentSpec extends Specification {
  private static final String ACCOUNT = "auto"
  private static final String PROJECT = "my-project"
  private static final String REGION = "us-central1"

  void "internal passthrough agent does not report pending on-demand requests"() {
    expect:
    createBatchExecutingAgent(Mock(Compute)).pendingOnDemandRequests(null) == []
  }

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

  void "constructLoadBalancers lists internal passthrough load balancers and applies group health"() {
    given:
    def compute = Mock(Compute)
    def regionBackendServices = Mock(Compute.RegionBackendServices)
    def backendServicesList = Mock(Compute.RegionBackendServices.List)
    def getHealth = Mock(Compute.RegionBackendServices.GetHealth)
    def httpHealthChecks = Mock(Compute.HttpHealthChecks)
    def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
    def httpsHealthChecks = Mock(Compute.HttpsHealthChecks)
    def httpsHealthChecksList = Mock(Compute.HttpsHealthChecks.List)
    def healthChecks = Mock(Compute.HealthChecks)
    def healthChecksList = Mock(Compute.HealthChecks.List)
    def forwardingRules = Mock(Compute.ForwardingRules)
    def forwardingRulesList = Mock(Compute.ForwardingRules.List)
    def groupUrl = "projects/${PROJECT}/zones/${REGION}-a/instanceGroups/server-group"
    def agent = createBatchExecutingAgent(compute)

    when:
    def loadBalancers = agent.constructLoadBalancers()

    then:
    (1.._) * compute.regionBackendServices() >> regionBackendServices
    1 * regionBackendServices.list(PROJECT, REGION) >> backendServicesList
    1 * backendServicesList.execute() >> new BackendServiceList(items: [
      new BackendService(
        name: "backend-service",
        loadBalancingScheme: "INTERNAL",
        sessionAffinity: "NONE",
        backends: [new Backend(group: groupUrl, balancingMode: "UTILIZATION")],
        healthChecks: ["projects/${PROJECT}/global/healthChecks/tcp-hc"]
      )
    ])
    1 * compute.httpHealthChecks() >> httpHealthChecks
    1 * httpHealthChecks.list(PROJECT) >> httpHealthChecksList
    1 * httpHealthChecksList.setPageToken(null) >> httpHealthChecksList
    1 * httpHealthChecksList.execute() >> new HttpHealthCheckList(items: [])
    1 * compute.httpsHealthChecks() >> httpsHealthChecks
    1 * httpsHealthChecks.list(PROJECT) >> httpsHealthChecksList
    1 * httpsHealthChecksList.setPageToken(null) >> httpsHealthChecksList
    1 * httpsHealthChecksList.execute() >> new HttpsHealthCheckList(items: [])
    1 * compute.healthChecks() >> healthChecks
    1 * healthChecks.list(PROJECT) >> healthChecksList
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
        loadBalancingScheme: "INTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/backend-service",
        IPProtocol: "TCP",
        subnetwork: "projects/${PROJECT}/regions/${REGION}/subnetworks/default"
      ),
      new ForwardingRule(
        name: "external-lb",
        loadBalancingScheme: "EXTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/backend-service",
        IPProtocol: "TCP"
      )
    ])
    1 * regionBackendServices.getHealth(PROJECT, REGION, "backend-service", {
      it.group == groupUrl
    }) >> getHealth
    1 * getHealth.execute() >> new BackendServiceGroupHealth(healthStatus: [
      new HealthStatus(
        instance: "projects/${PROJECT}/zones/${REGION}-a/instances/server-group-v000",
        healthState: "HEALTHY"
      )
    ])

    loadBalancers.size() == 1
    loadBalancers[0].name == "lb-name"
    loadBalancers[0].backendService.name == "backend-service"
    loadBalancers[0].backendService.healthCheck.name == "tcp-hc"
    loadBalancers[0].backendService.healthCheck.healthCheckType == GoogleHealthCheck.HealthCheckType.TCP
    loadBalancers[0].healths.size() == 1
    loadBalancers[0].healths[0].instanceName == "server-group-v000"
    loadBalancers[0].healths[0].status.name() == "HEALTHY"
  }

  private GoogleInternalLoadBalancerCachingAgent createBatchExecutingAgent(Compute compute) {
    new TestGoogleInternalLoadBalancerCachingAgent(
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

  static class TestGoogleInternalLoadBalancerCachingAgent
    extends GoogleInternalLoadBalancerCachingAgent {

    TestGoogleInternalLoadBalancerCachingAgent(
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
