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
import com.google.api.services.compute.model.HealthCheck
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.Region
import com.google.api.services.compute.model.RegionList
import com.google.api.services.compute.model.TCPHealthCheck
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleRegionalExternalNetworkLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final String PROJECT = "my-project"
  private static final String REGION = "us-central1"
  private static final String LOAD_BALANCER = "regional-external-network-lb"
  private static final String BACKEND_SERVICE = "regional-external-network-lb"
  private static final String HEALTH_CHECK = "tcp-hc"
  private static final String HEALTH_CHECK_URL = "https://www.googleapis.com/compute/v1/projects/${PROJECT}/regions/${REGION}/healthChecks/${HEALTH_CHECK}"

  @Shared def registry = new DefaultRegistry()
  @Shared def threadSleeper = Mock(GoogleOperationPoller.ThreadSleeper)
  @Shared SafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = SafeRetry.withoutDelay()
  }

  void "omitted address fields do not force forwarding rule recreation when GCP has defaulted them"() {
    setup:
      def compute = Mock(Compute)
      def regions = Mock(Compute.Regions)
      def regionsList = Mock(Compute.Regions.List)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksGet = Mock(Compute.RegionHealthChecks.Get)
      def description = description(compute)
      @Subject def operation = operation(description)

    when:
      operation.operate([])

    then:
      1 * compute.regions() >> regions
      1 * regions.list(PROJECT) >> regionsList
      1 * regionsList.execute() >> new RegionList(items: [new Region(name: REGION)])

      1 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT, REGION, LOAD_BALANCER) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> new ForwardingRule(
        name: LOAD_BALANCER,
        region: "projects/${PROJECT}/regions/${REGION}",
        loadBalancingScheme: "EXTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/${BACKEND_SERVICE}",
        IPProtocol: "TCP",
        IPAddress: "35.1.2.3",
        networkTier: "PREMIUM",
        ports: ["80"]
      )

      1 * compute.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT, REGION, BACKEND_SERVICE) >> backendServicesGet
      1 * backendServicesGet.execute() >> new BackendService(
        name: BACKEND_SERVICE,
        loadBalancingScheme: "EXTERNAL",
        protocol: "TCP",
        sessionAffinity: "NONE",
        healthChecks: [HEALTH_CHECK_URL]
      )

      1 * compute.regionHealthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT, REGION, HEALTH_CHECK) >> healthChecksGet
      1 * healthChecksGet.execute() >> new HealthCheck(
        name: HEALTH_CHECK,
        checkIntervalSec: 5,
        timeoutSec: 5,
        healthyThreshold: 2,
        unhealthyThreshold: 2,
        tcpHealthCheck: new TCPHealthCheck(port: 80)
      )

      0 * forwardingRules.delete(_, _, _)
      0 * forwardingRules.insert(_, _, _)
      0 * backendServices.update(_, _, _, _)
      0 * healthChecks.update(_, _, _, _)
  }

  void "updates existing backend service when health check list is missing"() {
    setup:
      def compute = Mock(Compute)
      def regions = Mock(Compute.Regions)
      def regionsList = Mock(Compute.Regions.List)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesUpdate = Mock(Compute.RegionBackendServices.Update)
      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksGet = Mock(Compute.RegionHealthChecks.Get)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionOperationsGet = Mock(Compute.RegionOperations.Get)
      def description = description(compute)
      @Subject def operation = operation(description)

    when:
      operation.operate([])

    then:
      1 * compute.regions() >> regions
      1 * regions.list(PROJECT) >> regionsList
      1 * regionsList.execute() >> new RegionList(items: [new Region(name: REGION)])

      1 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT, REGION, LOAD_BALANCER) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> new ForwardingRule(
        name: LOAD_BALANCER,
        region: "projects/${PROJECT}/regions/${REGION}",
        loadBalancingScheme: "EXTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/${BACKEND_SERVICE}",
        IPProtocol: "TCP",
        IPAddress: "35.1.2.3",
        networkTier: "PREMIUM",
        ports: ["80"]
      )

      2 * compute.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT, REGION, BACKEND_SERVICE) >> backendServicesGet
      1 * backendServicesGet.execute() >> new BackendService(
        name: BACKEND_SERVICE,
        loadBalancingScheme: "EXTERNAL",
        protocol: "TCP",
        sessionAffinity: "NONE",
        healthChecks: null
      )
      1 * backendServices.update(PROJECT, REGION, BACKEND_SERVICE, { BackendService updated ->
        updated.healthChecks.collect { it.endsWith("/healthChecks/${HEALTH_CHECK}") } == [true]
      }) >> backendServicesUpdate
      1 * backendServicesUpdate.execute() >> new Operation(name: "update-backend-service", status: "DONE")

      1 * compute.regionHealthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT, REGION, HEALTH_CHECK) >> healthChecksGet
      1 * healthChecksGet.execute() >> new HealthCheck(
        name: HEALTH_CHECK,
        checkIntervalSec: 5,
        timeoutSec: 5,
        healthyThreshold: 2,
        unhealthyThreshold: 2,
        tcpHealthCheck: new TCPHealthCheck(port: 80)
      )

      1 * compute.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT, REGION, "update-backend-service") >> regionOperationsGet
      1 * regionOperationsGet.execute() >> new Operation(name: "update-backend-service", status: "DONE")

      0 * forwardingRules.delete(_, _, _)
      0 * forwardingRules.insert(_, _, _)
      0 * healthChecks.update(_, _, _, _)
  }

  void "ports-only update preserves existing forwarding rule address and network tier"() {
    setup:
      def compute = Mock(Compute)
      def regions = Mock(Compute.Regions)
      def regionsList = Mock(Compute.Regions.List)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksGet = Mock(Compute.RegionHealthChecks.Get)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionOperationsGet = Mock(Compute.RegionOperations.Get)
      def existingForwardingRule = new ForwardingRule(
        name: LOAD_BALANCER,
        region: "projects/${PROJECT}/regions/${REGION}",
        loadBalancingScheme: "EXTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/${BACKEND_SERVICE}",
        IPProtocol: "TCP",
        IPAddress: "35.1.2.3",
        networkTier: "PREMIUM",
        ports: ["80"]
      )
      def description = description(compute)
      description.ports = ["80", "443"]
      @Subject def operation = operation(description)

    when:
      operation.operate([])

    then:
      1 * compute.regions() >> regions
      1 * regions.list(PROJECT) >> regionsList
      1 * regionsList.execute() >> new RegionList(items: [new Region(name: REGION)])

      3 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT, REGION, LOAD_BALANCER) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> existingForwardingRule
      1 * forwardingRules.delete(PROJECT, REGION, LOAD_BALANCER) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> new Operation(name: "delete-forwarding-rule", status: "DONE")
      1 * forwardingRules.insert(PROJECT, REGION, { ForwardingRule replacement ->
        replacement.IPAddress == "35.1.2.3" &&
          replacement.networkTier == "PREMIUM" &&
          replacement.ports == ["80", "443"]
      }) >> forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> new Operation(name: "insert-forwarding-rule", status: "DONE")

      1 * compute.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT, REGION, BACKEND_SERVICE) >> backendServicesGet
      1 * backendServicesGet.execute() >> new BackendService(
        name: BACKEND_SERVICE,
        loadBalancingScheme: "EXTERNAL",
        protocol: "TCP",
        sessionAffinity: "NONE",
        healthChecks: [HEALTH_CHECK_URL]
      )

      1 * compute.regionHealthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT, REGION, HEALTH_CHECK) >> healthChecksGet
      1 * healthChecksGet.execute() >> new HealthCheck(
        name: HEALTH_CHECK,
        checkIntervalSec: 5,
        timeoutSec: 5,
        healthyThreshold: 2,
        unhealthyThreshold: 2,
        tcpHealthCheck: new TCPHealthCheck(port: 80)
      )

      2 * compute.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT, REGION, "delete-forwarding-rule") >> regionOperationsGet
      1 * regionOperations.get(PROJECT, REGION, "insert-forwarding-rule") >> regionOperationsGet
      2 * regionOperationsGet.execute() >>> [
        new Operation(name: "delete-forwarding-rule", status: "DONE"),
        new Operation(name: "insert-forwarding-rule", status: "DONE")
      ]
  }

  void "deletes listenersToDelete without recreating unchanged primary forwarding rule"() {
    setup:
      def compute = Mock(Compute)
      def regions = Mock(Compute.Regions)
      def regionsList = Mock(Compute.Regions.List)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksGet = Mock(Compute.RegionHealthChecks.Get)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionOperationsGet = Mock(Compute.RegionOperations.Get)
      def description = description(compute)
      description.listenersToDelete = ["old-listener"]
      @Subject def operation = operation(description)

    when:
      operation.operate([])

    then:
      1 * compute.regions() >> regions
      1 * regions.list(PROJECT) >> regionsList
      1 * regionsList.execute() >> new RegionList(items: [new Region(name: REGION)])

      2 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT, REGION, LOAD_BALANCER) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> new ForwardingRule(
        name: LOAD_BALANCER,
        region: "projects/${PROJECT}/regions/${REGION}",
        loadBalancingScheme: "EXTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/${BACKEND_SERVICE}",
        IPProtocol: "TCP",
        IPAddress: "35.1.2.3",
        networkTier: "PREMIUM",
        ports: ["80"]
      )
      1 * forwardingRules.delete(PROJECT, REGION, "old-listener") >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> new Operation(name: "delete-old-listener", status: "DONE")
      0 * forwardingRules.insert(_, _, _)

      1 * compute.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT, REGION, BACKEND_SERVICE) >> backendServicesGet
      1 * backendServicesGet.execute() >> new BackendService(
        name: BACKEND_SERVICE,
        loadBalancingScheme: "EXTERNAL",
        protocol: "TCP",
        sessionAffinity: "NONE",
        healthChecks: [HEALTH_CHECK_URL]
      )

      1 * compute.regionHealthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT, REGION, HEALTH_CHECK) >> healthChecksGet
      1 * healthChecksGet.execute() >> new HealthCheck(
        name: HEALTH_CHECK,
        checkIntervalSec: 5,
        timeoutSec: 5,
        healthyThreshold: 2,
        unhealthyThreshold: 2,
        tcpHealthCheck: new TCPHealthCheck(port: 80)
      )

      1 * compute.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT, REGION, "delete-old-listener") >> regionOperationsGet
      1 * regionOperationsGet.execute() >> new Operation(name: "delete-old-listener", status: "DONE")
  }

  void "throws when an existing forwarding rule with the same name is not regional external network passthrough"() {
    setup:
      def compute = Mock(Compute)
      def regions = Mock(Compute.Regions)
      def regionsList = Mock(Compute.Regions.List)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def description = description(compute)
      @Subject def operation = operation(description)

    when:
      operation.operate([])

    then:
      1 * compute.regions() >> regions
      1 * regions.list(PROJECT) >> regionsList
      1 * regionsList.execute() >> new RegionList(items: [new Region(name: REGION)])
      1 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT, REGION, LOAD_BALANCER) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> new ForwardingRule(
        name: LOAD_BALANCER,
        region: "projects/${PROJECT}/regions/${REGION}",
        loadBalancingScheme: "INTERNAL",
        backendService: "projects/${PROJECT}/regions/${REGION}/backendServices/${BACKEND_SERVICE}",
        IPProtocol: "TCP"
      )
      thrown(GoogleOperationException)
  }

  private UpsertGoogleLoadBalancerDescription description(Compute compute) {
    new UpsertGoogleLoadBalancerDescription(
      accountName: "auto",
      credentials: new GoogleNamedAccountCredentials.Builder().project(PROJECT).compute(compute).build(),
      loadBalancerName: LOAD_BALANCER,
      loadBalancerType: GoogleLoadBalancerType.REGIONAL_EXTERNAL_NETWORK,
      region: REGION,
      ipProtocol: "TCP",
      ports: ["80"],
      backendService: new GoogleBackendService(
        name: BACKEND_SERVICE,
        healthCheck: new GoogleHealthCheck(
          name: HEALTH_CHECK,
          healthCheckType: GoogleHealthCheck.HealthCheckType.TCP,
          port: 80,
          checkIntervalSec: 5,
          timeoutSec: 5,
          healthyThreshold: 2,
          unhealthyThreshold: 2
        )
      )
    )
  }

  private UpsertGoogleRegionalExternalNetworkLoadBalancerAtomicOperation operation(UpsertGoogleLoadBalancerDescription description) {
    def operation = new UpsertGoogleRegionalExternalNetworkLoadBalancerAtomicOperation(description)
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
