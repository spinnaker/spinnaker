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
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Unroll

class GoogleNetworkLoadBalancerCachingAgentSpec extends Specification {
  private static final String ACCOUNT = "auto"
  private static final String PROJECT = "my-project"
  private static final String REGION = "us-central1"

  @Unroll
  void "on-demand refresh leaves regional #proxyType proxy load balancers to their own agent"() {
    given:
    def compute = Mock(Compute)
    def forwardingRules = Mock(Compute.ForwardingRules)
    def forwardingRuleGet = Mock(Compute.ForwardingRules.Get)
    def providerCache = Mock(ProviderCache)
    def agent = createBatchExecutingAgent(compute)

    when:
    def result = agent.handle(providerCache, [loadBalancerName: "listener-a", account: ACCOUNT, region: REGION])

    then:
    1 * compute.forwardingRules() >> forwardingRules
    1 * forwardingRules.get(PROJECT, REGION, "listener-a") >> forwardingRuleGet
    1 * forwardingRuleGet.execute() >> new ForwardingRule(
      name: "listener-a",
      loadBalancingScheme: "EXTERNAL_MANAGED",
      target: "projects/${PROJECT}/regions/${REGION}/${proxyType}/listener-a-proxy"
    )
    0 * compute.targetPools()
    // Any provider cache access here would evict the regional HTTP agent's rows.
    0 * providerCache._
    result == null

    where:
    proxyType << ["targetHttpProxies", "targetHttpsProxies"]
  }

  void "caching cycle skips regional HTTP proxy rules and keeps other targeted rules"() {
    given:
    def compute = Mock(Compute)
    def forwardingRules = Mock(Compute.ForwardingRules)
    def forwardingRulesList = Mock(Compute.ForwardingRules.List)
    def agent = createBatchExecutingAgent(compute)

    when:
    def loadBalancers = agent.constructLoadBalancers()

    then:
    1 * compute.forwardingRules() >> forwardingRules
    1 * forwardingRules.list(PROJECT, REGION) >> forwardingRulesList
    1 * forwardingRulesList.setPageToken(null) >> forwardingRulesList
    1 * forwardingRulesList.execute() >> new ForwardingRuleList(items: [
      new ForwardingRule(
        name: "listener-a",
        loadBalancingScheme: "EXTERNAL_MANAGED",
        target: "projects/${PROJECT}/regions/${REGION}/targetHttpProxies/listener-a-proxy"
      ),
      new ForwardingRule(
        name: "vpn-rule",
        loadBalancingScheme: "EXTERNAL",
        target: "projects/${PROJECT}/regions/${REGION}/targetVpnGateways/vpn-gateway"
      ),
    ])
    0 * compute.targetPools()
    loadBalancers*.name == ["vpn-rule"]
  }

  private GoogleNetworkLoadBalancerCachingAgent createBatchExecutingAgent(Compute compute) {
    new TestGoogleNetworkLoadBalancerCachingAgent(
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

  static class TestGoogleNetworkLoadBalancerCachingAgent extends GoogleNetworkLoadBalancerCachingAgent {

    TestGoogleNetworkLoadBalancerCachingAgent(
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
