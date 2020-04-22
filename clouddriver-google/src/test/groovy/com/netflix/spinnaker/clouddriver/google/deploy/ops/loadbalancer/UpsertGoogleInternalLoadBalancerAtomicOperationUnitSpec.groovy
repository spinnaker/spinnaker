/*
 * Copyright 2016 Google, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.converters.UpsertGoogleLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.GoogleNetwork
import com.netflix.spinnaker.clouddriver.google.model.GoogleSubnet
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.UpsertGoogleHttpLoadBalancerTestConstants.*

class UpsertGoogleInternalLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final PROJECT_NAME = "my-project"
  private static final HEALTH_CHECK_OP_NAME = "health-check-op"
  private static final BACKEND_SERVICE_OP_NAME = "backend-service-op"
  private static final DONE = "DONE"
  private static final REGION = "us-central1"

  @Shared GoogleHealthCheck hc
  @Shared def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
  @Shared def registry = new DefaultRegistry()
  @Shared SafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    hc = [
      "name"              : "basic-check",
      "healthCheckType"   : "HTTP",
      "requestPath"       : "/",
      "port"              : 80,
      "checkIntervalSec"  : 1,
      "timeoutSec"        : 1,
      "healthyThreshold"  : 1,
      "unhealthyThreshold": 1
    ]
    safeRetry = SafeRetry.withoutDelay()
  }

  void "should create Internal load balancer if no infrastructure present."() {
    def computeMock = Mock(Compute)

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()

    credentialsRepo.save(ACCOUNT_NAME, credentials)
    def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
      accountCredentialsProvider: credentialsProvider,
      objectMapper: new ObjectMapper()
    )

    def globalOperations = Mock(Compute.GlobalOperations)
    def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)

    def regionalOperations = Mock(Compute.RegionOperations)
    def regionalBackendServiceOperationGet = Mock(Compute.RegionOperations.Get)
    def regionalForwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)

    def regions = Mock(Compute.Regions)
    def regionsList = Mock(Compute.Regions.List)
    def regionList = new RegionList(items: [new Region(name: REGION)])

    def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
    def networkKeyPattern = "gce:networks:some-network:$ACCOUNT_NAME:global"
    def googleNetwork = new GoogleNetwork(selfLink: "projects/$PROJECT_NAME/global/networks/some-network")

    def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
    def subnetKeyPattern = "gce:subnets:some-subnet:$ACCOUNT_NAME:$REGION"
    def googleSubnet = new GoogleSubnet(selfLink: "projects/$PROJECT_NAME/regions/$REGION/subnetworks/some-subnet")

    def forwardingRules = Mock(Compute.ForwardingRules)
    def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
    def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
    def forwardingRuleInsertOp = new Operation(
        targetLink: "forwarding-rule",
        name: LOAD_BALANCER_NAME,
        status: DONE)

    def healthChecks = Mock(Compute.HealthChecks)
    def healthChecksGet = Mock(Compute.HealthChecks.Get)
    def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
    def healthChecksInsertOp = new Operation(
      targetLink: "health-check",
      name: HEALTH_CHECK_OP_NAME,
      status: DONE
    )

    def backendServices = Mock(Compute.RegionBackendServices)
    def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
    def backendServicesInsert = Mock(Compute.RegionBackendServices.Insert)
    def backendServicesInsertOp = new Operation(
      targetLink: "backend-service",
      name: BACKEND_SERVICE_OP_NAME,
      status: DONE
    )

    def input = [
      accountName       : ACCOUNT_NAME,
      loadBalancerName  : LOAD_BALANCER_NAME,
      portRange         : PORT_RANGE,
      region            : REGION,
      backendService    : [
        name            : DEFAULT_SERVICE,
        backends        : [],
        healthCheck     : hc,
        sessionAffinity : "NONE",
      ],
      certificate       : "",
      network           : "some-network",
      subnet            : "some-subnet"
    ]

    def description = converter.convertDescription(input)
    @Subject def operation = new UpsertGoogleInternalLoadBalancerAtomicOperation(description)
    operation.googleOperationPoller =
      new GoogleOperationPoller(
        googleConfigurationProperties: new GoogleConfigurationProperties(),
        threadSleeper: threadSleeperMock,
        registry: registry,
        safeRetry: safeRetry
      )
    operation.googleNetworkProvider = googleNetworkProviderMock
    operation.googleSubnetProvider = googleSubnetProviderMock
    operation.registry = registry
    operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      1 * computeMock.regions() >> regions
      1 * regions.list(PROJECT_NAME) >> regionsList
      1 * regionsList.execute() >> regionList

      1 * googleNetworkProviderMock.getAllMatchingKeyPattern(networkKeyPattern) >> [googleNetwork]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern(subnetKeyPattern) >> [googleSubnet]

      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> null
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> forwardingRuleInsertOp

      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT_NAME, "basic-check") >> healthChecksGet
      1 * healthChecksGet.execute() >> null
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, DEFAULT_SERVICE) >> backendServicesGet
      1 * backendServicesGet.execute() >> null
      1 * backendServices.insert(PROJECT_NAME, REGION, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> healthChecksInsertOp

      2 * computeMock.regionOperations() >> regionalOperations
      1 * regionalOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME) >> regionalBackendServiceOperationGet
      1 * regionalBackendServiceOperationGet.execute() >> backendServicesInsertOp
      1 * regionalOperations.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> regionalForwardingRuleOperationGet
      1 * regionalForwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should update backend service if it exists."() {
    def computeMock = Mock(Compute)

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()

    credentialsRepo.save(ACCOUNT_NAME, credentials)
    def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
      accountCredentialsProvider: credentialsProvider,
      objectMapper: new ObjectMapper()
    )

    def globalOperations = Mock(Compute.GlobalOperations)
    def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)

    def regionalOperations = Mock(Compute.RegionOperations)
    def regionalBackendServiceOperationGet = Mock(Compute.RegionOperations.Get)
    def regionalForwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)

    def regions = Mock(Compute.Regions)
    def regionsList = Mock(Compute.Regions.List)
    def regionList = new RegionList(items: [new Region(name: REGION)])

    def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
    def networkKeyPattern = "gce:networks:some-network:$ACCOUNT_NAME:global"
    def googleNetwork = new GoogleNetwork(selfLink: "projects/$PROJECT_NAME/global/networks/some-network")

    def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
    def subnetKeyPattern = "gce:subnets:some-subnet:$ACCOUNT_NAME:$REGION"
    def googleSubnet = new GoogleSubnet(selfLink: "projects/$PROJECT_NAME/regions/$REGION/subnetworks/some-subnet")

    def forwardingRules = Mock(Compute.ForwardingRules)
    def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
    def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
    def forwardingRuleInsertOp = new Operation(
        targetLink: "forwarding-rule",
        name: LOAD_BALANCER_NAME,
        status: DONE)

    def healthChecks = Mock(Compute.HealthChecks)
    def healthChecksGet = Mock(Compute.HealthChecks.Get)
    def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
    def healthChecksInsertOp = new Operation(
      targetLink: "health-check",
      name: HEALTH_CHECK_OP_NAME,
      status: DONE
    )

    def backendServices = Mock(Compute.RegionBackendServices)
    def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
    def backendServicesUpdate = Mock(Compute.RegionBackendServices.Update)
    def backendServicesInsertOp = new Operation(
      targetLink: "backend-service",
      name: BACKEND_SERVICE_OP_NAME,
      status: DONE
    )

    def input = [
      accountName       : ACCOUNT_NAME,
      loadBalancerName  : LOAD_BALANCER_NAME,
      portRange         : PORT_RANGE,
      region            : REGION,
      backendService    : [
        name            : DEFAULT_SERVICE,
        backends        : [],
        healthCheck     : hc,
        sessionAffinity : "NONE",
      ],
      certificate       : "",
      network           : "some-network",
      subnet            : "some-subnet"
    ]

    def description = converter.convertDescription(input)
    @Subject def operation = new UpsertGoogleInternalLoadBalancerAtomicOperation(description)
    operation.googleOperationPoller =
      new GoogleOperationPoller(
        googleConfigurationProperties: new GoogleConfigurationProperties(),
        threadSleeper: threadSleeperMock,
        registry: registry,
        safeRetry: safeRetry
      )
    operation.googleNetworkProvider = googleNetworkProviderMock
    operation.googleSubnetProvider = googleSubnetProviderMock
    operation.registry = registry
    operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      1 * computeMock.regions() >> regions
      1 * regions.list(PROJECT_NAME) >> regionsList
      1 * regionsList.execute() >> regionList

      1 * googleNetworkProviderMock.getAllMatchingKeyPattern(networkKeyPattern) >> [googleNetwork]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern(subnetKeyPattern) >> [googleSubnet]

      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> null
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> forwardingRuleInsertOp

      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT_NAME, "basic-check") >> healthChecksGet
      1 * healthChecksGet.execute() >> null
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, DEFAULT_SERVICE) >> backendServicesGet
      1 * backendServicesGet.execute() >> new BackendService(name: DEFAULT_SERVICE, healthChecks: [], sessionAffinity: 'NONE')
      1 * backendServices.update(PROJECT_NAME, REGION, DEFAULT_SERVICE, _) >> backendServicesUpdate
      1 * backendServicesUpdate.execute() >> backendServicesInsertOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> healthChecksInsertOp

      2 * computeMock.regionOperations() >> regionalOperations
      1 * regionalOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME) >> regionalBackendServiceOperationGet
      1 * regionalBackendServiceOperationGet.execute() >> backendServicesInsertOp
      1 * regionalOperations.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> regionalForwardingRuleOperationGet
      1 * regionalForwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should update health check if it exists."() {
    def computeMock = Mock(Compute)

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()

    credentialsRepo.save(ACCOUNT_NAME, credentials)
    def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
      accountCredentialsProvider: credentialsProvider,
      objectMapper: new ObjectMapper()
    )

    def globalOperations = Mock(Compute.GlobalOperations)
    def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)

    def regionalOperations = Mock(Compute.RegionOperations)
    def regionalBackendServiceOperationGet = Mock(Compute.RegionOperations.Get)
    def regionalForwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)

    def regions = Mock(Compute.Regions)
    def regionsList = Mock(Compute.Regions.List)
    def regionList = new RegionList(items: [new Region(name: REGION)])

    def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
    def networkKeyPattern = "gce:networks:some-network:$ACCOUNT_NAME:global"
    def googleNetwork = new GoogleNetwork(selfLink: "projects/$PROJECT_NAME/global/networks/some-network")

    def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
    def subnetKeyPattern = "gce:subnets:some-subnet:$ACCOUNT_NAME:$REGION"
    def googleSubnet = new GoogleSubnet(selfLink: "projects/$PROJECT_NAME/regions/$REGION/subnetworks/some-subnet")

    def forwardingRules = Mock(Compute.ForwardingRules)
    def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
    def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
    def forwardingRuleInsertOp = new Operation(
        targetLink: "forwarding-rule",
        name: LOAD_BALANCER_NAME,
        status: DONE)

    def healthChecks = Mock(Compute.HealthChecks)
    def healthChecksGet = Mock(Compute.HealthChecks.Get)
    def healthChecksUpdate = Mock(Compute.HealthChecks.Update)
    def healthChecksInsertOp = new Operation(
      targetLink: "health-check",
      name: HEALTH_CHECK_OP_NAME,
      status: DONE
    )

    def backendServices = Mock(Compute.RegionBackendServices)
    def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
    def backendServicesUpdate = Mock(Compute.RegionBackendServices.Update)
    def backendServicesInsertOp = new Operation(
      targetLink: "backend-service",
      name: BACKEND_SERVICE_OP_NAME,
      status: DONE
    )

    def input = [
      accountName       : ACCOUNT_NAME,
      loadBalancerName  : LOAD_BALANCER_NAME,
      portRange         : PORT_RANGE,
      region            : REGION,
      backendService    : [
        name            : DEFAULT_SERVICE,
        backends        : [],
        healthCheck     : hc,
        sessionAffinity : "NONE",
      ],
      certificate       : "",
      network           : "some-network",
      subnet            : "some-subnet"
    ]

    def description = converter.convertDescription(input)
    @Subject def operation = new UpsertGoogleInternalLoadBalancerAtomicOperation(description)
    operation.googleOperationPoller =
      new GoogleOperationPoller(
        googleConfigurationProperties: new GoogleConfigurationProperties(),
        threadSleeper: threadSleeperMock,
        registry: registry,
        safeRetry: safeRetry
      )
    operation.googleNetworkProvider = googleNetworkProviderMock
    operation.googleSubnetProvider = googleSubnetProviderMock
    operation.registry = registry
    operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      1 * computeMock.regions() >> regions
      1 * regions.list(PROJECT_NAME) >> regionsList
      1 * regionsList.execute() >> regionList

      1 * googleNetworkProviderMock.getAllMatchingKeyPattern(networkKeyPattern) >> [googleNetwork]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern(subnetKeyPattern) >> [googleSubnet]

      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> null
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRulesInsert.execute() >> forwardingRuleInsertOp

      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.get(PROJECT_NAME, "basic-check") >> healthChecksGet
      1 * healthChecksGet.execute() >> new HealthCheck(name: 'basic-check', checkIntervalSec: 11, httpHealthCheck: new HTTPHealthCheck(port: 80, requestPath: '/'))
      1 * healthChecks.update(PROJECT_NAME, "basic-check", _) >> healthChecksUpdate
      1 * healthChecksUpdate.execute() >> healthChecksInsertOp

      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, DEFAULT_SERVICE) >> backendServicesGet
      1 * backendServicesGet.execute() >> new BackendService(name: DEFAULT_SERVICE, healthChecks: [], sessionAffinity: 'NONE')
      1 * backendServices.update(PROJECT_NAME, REGION, DEFAULT_SERVICE, _) >> backendServicesUpdate
      1 * backendServicesUpdate.execute() >> backendServicesInsertOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> healthChecksInsertOp

      2 * computeMock.regionOperations() >> regionalOperations
      1 * regionalOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME) >> regionalBackendServiceOperationGet
      1 * regionalBackendServiceOperationGet.execute() >> backendServicesInsertOp
      1 * regionalOperations.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> regionalForwardingRuleOperationGet
      1 * regionalForwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }
}
