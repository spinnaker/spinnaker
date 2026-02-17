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
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import static UpsertGoogleHttpLoadBalancerTestConstants.*

class UpsertGoogleHttpLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final PROJECT_NAME = "my-project"
  private static final HEALTH_CHECK_OP_NAME = "health-check-op"
  private static final BACKEND_SERVICE_OP_NAME = "backend-service-op"
  private static final URL_MAP_OP_NAME = "url-map-op"
  private static final TARGET_HTTP_PROXY_OP_NAME = "target-http-proxy-op"
  private static final DONE = "DONE"

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

  void "should create an HTTP Load Balancer with host rule, path matcher, path rules, etc with no existing infrastructure"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpProxyOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalForwardingRuleOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
          targetLink: "backend-service",
          name: BACKEND_SERVICE_OP_NAME,
          status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
          targetLink: "url-map",
          name: URL_MAP_OP_NAME,
          status: DONE)

      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.TargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new Operation(
          targetLink: "target-proxy",
          name: TARGET_HTTP_PROXY_OP_NAME,
          status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)

      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
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
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
     operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      4 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      3 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      3 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp

      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME, _) >> globalForwardingRulesInsert
      1 * globalForwardingRules.get(PROJECT_NAME, _) >> globalForwardingRulesGet
      1 * globalForwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * globalForwardingRulesGet.execute() >> null

      7 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> healthChecksInsertOp
      3 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      3 * globalBackendServiceOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_OP_NAME) >> globalUrlMapOperationGet
      1 * globalUrlMapOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_OP_NAME) >> globalTargetHttpProxyOperationGet
      1 * globalTargetHttpProxyOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRuleOperationGet
      1 * globalForwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should create an HTTP Load Balancer with minimal description"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpProxyOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalForwardingRuleOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
          targetLink: "backend-service",
          name: BACKEND_SERVICE_OP_NAME,
          status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
          targetLink: "url-map",
          name: URL_MAP_OP_NAME,
          status: DONE)

      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.TargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new Operation(
          targetLink: "target-proxy",
          name: TARGET_HTTP_PROXY_OP_NAME,
          status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
          "sessionAffinity": "NONE",
        ],
        "certificate"     : "",
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(
            googleConfigurationProperties: new GoogleConfigurationProperties(),
            threadSleeper: threadSleeperMock,
            registry: registry,
            safeRetry: safeRetry
          )
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      1 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp

      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME, _) >> globalForwardingRulesInsert
      1 * globalForwardingRules.get(PROJECT_NAME, _) >> globalForwardingRulesGet
      1 * globalForwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * globalForwardingRulesGet.execute() >> null

      5 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      1 * globalBackendServiceOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_OP_NAME) >> globalUrlMapOperationGet
      1 * globalUrlMapOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_OP_NAME) >> globalTargetHttpProxyOperationGet
      1 * globalTargetHttpProxyOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRuleOperationGet
      1 * globalForwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should create an HTTPS Load Balancer when certificate specified"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpsProxyOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalForwardingRuleOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
          targetLink: "backend-service",
          name: BACKEND_SERVICE_OP_NAME,
          status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
          targetLink: "url-map",
          name: URL_MAP_OP_NAME,
          status: DONE)

      def targetHttpsProxies = Mock(Compute.TargetHttpsProxies)
      def targetHttpsProxiesInsert = Mock(Compute.TargetHttpsProxies.Insert)
      def targetHttpsProxiesInsertOp = new Operation(
          targetLink: "target-proxy",
          name: TARGET_HTTP_PROXY_OP_NAME,
          status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "certificate"     : "my-cert",
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
          "sessionAffinity": "NONE",
        ],
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(
            googleConfigurationProperties: new GoogleConfigurationProperties(),
            threadSleeper: threadSleeperMock,
            registry: registry,
            safeRetry: safeRetry
          )
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      1 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.targetHttpsProxies() >> targetHttpsProxies
      1 * targetHttpsProxies.insert(PROJECT_NAME, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpsProxiesInsert
      1 * targetHttpsProxiesInsert.execute() >> targetHttpsProxiesInsertOp

      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME, _) >> globalForwardingRulesInsert
      1 * globalForwardingRules.get(PROJECT_NAME, _) >> globalForwardingRulesGet
      1 * globalForwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * globalForwardingRulesGet.execute() >> null

      5 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      1 * globalBackendServiceOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_OP_NAME) >> globalUrlMapOperationGet
      1 * globalUrlMapOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_OP_NAME) >> globalTargetHttpsProxyOperationGet
      1 * globalTargetHttpsProxyOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRuleOperationGet
      1 * globalForwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should create an HTTPS Load Balancer when certificateMap specified"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpsProxyOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalForwardingRuleOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
          targetLink: "backend-service",
          name: BACKEND_SERVICE_OP_NAME,
          status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
          targetLink: "url-map",
          name: URL_MAP_OP_NAME,
          status: DONE)

      def targetHttpsProxies = Mock(Compute.TargetHttpsProxies)
      def targetHttpsProxiesInsert = Mock(Compute.TargetHttpsProxies.Insert)
      def targetHttpsProxiesInsertOp = new Operation(
          targetLink: "target-proxy",
          name: TARGET_HTTP_PROXY_OP_NAME,
          status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "certificateMap"  : "my-map",
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
          "sessionAffinity": "NONE",
        ],
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(
            googleConfigurationProperties: new GoogleConfigurationProperties(),
            threadSleeper: threadSleeperMock,
            registry: registry,
            safeRetry: safeRetry
          )
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      1 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.targetHttpsProxies() >> targetHttpsProxies
      1 * targetHttpsProxies.insert(PROJECT_NAME, {
        it.urlMap == urlMapsInsertOp.targetLink &&
          it.certificateMap == "//certificatemanager.googleapis.com/projects/${PROJECT_NAME}/locations/global/certificateMaps/my-map".toString() &&
          it.sslCertificates == null
      }) >> targetHttpsProxiesInsert
      1 * targetHttpsProxiesInsert.execute() >> targetHttpsProxiesInsertOp

      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME, _) >> globalForwardingRulesInsert
      1 * globalForwardingRules.get(PROJECT_NAME, _) >> globalForwardingRulesGet
      1 * globalForwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * globalForwardingRulesGet.execute() >> null

      5 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      1 * globalBackendServiceOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_OP_NAME) >> globalUrlMapOperationGet
      1 * globalUrlMapOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_OP_NAME) >> globalTargetHttpsProxyOperationGet
      1 * globalTargetHttpsProxyOperationGet.execute() >> healthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRuleOperationGet
      1 * globalForwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should update health check when it exists and needs updated"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpProxyOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalForwardingRuleOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def healthChecksUpdateOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME,
        status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
        targetLink: "url-map",
        name: URL_MAP_OP_NAME,
        status: DONE)

      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.TargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new Operation(
        targetLink: "target-proxy",
        name: TARGET_HTTP_PROXY_OP_NAME,
        status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)

      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
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
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      4 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      3 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      3 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp

      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME, _) >> globalForwardingRulesInsert
      1 * globalForwardingRules.get(PROJECT_NAME, _) >> globalForwardingRulesGet
      1 * globalForwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * globalForwardingRulesGet.execute() >> null

      7 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> healthChecksUpdateOp
      3 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      3 * globalBackendServiceOperationGet.execute() >> backendServicesInsertOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_OP_NAME) >> globalUrlMapOperationGet
      1 * globalUrlMapOperationGet.execute() >> urlMapsInsertOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_OP_NAME) >> globalTargetHttpProxyOperationGet
      1 * globalTargetHttpProxyOperationGet.execute() >> targetHttpProxiesInsertOp
      1 * globalOperations.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRuleOperationGet
      1 * globalForwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should update backend service if it exists and needs updated"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpProxyOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalForwardingRuleOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def healthChecksUpdateOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [new BackendService(name: PM_SERVICE, sessionAffinity: 'NONE')])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME,
        status: DONE)
      def backendServicesUpdate = Mock(Compute.BackendServices.Update)
      def backendServicesUpdateOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME + "update",
        status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapsInsertOp = new Operation(
        targetLink: "url-map",
        name: URL_MAP_OP_NAME,
        status: DONE)

      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.TargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new Operation(
        targetLink: "target-proxy",
        name: TARGET_HTTP_PROXY_OP_NAME,
        status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)

      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
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
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      4 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      2 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      2 * backendServicesInsert.execute() >> backendServicesInsertOp
      1 * backendServices.update(PROJECT_NAME, PM_SERVICE, _) >> backendServicesUpdate
      1 * backendServicesUpdate.execute() >> backendServicesUpdateOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp

      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME, _) >> globalForwardingRulesInsert
      1 * globalForwardingRules.get(PROJECT_NAME, _) >> globalForwardingRulesGet
      1 * globalForwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * globalForwardingRulesGet.execute() >> null

      7 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> healthChecksUpdateOp
      2 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      2 * globalBackendServiceOperationGet.execute() >> backendServicesInsertOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME + "update") >> globalBackendServiceOperationGet
      1 * globalBackendServiceOperationGet.execute() >> backendServicesUpdateOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_OP_NAME) >> globalUrlMapOperationGet
      1 * globalUrlMapOperationGet.execute() >> urlMapsInsertOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_OP_NAME) >> globalTargetHttpProxyOperationGet
      1 * globalTargetHttpProxyOperationGet.execute() >> targetHttpProxiesInsertOp
      1 * globalOperations.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRuleOperationGet
      1 * globalForwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "should update url map if it exists and needs updated"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpProxyOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalForwardingRuleOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def healthChecksUpdateOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [new BackendService(name: PM_SERVICE, sessionAffinity: 'NONE')])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME,
        status: DONE)
      def backendServicesUpdate = Mock(Compute.BackendServices.Update)
      def backendServicesUpdateOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME + "update",
        status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapReal = new UrlMap(name: LOAD_BALANCER_NAME)
      def urlMapsUpdate = Mock(Compute.UrlMaps.Update)
      def urlMapsUpdateOp = new Operation(
        targetLink: "url-map",
        name: URL_MAP_OP_NAME,
        status: DONE)

      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.TargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new Operation(
        targetLink: "target-proxy",
        name: TARGET_HTTP_PROXY_OP_NAME,
        status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRuleInsertOp = new Operation(
          targetLink: "forwarding-rule",
          name: LOAD_BALANCER_NAME,
          status: DONE)

      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
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
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      4 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      2 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      2 * backendServicesInsert.execute() >> backendServicesInsertOp
      1 * backendServices.update(PROJECT_NAME, PM_SERVICE, _) >> backendServicesUpdate
      1 * backendServicesUpdate.execute() >> backendServicesUpdateOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, description.loadBalancerName) >> urlMapsGet
      1 * urlMapsGet.execute() >> urlMapReal
      1 * urlMaps.update(PROJECT_NAME, LOAD_BALANCER_NAME, _) >> urlMapsUpdate
      1 * urlMapsUpdate.execute() >> urlMapsUpdateOp

      1 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, {it.urlMap == urlMapsUpdateOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp

      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME, _) >> globalForwardingRulesInsert
      1 * globalForwardingRules.get(PROJECT_NAME, _) >> globalForwardingRulesGet
      1 * globalForwardingRulesInsert.execute() >> forwardingRuleInsertOp
      1 * globalForwardingRulesGet.execute() >> null

      7 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> healthChecksUpdateOp
      2 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      2 * globalBackendServiceOperationGet.execute() >> backendServicesInsertOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME + "update") >> globalBackendServiceOperationGet
      1 * globalBackendServiceOperationGet.execute() >> backendServicesUpdateOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_OP_NAME) >> globalUrlMapOperationGet
      1 * globalUrlMapOperationGet.execute() >> urlMapsUpdateOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_OP_NAME) >> globalTargetHttpProxyOperationGet
      1 * globalTargetHttpProxyOperationGet.execute() >> targetHttpProxiesInsertOp
      1 * globalOperations.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRuleOperationGet
      1 * globalForwardingRuleOperationGet.execute() >> forwardingRuleInsertOp
  }

  void "first ssl certificate name helper handles null and empty list"() {
    expect:
      UpsertGoogleHttpLoadBalancerAtomicOperation.getFirstSslCertificateName(null) == null
      UpsertGoogleHttpLoadBalancerAtomicOperation.getFirstSslCertificateName([]) == null
      UpsertGoogleHttpLoadBalancerAtomicOperation.getFirstSslCertificateName(
        ["https://www.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/sslCertificates/my-cert"]
      ) == "my-cert"
  }

  void "map to cert migration clears certificateMap after setting ssl certificate"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
        targetLink: "health-check",
        name: HEALTH_CHECK_OP_NAME,
        status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME,
        status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapUrl = "https://www.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/urlMaps/${LOAD_BALANCER_NAME}".toString()
      def urlMapsInsertOp = new Operation(
        targetLink: urlMapUrl,
        name: URL_MAP_OP_NAME,
        status: DONE)

      def targetProxyName = "${LOAD_BALANCER_NAME}-${UpsertGoogleHttpLoadBalancerAtomicOperation.TARGET_HTTPS_PROXY_NAME_PREFIX}"
      def targetProxyUrl = "https://www.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/targetHttpsProxies/${targetProxyName}".toString()
      def existingForwardingRule = new ForwardingRule(
        name: LOAD_BALANCER_NAME,
        target: targetProxyUrl,
      )
      def existingTargetProxy = new TargetHttpsProxy(
        name: targetProxyName,
        selfLink: targetProxyUrl,
        urlMap: urlMapUrl,
        sslCertificates: [],
        certificateMap: "//certificatemanager.googleapis.com/projects/${PROJECT_NAME}/locations/global/certificateMaps/my-map".toString(),
      )

      def targetHttpsProxies = Mock(Compute.TargetHttpsProxies)
      def targetHttpsProxiesGet = Mock(Compute.TargetHttpsProxies.Get)
      def targetHttpsProxiesSetSsl = Mock(Compute.TargetHttpsProxies.SetSslCertificates)
      def targetHttpsProxiesSetCertificateMap = Mock(Compute.TargetHttpsProxies.SetCertificateMap)
      def targetHttpsProxiesSetUrlMap = Mock(Compute.TargetHttpsProxies.SetUrlMap)
      def targetHttpsProxySetSslOp = new Operation(
        targetLink: targetProxyUrl,
        name: "set-ssl",
        status: DONE)
      def targetHttpsProxyClearCertificateMapOp = new Operation(
        targetLink: targetProxyUrl,
        name: "clear-certificate-map",
        status: DONE)
      def targetHttpsProxySetUrlMapOp = new Operation(
        targetLink: targetProxyUrl,
        name: "set-url-map",
        status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)

      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "urlMapName"      : LOAD_BALANCER_NAME,
        "certificate"     : "my-cert",
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"           : DEFAULT_SERVICE,
          "backends"       : [],
          "healthCheck"    : hc,
          "sessionAffinity": "NONE",
        ],
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      def googleOperationPoller = Mock(GoogleOperationPoller)
      operation.googleOperationPoller = googleOperationPoller
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      1 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> existingForwardingRule

      4 * computeMock.targetHttpsProxies() >> targetHttpsProxies
      1 * targetHttpsProxies.get(PROJECT_NAME, targetProxyName) >> targetHttpsProxiesGet
      1 * targetHttpsProxiesGet.execute() >> existingTargetProxy
      1 * targetHttpsProxies.setSslCertificates(PROJECT_NAME, targetProxyName, {
        it.sslCertificates == ["https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/sslCertificates/my-cert".toString()]
      }) >> targetHttpsProxiesSetSsl
      1 * targetHttpsProxiesSetSsl.execute() >> targetHttpsProxySetSslOp
      1 * targetHttpsProxies.setCertificateMap(PROJECT_NAME, targetProxyName, { it.certificateMap == "" }) >> targetHttpsProxiesSetCertificateMap
      1 * targetHttpsProxiesSetCertificateMap.execute() >> targetHttpsProxyClearCertificateMapOp
      1 * targetHttpsProxies.setUrlMap(PROJECT_NAME, targetProxyName, { it.urlMap == urlMapUrl }) >> targetHttpsProxiesSetUrlMap
      1 * targetHttpsProxiesSetUrlMap.execute() >> targetHttpsProxySetUrlMapOp

      _ * googleOperationPoller.waitForGlobalOperation(_, _, _, _, _, _, _)
      0 * computeMock.targetHttpProxies()
      0 * computeMock.globalOperations()
  }

  void "cert to map migration sets certificateMap and skips ssl certs"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
        targetLink: "health-check",
        name: HEALTH_CHECK_OP_NAME,
        status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME,
        status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapUrl = "https://www.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/urlMaps/${LOAD_BALANCER_NAME}".toString()
      def urlMapsInsertOp = new Operation(
        targetLink: urlMapUrl,
        name: URL_MAP_OP_NAME,
        status: DONE)

      def targetProxyName = "${LOAD_BALANCER_NAME}-${UpsertGoogleHttpLoadBalancerAtomicOperation.TARGET_HTTPS_PROXY_NAME_PREFIX}"
      def targetProxyUrl = "https://www.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/targetHttpsProxies/${targetProxyName}".toString()
      def existingForwardingRule = new ForwardingRule(
        name: LOAD_BALANCER_NAME,
        target: targetProxyUrl,
      )
      // Existing proxy uses sslCertificates with no certificateMap.
      def existingTargetProxy = new TargetHttpsProxy(
        name: targetProxyName,
        selfLink: targetProxyUrl,
        urlMap: urlMapUrl,
        sslCertificates: ["https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/sslCertificates/old-cert".toString()],
      )

      def targetHttpsProxies = Mock(Compute.TargetHttpsProxies)
      def targetHttpsProxiesGet = Mock(Compute.TargetHttpsProxies.Get)
      def targetHttpsProxiesSetCertificateMap = Mock(Compute.TargetHttpsProxies.SetCertificateMap)
      def targetHttpsProxiesSetUrlMap = Mock(Compute.TargetHttpsProxies.SetUrlMap)
      def targetHttpsProxySetCertificateMapOp = new Operation(
        targetLink: targetProxyUrl,
        name: "set-certificate-map",
        status: DONE)
      def targetHttpsProxySetUrlMapOp = new Operation(
        targetLink: targetProxyUrl,
        name: "set-url-map",
        status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)

      // Desired state: switch from sslCertificates to certificateMap.
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "urlMapName"      : LOAD_BALANCER_NAME,
        "certificateMap"  : "my-map",
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"           : DEFAULT_SERVICE,
          "backends"       : [],
          "healthCheck"    : hc,
          "sessionAffinity": "NONE",
        ],
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      def googleOperationPoller = Mock(GoogleOperationPoller)
      operation.googleOperationPoller = googleOperationPoller
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      1 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> existingForwardingRule

      // setCertificateMap is called with the full Certificate Manager URL — no ssl cert or clear ops.
      3 * computeMock.targetHttpsProxies() >> targetHttpsProxies
      1 * targetHttpsProxies.get(PROJECT_NAME, targetProxyName) >> targetHttpsProxiesGet
      1 * targetHttpsProxiesGet.execute() >> existingTargetProxy
      1 * targetHttpsProxies.setCertificateMap(PROJECT_NAME, targetProxyName, {
        it.certificateMap == "//certificatemanager.googleapis.com/projects/${PROJECT_NAME}/locations/global/certificateMaps/my-map".toString()
      }) >> targetHttpsProxiesSetCertificateMap
      1 * targetHttpsProxiesSetCertificateMap.execute() >> targetHttpsProxySetCertificateMapOp
      1 * targetHttpsProxies.setUrlMap(PROJECT_NAME, targetProxyName, { it.urlMap == urlMapUrl }) >> targetHttpsProxiesSetUrlMap
      1 * targetHttpsProxiesSetUrlMap.execute() >> targetHttpsProxySetUrlMapOp

      _ * googleOperationPoller.waitForGlobalOperation(_, _, _, _, _, _, _)
      // Setting certificateMap makes sslCertificates inert (certificateMap takes precedence per
      // GCP API docs), so setSslCertificates must not be called during a cert-to-map migration.
      0 * targetHttpsProxies.setSslCertificates(_, _, _)
      0 * computeMock.targetHttpProxies()
      0 * computeMock.globalOperations()
  }

  void "map to different map updates certificateMap without clear"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
        targetLink: "health-check",
        name: HEALTH_CHECK_OP_NAME,
        status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME,
        status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapUrl = "https://www.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/urlMaps/${LOAD_BALANCER_NAME}".toString()
      def urlMapsInsertOp = new Operation(
        targetLink: urlMapUrl,
        name: URL_MAP_OP_NAME,
        status: DONE)

      def targetProxyName = "${LOAD_BALANCER_NAME}-${UpsertGoogleHttpLoadBalancerAtomicOperation.TARGET_HTTPS_PROXY_NAME_PREFIX}"
      def targetProxyUrl = "https://www.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/targetHttpsProxies/${targetProxyName}".toString()
      def existingForwardingRule = new ForwardingRule(
        name: LOAD_BALANCER_NAME,
        target: targetProxyUrl,
      )
      // Existing proxy already uses certificateMap (map-a).
      def existingTargetProxy = new TargetHttpsProxy(
        name: targetProxyName,
        selfLink: targetProxyUrl,
        urlMap: urlMapUrl,
        sslCertificates: [],
        certificateMap: "//certificatemanager.googleapis.com/projects/${PROJECT_NAME}/locations/global/certificateMaps/map-a".toString(),
      )

      def targetHttpsProxies = Mock(Compute.TargetHttpsProxies)
      def targetHttpsProxiesGet = Mock(Compute.TargetHttpsProxies.Get)
      def targetHttpsProxiesSetCertificateMap = Mock(Compute.TargetHttpsProxies.SetCertificateMap)
      def targetHttpsProxiesSetUrlMap = Mock(Compute.TargetHttpsProxies.SetUrlMap)
      def targetHttpsProxySetCertificateMapOp = new Operation(
        targetLink: targetProxyUrl,
        name: "set-certificate-map",
        status: DONE)
      def targetHttpsProxySetUrlMapOp = new Operation(
        targetLink: targetProxyUrl,
        name: "set-url-map",
        status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)

      // Desired state: switch from map-a to map-b.
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "urlMapName"      : LOAD_BALANCER_NAME,
        "certificateMap"  : "map-b",
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"           : DEFAULT_SERVICE,
          "backends"       : [],
          "healthCheck"    : hc,
          "sessionAffinity": "NONE",
        ],
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      def googleOperationPoller = Mock(GoogleOperationPoller)
      operation.googleOperationPoller = googleOperationPoller
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      1 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> existingForwardingRule

      // setCertificateMap is called with the new map URL. No clear (empty-string) call
      // because shouldClearCertificateMap is false when both existing and desired maps are present.
      3 * computeMock.targetHttpsProxies() >> targetHttpsProxies
      1 * targetHttpsProxies.get(PROJECT_NAME, targetProxyName) >> targetHttpsProxiesGet
      1 * targetHttpsProxiesGet.execute() >> existingTargetProxy
      1 * targetHttpsProxies.setCertificateMap(PROJECT_NAME, targetProxyName, {
        it.certificateMap == "//certificatemanager.googleapis.com/projects/${PROJECT_NAME}/locations/global/certificateMaps/map-b".toString()
      }) >> targetHttpsProxiesSetCertificateMap
      1 * targetHttpsProxiesSetCertificateMap.execute() >> targetHttpsProxySetCertificateMapOp
      1 * targetHttpsProxies.setUrlMap(PROJECT_NAME, targetProxyName, { it.urlMap == urlMapUrl }) >> targetHttpsProxiesSetUrlMap
      1 * targetHttpsProxiesSetUrlMap.execute() >> targetHttpsProxySetUrlMapOp

      _ * googleOperationPoller.waitForGlobalOperation(_, _, _, _, _, _, _)
      0 * targetHttpsProxies.setSslCertificates(_, _, _)
      0 * computeMock.targetHttpProxies()
      0 * computeMock.globalOperations()
  }

  void "cert rotation with no existing map does not call setCertificateMap"() {
    setup:
      def computeMock = Mock(Compute)

      def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
        new NoopCredentialsLifecycleHandler<>())
      def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).project(PROJECT_NAME).applicationName("my-application").compute(computeMock).credentials(new FakeGoogleCredentials()).build()
      credentialsRepo.save(credentials)
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo
      )

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)

      def healthChecks = Mock(Compute.HealthChecks)
      def healthChecksList = Mock(Compute.HealthChecks.List)
      def healthCheckListReal = new HealthCheckList(items: [])
      def healthChecksInsert = Mock(Compute.HealthChecks.Insert)
      def healthChecksInsertOp = new Operation(
        targetLink: "health-check",
        name: HEALTH_CHECK_OP_NAME,
        status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [])
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
        targetLink: "backend-service",
        name: BACKEND_SERVICE_OP_NAME,
        status: DONE)

      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapUrl = "https://www.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/urlMaps/${LOAD_BALANCER_NAME}".toString()
      def urlMapsInsertOp = new Operation(
        targetLink: urlMapUrl,
        name: URL_MAP_OP_NAME,
        status: DONE)

      def targetProxyName = "${LOAD_BALANCER_NAME}-${UpsertGoogleHttpLoadBalancerAtomicOperation.TARGET_HTTPS_PROXY_NAME_PREFIX}"
      def targetProxyUrl = "https://www.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/targetHttpsProxies/${targetProxyName}".toString()
      def existingForwardingRule = new ForwardingRule(
        name: LOAD_BALANCER_NAME,
        target: targetProxyUrl,
      )
      // Existing proxy uses sslCertificates with no certificateMap — plain cert rotation.
      def existingTargetProxy = new TargetHttpsProxy(
        name: targetProxyName,
        selfLink: targetProxyUrl,
        urlMap: urlMapUrl,
        sslCertificates: ["https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/sslCertificates/cert-a".toString()],
      )

      def targetHttpsProxies = Mock(Compute.TargetHttpsProxies)
      def targetHttpsProxiesGet = Mock(Compute.TargetHttpsProxies.Get)
      def targetHttpsProxiesSetSsl = Mock(Compute.TargetHttpsProxies.SetSslCertificates)
      def targetHttpsProxiesSetUrlMap = Mock(Compute.TargetHttpsProxies.SetUrlMap)
      def targetHttpsProxySetSslOp = new Operation(
        targetLink: targetProxyUrl,
        name: "set-ssl",
        status: DONE)
      def targetHttpsProxySetUrlMapOp = new Operation(
        targetLink: targetProxyUrl,
        name: "set-url-map",
        status: DONE)

      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)

      // Desired state: rotate from cert-a to cert-b (no certificateMap involved).
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "urlMapName"      : LOAD_BALANCER_NAME,
        "certificate"     : "cert-b",
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"           : DEFAULT_SERVICE,
          "backends"       : [],
          "healthCheck"    : hc,
          "sessionAffinity": "NONE",
        ],
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new UpsertGoogleHttpLoadBalancerAtomicOperation(description)
      def googleOperationPoller = Mock(GoogleOperationPoller)
      operation.googleOperationPoller = googleOperationPoller
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      2 * computeMock.healthChecks() >> healthChecks
      1 * healthChecks.list(PROJECT_NAME) >> healthChecksList
      1 * healthChecksList.execute() >> healthCheckListReal
      1 * healthChecks.insert(PROJECT_NAME, _) >> healthChecksInsert
      1 * healthChecksInsert.execute() >> healthChecksInsertOp

      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal

      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      1 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> urlMapsGet
      1 * urlMapsGet.execute() >> null
      1 * urlMaps.insert(PROJECT_NAME, _) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.get(PROJECT_NAME, LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> existingForwardingRule

      // Only setSslCertificates is called — no setCertificateMap at all because the existing
      // proxy had no certificateMap, so shouldClearCertificateMap(null, null) returns false.
      3 * computeMock.targetHttpsProxies() >> targetHttpsProxies
      1 * targetHttpsProxies.get(PROJECT_NAME, targetProxyName) >> targetHttpsProxiesGet
      1 * targetHttpsProxiesGet.execute() >> existingTargetProxy
      1 * targetHttpsProxies.setSslCertificates(PROJECT_NAME, targetProxyName, {
        it.sslCertificates == ["https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/sslCertificates/cert-b".toString()]
      }) >> targetHttpsProxiesSetSsl
      1 * targetHttpsProxiesSetSsl.execute() >> targetHttpsProxySetSslOp
      1 * targetHttpsProxies.setUrlMap(PROJECT_NAME, targetProxyName, { it.urlMap == urlMapUrl }) >> targetHttpsProxiesSetUrlMap
      1 * targetHttpsProxiesSetUrlMap.execute() >> targetHttpsProxySetUrlMapOp

      _ * googleOperationPoller.waitForGlobalOperation(_, _, _, _, _, _, _)
      // No certificateMap calls should happen — neither set nor clear.
      0 * targetHttpsProxies.setCertificateMap(_, _, _)
      0 * computeMock.targetHttpProxies()
      0 * computeMock.globalOperations()
  }

  void "https target proxy update helper supports cert and certificateMap migrations"() {
    expect:
      UpsertGoogleHttpLoadBalancerAtomicOperation.shouldUpdateHttpsTargetProxy(
        "legacy-cert",
        null,
        null,
        "my-map"
      )
      UpsertGoogleHttpLoadBalancerAtomicOperation.shouldUpdateHttpsTargetProxy(
        null,
        "my-map",
        "legacy-cert",
        null
      )
      !UpsertGoogleHttpLoadBalancerAtomicOperation.shouldUpdateHttpsTargetProxy(
        null,
        "my-map",
        null,
        "my-map"
      )
  }

  void "certificateMap clear helper only clears during map to cert migration"() {
    expect:
      UpsertGoogleHttpLoadBalancerAtomicOperation.shouldClearCertificateMap("my-map", null)
      !UpsertGoogleHttpLoadBalancerAtomicOperation.shouldClearCertificateMap(null, null)
      !UpsertGoogleHttpLoadBalancerAtomicOperation.shouldClearCertificateMap(null, "my-map")
      !UpsertGoogleHttpLoadBalancerAtomicOperation.shouldClearCertificateMap("my-map", "my-map")
  }

  void "certificateMap helper normalizes both name and URL values"() {
    expect:
      UpsertGoogleHttpLoadBalancerAtomicOperation.getCertificateMapName("my-map") == "my-map"
      UpsertGoogleHttpLoadBalancerAtomicOperation.getCertificateMapName(
        "//certificatemanager.googleapis.com/projects/my-project/locations/global/certificateMaps/my-map"
      ) == "my-map"
  }
}
