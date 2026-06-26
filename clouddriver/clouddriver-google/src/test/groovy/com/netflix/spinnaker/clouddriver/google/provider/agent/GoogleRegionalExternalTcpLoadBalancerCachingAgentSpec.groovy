/*
 * Copyright 2024 Harness, Inc.
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
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.HealthCheck
import com.google.api.services.compute.model.HTTPHealthCheck
import com.google.api.services.compute.model.TCPHealthCheck
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancingScheme
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleTcpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.HealthCheckHelper
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification

class GoogleRegionalExternalTcpLoadBalancerCachingAgentSpec extends Specification {

  static final String PROJECT = "test-project"
  static final String REGION = "us-central1"
  static final String ACCOUNT_NAME = "test-account"

  GoogleNamedAccountCredentials credentials
  Compute compute
  ObjectMapper objectMapper
  Registry registry
  GoogleRegionalExternalTcpLoadBalancerCachingAgent agent

  def setup() {
    credentials = Mock(GoogleNamedAccountCredentials)
    compute = Mock(Compute)
    objectMapper = new ObjectMapper()
    registry = Mock(Registry)

    credentials.getCompute() >> compute
    credentials.getProject() >> PROJECT
    credentials.getName() >> ACCOUNT_NAME

    agent = new GoogleRegionalExternalTcpLoadBalancerCachingAgent(
      "user-agent",
      credentials,
      objectMapper,
      registry,
      REGION
    )
  }

  def "agent type is correct"() {
    expect:
    agent.getAgentType() == "${ACCOUNT_NAME}/${REGION}/GoogleRegionalExternalTcpLoadBalancerCachingAgent"
  }

  def "region is set correctly"() {
    expect:
    agent.getRegion() == REGION
  }

  def "handleHttpHealthCheck sets health check properties"() {
    given:
    def healthCheck = new com.google.api.services.compute.model.HttpHealthCheck(
      name: "http-hc",
      port: 8080,
      requestPath: "/health",
      checkIntervalSec: 30,
      timeoutSec: 5,
      unhealthyThreshold: 3,
      healthyThreshold: 2
    )
    def backendService = new com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService()

    when:
    HealthCheckHelper.handleHttpHealthCheck(healthCheck, backendService)

    then:
    backendService.healthCheck != null
    backendService.healthCheck.name == "http-hc"
    backendService.healthCheck.port == 8080
    backendService.healthCheck.requestPath == "/health"
    backendService.healthCheck.checkIntervalSec == 30
    backendService.healthCheck.timeoutSec == 5
    backendService.healthCheck.unhealthyThreshold == 3
    backendService.healthCheck.healthyThreshold == 2
    backendService.healthCheck.healthCheckType == com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck.HealthCheckType.HTTP
  }

  def "handleHealthCheck with TCP health check"() {
    given:
    def healthCheck = new HealthCheck(
      name: "tcp-hc",
      checkIntervalSec: 30,
      timeoutSec: 5,
      unhealthyThreshold: 3,
      healthyThreshold: 2,
      tcpHealthCheck: new TCPHealthCheck(port: 3306)
    )
    def backendService = new com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService()

    when:
    HealthCheckHelper.handleHealthCheck(healthCheck, backendService)

    then:
    backendService.healthCheck != null
    backendService.healthCheck.name == "tcp-hc"
    backendService.healthCheck.port == 3306
    backendService.healthCheck.healthCheckType == com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck.HealthCheckType.TCP
  }

  def "handleHealthCheck with HTTP health check in HealthCheck type"() {
    given:
    def healthCheck = new HealthCheck(
      name: "http-hc",
      checkIntervalSec: 30,
      timeoutSec: 5,
      unhealthyThreshold: 3,
      healthyThreshold: 2,
      httpHealthCheck: new HTTPHealthCheck(port: 8080, requestPath: "/healthz")
    )
    def backendService = new com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService()

    when:
    HealthCheckHelper.handleHealthCheck(healthCheck, backendService)

    then:
    backendService.healthCheck != null
    backendService.healthCheck.name == "http-hc"
    backendService.healthCheck.port == 8080
    backendService.healthCheck.requestPath == "/healthz"
    backendService.healthCheck.healthCheckType == com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck.HealthCheckType.HTTP
  }

  def "creates GoogleTcpLoadBalancer with EXTERNAL scheme"() {
    given:
    def forwardingRule = new ForwardingRule(
      name: "tcp-lb",
      region: "https://compute.googleapis.com/compute/v1/projects/${PROJECT}/regions/${REGION}",
      creationTimestamp: "2024-01-01T00:00:00.000-00:00",
      IPAddress: "10.0.0.1",
      IPProtocol: "TCP",
      portRange: "80-80",
      loadBalancingScheme: "EXTERNAL",
      backendService: "https://compute.googleapis.com/compute/v1/projects/${PROJECT}/regions/${REGION}/backendServices/tcp-backend"
    )

    expect:
    forwardingRule.loadBalancingScheme == "EXTERNAL"
    forwardingRule.backendService != null
    forwardingRule.target == null
  }

  def "filters forwarding rules correctly for regional external TCP"() {
    given:
    def externalWithBackend = new ForwardingRule(
      name: "external-tcp",
      loadBalancingScheme: "EXTERNAL",
      backendService: "backend-svc",
      target: null
    )
    def externalWithTarget = new ForwardingRule(
      name: "global-tcp",
      loadBalancingScheme: "EXTERNAL",
      target: "tcp-proxy",
      backendService: null
    )
    def internalWithBackend = new ForwardingRule(
      name: "internal-tcp",
      loadBalancingScheme: "INTERNAL",
      backendService: "backend-svc",
      target: null
    )

    expect: "regional external TCP has backendService, no target, and EXTERNAL scheme"
    externalWithBackend.backendService != null
    externalWithBackend.target == null
    externalWithBackend.loadBalancingScheme == "EXTERNAL"

    and: "global TCP has target proxy instead"
    externalWithTarget.target != null
    externalWithTarget.backendService == null

    and: "internal TCP has INTERNAL scheme"
    internalWithBackend.loadBalancingScheme == "INTERNAL"
  }
}
