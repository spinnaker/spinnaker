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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.google.api.services.compute.model.Operation
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DeleteGoogleRegionalExternalNetworkLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final String PROJECT = "my-project"
  private static final String REGION = "us-central1"
  private static final String LOAD_BALANCER = "regional-external-network-lb"
  private static final String OTHER_LISTENER = "regional-external-network-lb-udp"
  private static final String BACKEND_SERVICE = "regional-external-network-lb"
  private static final String BACKEND_SERVICE_URL = "projects/${PROJECT}/regions/${REGION}/backendServices/${BACKEND_SERVICE}"
  private static final String HEALTH_CHECK_URL = "projects/${PROJECT}/regions/${REGION}/healthChecks/tcp-hc"

  @Shared def registry = new DefaultRegistry()
  @Shared def threadSleeper = Mock(GoogleOperationPoller.ThreadSleeper)
  @Shared SafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = SafeRetry.withoutDelay()
  }

  void "deletes all regional external passthrough listeners sharing the backend service and skips health checks when not requested"() {
    setup:
      def compute = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionOperationsGet = Mock(Compute.RegionOperations.Get)
      def deleteForwardingRuleOp = new Operation(name: "delete-forwarding-rule", status: "DONE")
      def deleteBackendServiceOp = new Operation(name: "delete-backend-service", status: "DONE")
      def description = description(compute, false)
      @Subject def operation = operation(description)

    when:
      operation.operate([])

    then:
      3 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> new ForwardingRuleList(items: [
        forwardingRule(LOAD_BALANCER, "TCP", BACKEND_SERVICE_URL, "EXTERNAL"),
        forwardingRule(OTHER_LISTENER, "UDP", BACKEND_SERVICE_URL, "EXTERNAL"),
        forwardingRule("internal-listener", "TCP", BACKEND_SERVICE_URL, "INTERNAL")
      ])
      1 * forwardingRules.delete(PROJECT, REGION, LOAD_BALANCER) >> forwardingRulesDelete
      1 * forwardingRules.delete(PROJECT, REGION, OTHER_LISTENER) >> forwardingRulesDelete
      2 * forwardingRulesDelete.execute() >> deleteForwardingRuleOp

      2 * compute.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT, REGION, BACKEND_SERVICE) >> backendServicesGet
      1 * backendServicesGet.execute() >> new BackendService(
        name: BACKEND_SERVICE,
        loadBalancingScheme: "EXTERNAL",
        healthChecks: [HEALTH_CHECK_URL]
      )
      1 * backendServices.delete(PROJECT, REGION, BACKEND_SERVICE) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> deleteBackendServiceOp

      3 * compute.regionOperations() >> regionOperations
      3 * regionOperations.get(PROJECT, REGION, _) >> regionOperationsGet
      3 * regionOperationsGet.execute() >>> [deleteForwardingRuleOp, deleteForwardingRuleOp, deleteBackendServiceOp]

      0 * compute.regionHealthChecks()
  }

  void "deletes listeners and backend service without reading missing health checks when not requested"() {
    setup:
      def compute = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionOperationsGet = Mock(Compute.RegionOperations.Get)
      def deleteForwardingRuleOp = new Operation(name: "delete-forwarding-rule", status: "DONE")
      def deleteBackendServiceOp = new Operation(name: "delete-backend-service", status: "DONE")
      def description = description(compute, false)
      @Subject def operation = operation(description)

    when:
      operation.operate([])

    then:
      2 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> new ForwardingRuleList(items: [
        forwardingRule(LOAD_BALANCER, "TCP", BACKEND_SERVICE_URL, "EXTERNAL")
      ])
      1 * forwardingRules.delete(PROJECT, REGION, LOAD_BALANCER) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> deleteForwardingRuleOp

      2 * compute.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT, REGION, BACKEND_SERVICE) >> backendServicesGet
      1 * backendServicesGet.execute() >> new BackendService(
        name: BACKEND_SERVICE,
        loadBalancingScheme: "EXTERNAL",
        healthChecks: null
      )
      1 * backendServices.delete(PROJECT, REGION, BACKEND_SERVICE) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> deleteBackendServiceOp

      2 * compute.regionOperations() >> regionOperations
      2 * regionOperations.get(PROJECT, REGION, _) >> regionOperationsGet
      2 * regionOperationsGet.execute() >>> [deleteForwardingRuleOp, deleteBackendServiceOp]

      0 * compute.regionHealthChecks()
  }

  void "deletes regional health check when requested"() {
    setup:
      def compute = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def regionHealthChecks = Mock(Compute.RegionHealthChecks)
      def regionHealthChecksDelete = Mock(Compute.RegionHealthChecks.Delete)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionOperationsGet = Mock(Compute.RegionOperations.Get)
      def deleteForwardingRuleOp = new Operation(name: "delete-forwarding-rule", status: "DONE")
      def deleteBackendServiceOp = new Operation(name: "delete-backend-service", status: "DONE")
      def deleteHealthCheckOp = new Operation(name: "delete-health-check", status: "DONE")
      def description = description(compute, true)
      @Subject def operation = operation(description)

    when:
      operation.operate([])

    then:
      2 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> new ForwardingRuleList(items: [
        forwardingRule(LOAD_BALANCER, "TCP", BACKEND_SERVICE_URL, "EXTERNAL")
      ])
      1 * forwardingRules.delete(PROJECT, REGION, LOAD_BALANCER) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> deleteForwardingRuleOp

      2 * compute.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT, REGION, BACKEND_SERVICE) >> backendServicesGet
      1 * backendServicesGet.execute() >> new BackendService(
        name: BACKEND_SERVICE,
        loadBalancingScheme: "EXTERNAL",
        healthChecks: [HEALTH_CHECK_URL]
      )
      1 * backendServices.delete(PROJECT, REGION, BACKEND_SERVICE) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> deleteBackendServiceOp

      1 * compute.regionHealthChecks() >> regionHealthChecks
      1 * regionHealthChecks.delete(PROJECT, REGION, "tcp-hc") >> regionHealthChecksDelete
      1 * regionHealthChecksDelete.execute() >> deleteHealthCheckOp

      3 * compute.regionOperations() >> regionOperations
      3 * regionOperations.get(PROJECT, REGION, _) >> regionOperationsGet
      3 * regionOperationsGet.execute() >>> [deleteForwardingRuleOp, deleteBackendServiceOp, deleteHealthCheckOp]
  }

  @Unroll
  void "does not delete wrong-shape forwarding rule: #reason"() {
    setup:
      def compute = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def description = description(compute, true)
      @Subject def operation = operation(description)

    when:
      operation.operate([])

    then:
      1 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> new ForwardingRuleList(items: [forwardingRule])
      thrown GoogleResourceNotFoundException
      0 * compute.regionBackendServices()
      0 * compute.regionHealthChecks()

    where:
      reason                 | forwardingRule
      "internal scheme"      | forwardingRule(LOAD_BALANCER, "TCP", BACKEND_SERVICE_URL, "INTERNAL")
      "target proxy"         | forwardingRule(LOAD_BALANCER, "TCP", BACKEND_SERVICE_URL, "EXTERNAL", "targetHttpProxies/proxy")
      "unsupported protocol" | forwardingRule(LOAD_BALANCER, "ESP", BACKEND_SERVICE_URL, "EXTERNAL")
  }

  private static ForwardingRule forwardingRule(
    String name, String protocol, String backendService, String scheme, String target = null) {
    new ForwardingRule(
      name: name,
      loadBalancingScheme: scheme,
      backendService: backendService,
      IPProtocol: protocol,
      target: target
    )
  }

  private DeleteGoogleLoadBalancerDescription description(Compute compute, boolean deleteHealthChecks) {
    new DeleteGoogleLoadBalancerDescription(
      accountName: "auto",
      credentials: new GoogleNamedAccountCredentials.Builder().project(PROJECT).compute(compute).build(),
      loadBalancerName: LOAD_BALANCER,
      loadBalancerType: GoogleLoadBalancerType.REGIONAL_EXTERNAL_NETWORK,
      region: REGION,
      deleteHealthChecks: deleteHealthChecks
    )
  }

  private DeleteGoogleRegionalExternalNetworkLoadBalancerAtomicOperation operation(DeleteGoogleLoadBalancerDescription description) {
    def operation = new DeleteGoogleRegionalExternalNetworkLoadBalancerAtomicOperation(description)
    operation.googleOperationPoller = new GoogleOperationPoller(
      googleConfigurationProperties: new GoogleConfigurationProperties(),
      threadSleeper: threadSleeper,
      registry: registry,
      safeRetry: safeRetry
    )
    operation.registry = registry
    operation.safeRetry = safeRetry
    operation
  }
}
