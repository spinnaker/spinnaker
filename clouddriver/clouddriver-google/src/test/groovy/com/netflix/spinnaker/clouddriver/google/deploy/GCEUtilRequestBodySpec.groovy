/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.Metadata
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleExternalHttpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleRegionalExternalNetworkLoadBalancer
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.google.test.CapturingComputeTransport
import spock.lang.Specification

class GCEUtilRequestBodySpec extends Specification {
  private static final String PROJECT = "test-project"
  private static final String REGION = "us-central1"
  private static final String SERVER_GROUP = "server-group-v001"
  private static final String PHASE = "TEST-PHASE"

  private final ObjectMapper objectMapper = new ObjectMapper()
  private final TestExecutor executor = new TestExecutor()

  void "regional external network attachment serializes a connection backend without capacity fields"() {
    given:
      def transport = new CapturingComputeTransport().registerGetResponse(
        "/backendServices/network-backend",
        '{"name":"network-backend","loadBalancingScheme":"EXTERNAL","backends":[]}')
      def compute = compute(transport)
      def provider = Mock(GoogleLoadBalancerProvider)
      def poller = Mock(GoogleOperationPoller)
      def task = Mock(Task)
      def loadBalancer = new GoogleRegionalExternalNetworkLoadBalancer(
        name: "network-lb",
        backendService: new GoogleBackendService(name: "network-backend"))
      def serverGroup = serverGroupView("network-lb")

    when:
      GCEUtil.addRegionalExternalNetworkLoadBalancerBackends(
        compute,
        PROJECT,
        serverGroup,
        provider,
        task,
        PHASE,
        poller,
        executor)

    then:
      1 * provider.getApplicationLoadBalancers("") >> ([loadBalancer.view] as Set)
      1 * poller.waitForRegionalOperation(
        compute, PROJECT, REGION, _, null, task, "compute.${REGION}.backendServices.update", PHASE)

      def request = transport.findPutTo("/backendServices/network-backend").orElseThrow()
      def backend = transport.parseBody(request).path("backends").get(0)
      backend.path("balancingMode").asText() == "CONNECTION"
      backend.path("group").asText() == GCEUtil.buildRegionalServerGroupUrl(PROJECT, REGION, SERVER_GROUP)
      !backend.has("capacityScaler")
      !backend.has("maxConnectionsPerInstance")
      !backend.has("maxRatePerInstance")
      !backend.has("maxUtilization")
  }

  void "external managed attachment serializes policy-driven utilization fields"() {
    given:
      def transport = new CapturingComputeTransport().registerGetResponse(
        "/backendServices/external-backend",
        '{"name":"external-backend","loadBalancingScheme":"EXTERNAL_MANAGED","backends":[]}')
      def compute = compute(transport)
      def provider = Mock(GoogleLoadBalancerProvider)
      def poller = Mock(GoogleOperationPoller)
      def task = Mock(Task)
      def loadBalancer = new GoogleExternalHttpLoadBalancer(
        name: "external-lb",
        defaultService: new GoogleBackendService(name: "external-backend"))
      def policy = new GoogleHttpLoadBalancingPolicy(
        balancingMode: GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION,
        capacityScaler: 1.0f,
        maxUtilization: 0.8f)
      def serverGroup = serverGroupView(
        "external-lb",
        [(GCEUtil.LOAD_BALANCING_POLICY): objectMapper.writeValueAsString(policy)])

    when:
      GCEUtil.addExternalHttpLoadBalancerBackends(
        compute,
        objectMapper,
        PROJECT,
        serverGroup,
        provider,
        task,
        PHASE,
        poller,
        executor)

    then:
      1 * provider.getApplicationLoadBalancers("") >> ([loadBalancer.view] as Set)
      1 * poller.waitForRegionalOperation(
        compute, PROJECT, REGION, _, null, task, "compute.regionBackendService.update", PHASE)

      def request = transport.findPutTo("/backendServices/external-backend").orElseThrow()
      def backend = transport.parseBody(request).path("backends").get(0)
      backend.path("balancingMode").asText() == "UTILIZATION"
      backend.path("capacityScaler").asDouble() == 1.0d
      backend.path("maxUtilization").asDouble() == 0.8d
      backend.path("group").asText() == GCEUtil.buildRegionalServerGroupUrl(PROJECT, REGION, SERVER_GROUP)
      !backend.has("maxConnectionsPerInstance")
      !backend.has("maxRatePerInstance")
  }

  private static Compute compute(CapturingComputeTransport transport) {
    new Compute.Builder(transport, GsonFactory.getDefaultInstance(), null)
      .setApplicationName("test")
      .build()
  }

  private static GoogleServerGroup.View serverGroupView(
    String loadBalancerName,
    Map<String, String> additionalMetadata = [:]) {
    def metadata = [(GCEUtil.REGIONAL_LOAD_BALANCER_NAMES): loadBalancerName] + additionalMetadata
    new GoogleServerGroup(
      name: SERVER_GROUP,
      region: REGION,
      regional: true,
      launchConfig: [
        instanceTemplate: new InstanceTemplate(
          properties: new InstanceProperties(
            metadata: new Metadata(
              items: metadata.collect { key, value -> new Metadata.Items(key: key, value: value) })))
      ],
      asg: [(GCEUtil.REGIONAL_LOAD_BALANCER_NAMES): [loadBalancerName]]
    ).view
  }

  private static class TestExecutor implements GoogleExecutorTraits {
    Registry registry = new DefaultRegistry()
  }
}
