/*
 * Copyright 2014 Google, Inc.
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

class UpsertGoogleInternalHttpLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final PROJECT_NAME = "my-project"
  private static final HEALTH_CHECK_OP_NAME = "health-check-op"
  private static final BACKEND_SERVICE_OP_NAME = "backend-service-op"
  private static final URL_MAP_OP_NAME = "url-map-op"
  private static final TARGET_HTTP_PROXY_OP_NAME = "target-http-proxy-op"
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
        "requestPath"       : "/",
        "healthCheckType"   : "HTTP",
        "port"              : 80,
        "checkIntervalSec"  : 1,
        "timeoutSec"        : 1,
        "healthyThreshold"  : 1,
        "unhealthyThreshold": 1
    ]
    safeRetry = SafeRetry.withoutDelay()
  }

  void "should create an Internal HTTP Load Balancer with host rule, path matcher, path rules, etc with no existing infrastructure"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedAccountCredentialsRepository()
      def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(ACCOUNT_NAME, credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
          accountCredentialsProvider: credentialsProvider,
          objectMapper: new ObjectMapper()
      )

      def regionOperations = Mock(Compute.RegionOperations)
      def healthCheckOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServiceOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapOperationGet = Mock(Compute.RegionOperations.Get)
      def targetHttpProxyOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)

      def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
      def networkKeyPattern = "gce:networks:some-network:$ACCOUNT_NAME:global"
      def googleNetwork = new GoogleNetwork(selfLink: "projects/$PROJECT_NAME/global/networks/some-network")

      def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
      def subnetKeyPattern = "gce:subnets:some-subnet:$ACCOUNT_NAME:$REGION"
      def googleSubnet = new GoogleSubnet(selfLink: "projects/$PROJECT_NAME/regions/$REGION/subnetworks/some-subnet")

      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksList = Mock(Compute.RegionHealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.RegionHealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesList = Mock(Compute.RegionBackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.RegionBackendServices.Insert)
      def backendServicesInsertOp = new Operation(
          targetLink: "backend-service",
          name: BACKEND_SERVICE_OP_NAME,
          status: DONE)

      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsGet = Mock(Compute.RegionUrlMaps.Get)
      def urlMapsInsert = Mock(Compute.RegionUrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
          targetLink: "url-map",
          name: URL_MAP_OP_NAME,
          status: DONE)

      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.RegionTargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new Operation(
          targetLink: "target-proxy",
          name: TARGET_HTTP_PROXY_OP_NAME,
          status: DONE)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)

      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "region"            : REGION,
        "network"           : "some-network",
        "subnet"            : "some-subnet",
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
          "sessionAffinity": "NONE",
        ],
        "certificate"     : "",
        "hostRules"       : [
          [
            "hostPatterns": [
              "host1.com",
              "host2.com"
            ],
            "pathMatcher" : [
              "pathRules"     : [
                [
                  "paths"         : [
                    "/path",
                    "/path2/more"
                  ],
                  "backendService": [
                    "name"       : PM_SERVICE,
                    "backends"   : [],
                    "healthCheck": hc,
                    "sessionAffinity": "NONE",
                  ]
                ]
              ],
              "defaultService": [
                "name"       : DEFAULT_PM_SERVICE,
                "backends"   : [],
                "healthCheck": hc,
                "sessionAffinity": "NONE",
              ]
            ]
          ]
        ]
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleInternalHttpLoadBalancerAtomicOperation(description)
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

      1 * googleNetworkProviderMock.getAllMatchingKeyPattern(networkKeyPattern) >> [googleNetwork]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern(subnetKeyPattern) >> [googleSubnet]

      2 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME, REGION) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, REGION, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      4 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME, REGION) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      3 * backendServices.insert(PROJECT_NAME, REGION, _) >> backendServicesInsert
      3 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, REGION, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, REGION, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, REGION, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp

      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRules.get(PROJECT_NAME, REGION, _) >> forwardingRulesGet
      1 * forwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * forwardingRulesGet.execute() >> null

      7 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksInsertOp
      3 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME) >> backendServiceOperationGet
      3 * backendServiceOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_OP_NAME) >> urlMapOperationGet
      1 * urlMapOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_OP_NAME) >> targetHttpProxyOperationGet
      1 * targetHttpProxyOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should create an Internal HTTP Load Balancer with minimal description"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedAccountCredentialsRepository()
      def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(ACCOUNT_NAME, credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
          accountCredentialsProvider: credentialsProvider,
          objectMapper: new ObjectMapper()
      )

      def regionOperations = Mock(Compute.RegionOperations)
      def healthCheckOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServiceOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapOperationGet = Mock(Compute.RegionOperations.Get)
      def targetHttpProxyOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)

      def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
      def networkKeyPattern = "gce:networks:some-network:$ACCOUNT_NAME:global"
      def googleNetwork = new GoogleNetwork(selfLink: "projects/$PROJECT_NAME/global/networks/some-network")

      def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
      def subnetKeyPattern = "gce:subnets:some-subnet:$ACCOUNT_NAME:$REGION"
      def googleSubnet = new GoogleSubnet(selfLink: "projects/$PROJECT_NAME/regions/$REGION/subnetworks/some-subnet")

      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksList = Mock(Compute.RegionHealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.RegionHealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesList = Mock(Compute.RegionBackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.RegionBackendServices.Insert)
      def backendServicesInsertOp = new Operation(
          targetLink: "backend-service",
          name: BACKEND_SERVICE_OP_NAME,
          status: DONE)

      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsGet = Mock(Compute.RegionUrlMaps.Get)
      def urlMapsInsert = Mock(Compute.RegionUrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
          targetLink: "url-map",
          name: URL_MAP_OP_NAME,
          status: DONE)

      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.RegionTargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new Operation(
          targetLink: "target-proxy",
          name: TARGET_HTTP_PROXY_OP_NAME,
          status: DONE)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "region"            : REGION,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
          "sessionAffinity": "NONE",
        ],
        "certificate"     : "",
        "hostRules"       : null,
        "network"           : "some-network",
        "subnet"            : "some-subnet",
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleInternalHttpLoadBalancerAtomicOperation(description)
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
      1 * googleNetworkProviderMock.getAllMatchingKeyPattern(networkKeyPattern) >> [googleNetwork]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern(subnetKeyPattern) >> [googleSubnet]

      2 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME, REGION) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, REGION, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME, REGION) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      1 * backendServices.insert(PROJECT_NAME, REGION, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, REGION, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, REGION, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, REGION, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp

      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRules.get(PROJECT_NAME, REGION, _) >> forwardingRulesGet
      1 * forwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * forwardingRulesGet.execute() >> null

      5 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME) >> backendServiceOperationGet
      1 * backendServiceOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_OP_NAME) >> urlMapOperationGet
      1 * urlMapOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_OP_NAME) >> targetHttpProxyOperationGet
      1 * targetHttpProxyOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should create an Internal HTTPS Load Balancer when certificate specified"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedAccountCredentialsRepository()
      def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(ACCOUNT_NAME, credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
          accountCredentialsProvider: credentialsProvider,
          objectMapper: new ObjectMapper()
      )

      def regionOperations = Mock(Compute.RegionOperations)
      def healthCheckOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServiceOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapOperationGet = Mock(Compute.RegionOperations.Get)
      def targetHttpsProxyOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)

      def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
      def networkKeyPattern = "gce:networks:some-network:$ACCOUNT_NAME:global"
      def googleNetwork = new GoogleNetwork(selfLink: "projects/$PROJECT_NAME/global/networks/some-network")

      def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
      def subnetKeyPattern = "gce:subnets:some-subnet:$ACCOUNT_NAME:$REGION"
      def googleSubnet = new GoogleSubnet(selfLink: "projects/$PROJECT_NAME/regions/$REGION/subnetworks/some-subnet")

      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksList = Mock(Compute.RegionHealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.RegionHealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesList = Mock(Compute.RegionBackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.RegionBackendServices.Insert)
      def backendServicesInsertOp = new Operation(
          targetLink: "backend-service",
          name: BACKEND_SERVICE_OP_NAME,
          status: DONE)

      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsGet = Mock(Compute.RegionUrlMaps.Get)
      def urlMapsInsert = Mock(Compute.RegionUrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
          targetLink: "url-map",
          name: URL_MAP_OP_NAME,
          status: DONE)

      def targetHttpsProxies = Mock(Compute.RegionTargetHttpsProxies)
      def targetHttpsProxiesInsert = Mock(Compute.RegionTargetHttpsProxies.Insert)
      def targetHttpsProxiesInsertOp = new Operation(
          targetLink: "target-proxy",
          name: TARGET_HTTP_PROXY_OP_NAME,
          status: DONE)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "certificate"     : "my-cert",
        "portRange"       : PORT_RANGE,
        "region"            : REGION,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
          "sessionAffinity": "NONE",
        ],
        "hostRules"       : null,
        "network"           : "some-network",
        "subnet"            : "some-subnet",
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleInternalHttpLoadBalancerAtomicOperation(description)
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

      1 * googleNetworkProviderMock.getAllMatchingKeyPattern(networkKeyPattern) >> [googleNetwork]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern(subnetKeyPattern) >> [googleSubnet]
      2 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME, REGION) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, REGION, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME, REGION) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      1 * backendServices.insert(PROJECT_NAME, REGION, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, REGION, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, REGION, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.regionTargetHttpsProxies() >> targetHttpsProxies
      1 * targetHttpsProxies.insert(PROJECT_NAME, REGION, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpsProxiesInsert
      1 * targetHttpsProxiesInsert.execute() >> targetHttpsProxiesInsertOp

      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRules.get(PROJECT_NAME, REGION, _) >> forwardingRulesGet
      1 * forwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * forwardingRulesGet.execute() >> null

      5 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME) >> backendServiceOperationGet
      1 * backendServiceOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_OP_NAME) >> urlMapOperationGet
      1 * urlMapOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_OP_NAME) >> targetHttpsProxyOperationGet
      1 * targetHttpsProxyOperationGet.execute() >> healthChecksInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should update health check when it exists and needs updated"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedAccountCredentialsRepository()
      def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(ACCOUNT_NAME, credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        accountCredentialsProvider: credentialsProvider,
        objectMapper: new ObjectMapper()
      )

      def regionOperations = Mock(Compute.RegionOperations)
      def healthCheckOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServiceOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapOperationGet = Mock(Compute.RegionOperations.Get)
      def targetHttpProxyOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)

      def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
      def networkKeyPattern = "gce:networks:some-network:$ACCOUNT_NAME:global"
      def googleNetwork = new GoogleNetwork(selfLink: "projects/$PROJECT_NAME/global/networks/some-network")

      def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
      def subnetKeyPattern = "gce:subnets:some-subnet:$ACCOUNT_NAME:$REGION"
      def googleSubnet = new GoogleSubnet(selfLink: "projects/$PROJECT_NAME/regions/$REGION/subnetworks/some-subnet")

      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksList = Mock(Compute.RegionHealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.RegionHealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def healthChecksUpdateOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesList = Mock(Compute.RegionBackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.RegionBackendServices.Insert)
      def backendServicesInsertOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME,
        status: DONE)

      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsGet = Mock(Compute.RegionUrlMaps.Get)
      def urlMapsInsert = Mock(Compute.RegionUrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
        targetLink: "url-map",
        name: URL_MAP_OP_NAME,
        status: DONE)

      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.RegionTargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new Operation(
        targetLink: "target-proxy",
        name: TARGET_HTTP_PROXY_OP_NAME,
        status: DONE)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)

      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "region"            : REGION,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
          "sessionAffinity": "NONE",
        ],
        "certificate"     : "",
        "hostRules"       : [
          [
            "hostPatterns": [
              "host1.com",
              "host2.com"
            ],
            "pathMatcher" : [
              "pathRules"     : [
                [
                  "paths"         : [
                    "/path",
                    "/path2/more"
                  ],
                  "backendService": [
                    "name"       : PM_SERVICE,
                    "backends"   : [],
                    "healthCheck": hc,
                    "sessionAffinity": "NONE",
                  ]
                ]
              ],
              "defaultService": [
                "name"       : DEFAULT_PM_SERVICE,
                "backends"   : [],
                "healthCheck": hc,
                "sessionAffinity": "NONE",
              ]
            ]
          ]
        ],
        "network"           : "some-network",
        "subnet"            : "some-subnet",
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleInternalHttpLoadBalancerAtomicOperation(description)
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

      1 * googleNetworkProviderMock.getAllMatchingKeyPattern(networkKeyPattern) >> [googleNetwork]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern(subnetKeyPattern) >> [googleSubnet]
      2 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME, REGION) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, REGION, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      4 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME, REGION) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      3 * backendServices.insert(PROJECT_NAME, REGION, _) >> backendServicesInsert
      3 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, REGION, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, REGION, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, REGION, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp

      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRules.get(PROJECT_NAME, REGION, _) >> forwardingRulesGet
      1 * forwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * forwardingRulesGet.execute() >> null

      7 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksUpdateOp
      3 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME) >> backendServiceOperationGet
      3 * backendServiceOperationGet.execute() >> backendServicesInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_OP_NAME) >> urlMapOperationGet
      1 * urlMapOperationGet.execute() >> urlMapsInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_OP_NAME) >> targetHttpProxyOperationGet
      1 * targetHttpProxyOperationGet.execute() >> targetHttpProxiesInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should update backend service if it exists and needs updated"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedAccountCredentialsRepository()
      def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(ACCOUNT_NAME, credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        accountCredentialsProvider: credentialsProvider,
        objectMapper: new ObjectMapper()
      )

      def regionOperations = Mock(Compute.RegionOperations)
      def healthCheckOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServiceOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapOperationGet = Mock(Compute.RegionOperations.Get)
      def targetHttpProxyOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)

      def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
      def networkKeyPattern = "gce:networks:some-network:$ACCOUNT_NAME:global"
      def googleNetwork = new GoogleNetwork(selfLink: "projects/$PROJECT_NAME/global/networks/some-network")

      def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
      def subnetKeyPattern = "gce:subnets:some-subnet:$ACCOUNT_NAME:$REGION"
      def googleSubnet = new GoogleSubnet(selfLink: "projects/$PROJECT_NAME/regions/$REGION/subnetworks/some-subnet")

      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksList = Mock(Compute.RegionHealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.RegionHealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def healthChecksUpdateOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesList = Mock(Compute.RegionBackendServices.List)
      def bsListReal = new BackendServiceList(items: [new BackendService(name: PM_SERVICE, sessionAffinity: 'NONE')])
      def backendServicesInsert = Mock(Compute.RegionBackendServices.Insert)
      def backendServicesInsertOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME,
        status: DONE)
      def backendServicesUpdate = Mock(Compute.RegionBackendServices.Update)
      def backendServicesUpdateOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME + "update",
        status: DONE)

      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsGet = Mock(Compute.RegionUrlMaps.Get)
      def urlMapsInsert = Mock(Compute.RegionUrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
        targetLink: "url-map",
        name: URL_MAP_OP_NAME,
        status: DONE)

      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.RegionTargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new Operation(
        targetLink: "target-proxy",
        name: TARGET_HTTP_PROXY_OP_NAME,
        status: DONE)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)

      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "region"            : REGION,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
          "sessionAffinity": "NONE",
        ],
        "certificate"     : "",
        "hostRules"       : [
          [
            "hostPatterns": [
              "host1.com",
              "host2.com"
            ],
            "pathMatcher" : [
              "pathRules"     : [
                [
                  "paths"         : [
                    "/path",
                    "/path2/more"
                  ],
                  "backendService": [
                    "name"       : PM_SERVICE,
                    "backends"   : [],
                    "healthCheck": hc,
                    "sessionAffinity": "NONE",
                  ]
                ]
              ],
              "defaultService": [
                "name"       : DEFAULT_PM_SERVICE,
                "backends"   : [],
                "healthCheck": hc,
                "sessionAffinity": "NONE",
              ]
            ]
          ]
        ],
        "network"           : "some-network",
        "subnet"            : "some-subnet",
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleInternalHttpLoadBalancerAtomicOperation(description)
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

      1 * googleNetworkProviderMock.getAllMatchingKeyPattern(networkKeyPattern) >> [googleNetwork]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern(subnetKeyPattern) >> [googleSubnet]
      2 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME, REGION) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, REGION, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      4 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME, REGION) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      2 * backendServices.insert(PROJECT_NAME, REGION, _) >> backendServicesInsert
      2 * backendServicesInsert.execute() >> backendServicesInsertOp
      1 * backendServices.update(PROJECT_NAME, REGION, PM_SERVICE, _) >> backendServicesUpdate
      1 * backendServicesUpdate.execute() >> backendServicesUpdateOp

      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, REGION, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, REGION, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, REGION, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp

      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRules.get(PROJECT_NAME, REGION, _) >> forwardingRulesGet
      1 * forwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * forwardingRulesGet.execute() >> null

      7 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksUpdateOp
      2 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME) >> backendServiceOperationGet
      2 * backendServiceOperationGet.execute() >> backendServicesInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME + "update") >> backendServiceOperationGet
      1 * backendServiceOperationGet.execute() >> backendServicesUpdateOp
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_OP_NAME) >> urlMapOperationGet
      1 * urlMapOperationGet.execute() >> urlMapsInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_OP_NAME) >> targetHttpProxyOperationGet
      1 * targetHttpProxyOperationGet.execute() >> targetHttpProxiesInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should update url map if it exists and needs updated"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedAccountCredentialsRepository()
      def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME,).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(ACCOUNT_NAME, credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        accountCredentialsProvider: credentialsProvider,
        objectMapper: new ObjectMapper()
      )

      def regionOperations = Mock(Compute.RegionOperations)
      def healthCheckOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServiceOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapOperationGet = Mock(Compute.RegionOperations.Get)
      def targetHttpProxyOperationGet = Mock(Compute.RegionOperations.Get)
      def forwardingRuleOperationGet = Mock(Compute.RegionOperations.Get)

      def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
      def networkKeyPattern = "gce:networks:some-network:$ACCOUNT_NAME:global"
      def googleNetwork = new GoogleNetwork(selfLink: "projects/$PROJECT_NAME/global/networks/some-network")

      def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
      def subnetKeyPattern = "gce:subnets:some-subnet:$ACCOUNT_NAME:$REGION"
      def googleSubnet = new GoogleSubnet(selfLink: "projects/$PROJECT_NAME/regions/$REGION/subnetworks/some-subnet")


      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksList = Mock(Compute.RegionHealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.RegionHealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def healthChecksUpdateOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesList = Mock(Compute.RegionBackendServices.List)
      def bsListReal = new BackendServiceList(items: [new BackendService(name: PM_SERVICE, sessionAffinity: 'NONE')])
      def backendServicesInsert = Mock(Compute.RegionBackendServices.Insert)
      def backendServicesInsertOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME,
        status: DONE)
      def backendServicesUpdate = Mock(Compute.RegionBackendServices.Update)
      def backendServicesUpdateOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME + "update",
        status: DONE)

      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsGet = Mock(Compute.RegionUrlMaps.Get)
      def urlMapReal = new UrlMap(name: LOAD_BALANCER_NAME)
      def urlMapsUpdate = Mock(Compute.RegionUrlMaps.Update)
      def urlMapsUpdateOp = new Operation(
        targetLink: "url-map",
        name: URL_MAP_OP_NAME,
        status: DONE)

      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.RegionTargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new Operation(
        targetLink: "target-proxy",
        name: TARGET_HTTP_PROXY_OP_NAME,
        status: DONE)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesInsert = Mock(Compute.ForwardingRules.Insert)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)

      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "region"            : REGION,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
          "sessionAffinity": "NONE",
        ],
        "certificate"     : "",
        "hostRules"       : [
          [
            "hostPatterns": [
              "host1.com",
              "host2.com"
            ],
            "pathMatcher" : [
              "pathRules"     : [
                [
                  "paths"         : [
                    "/path",
                    "/path2/more"
                  ],
                  "backendService": [
                    "name"       : PM_SERVICE,
                    "backends"   : [],
                    "healthCheck": hc,
                    "sessionAffinity": "NONE",
                  ]
                ]
              ],
              "defaultService": [
                "name"       : DEFAULT_PM_SERVICE,
                "backends"   : [],
                "healthCheck": hc,
                "sessionAffinity": "NONE",
              ]
            ]
          ]
        ],
        "network"           : "some-network",
        "subnet"            : "some-subnet",
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleInternalHttpLoadBalancerAtomicOperation(description)
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

      1 * googleNetworkProviderMock.getAllMatchingKeyPattern(networkKeyPattern) >> [googleNetwork]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern(subnetKeyPattern) >> [googleSubnet]
      2 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME, REGION) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, REGION, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      4 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME, REGION) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      2 * backendServices.insert(PROJECT_NAME, REGION, _) >> backendServicesInsert
      2 * backendServicesInsert.execute() >> backendServicesInsertOp
      1 * backendServices.update(PROJECT_NAME, REGION, PM_SERVICE, _) >> backendServicesUpdate
      1 * backendServicesUpdate.execute() >> backendServicesUpdateOp

      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, REGION, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> urlMapReal
      1 * urlMaps.update(PROJECT_NAME, REGION, LOAD_BALANCER_NAME, _) >> urlMapsUpdate
      1 * urlMapsUpdate.execute() >> urlMapsUpdateOp

      1 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, REGION, {it.urlMap == urlMapsUpdateOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp

      2 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.insert(PROJECT_NAME, REGION, _) >> forwardingRulesInsert
      1 * forwardingRules.get(PROJECT_NAME, REGION, _) >> forwardingRulesGet
      1 * forwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * forwardingRulesGet.execute() >> null

      7 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_OP_NAME) >> healthCheckOperationGet
      1 * healthCheckOperationGet.execute() >> healthChecksUpdateOp
      2 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME) >> backendServiceOperationGet
      2 * backendServiceOperationGet.execute() >> backendServicesInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_OP_NAME + "update") >> backendServiceOperationGet
      1 * backendServiceOperationGet.execute() >> backendServicesUpdateOp
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_OP_NAME) >> urlMapOperationGet
      1 * urlMapOperationGet.execute() >> urlMapsUpdateOp
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_OP_NAME) >> targetHttpProxyOperationGet
      1 * targetHttpProxyOperationGet.execute() >> targetHttpProxiesInsertOp
      1 * regionOperations.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRuleOperationGet
      1 * forwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }
}
