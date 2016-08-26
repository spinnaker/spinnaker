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
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.converters.UpsertGoogleLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.CreateGoogleHttpLoadBalancerTestConstants.*

class CreateGoogleHttpLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final PROJECT_NAME = "my-project"
  private static final HEALTH_CHECK_OP_NAME = "health-check-op"
  private static final BACKEND_SERVICE_OP_NAME = "backend-service-op"
  private static final URL_MAP_OP_NAME = "url-map-op"
  private static final TARGET_HTTP_PROXY_OP_NAME = "target-http-proxy-op"
  private static final DONE = "DONE"

  @Shared GoogleHealthCheck hc

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    hc = [
        "name"              : "basic-check",
        "requestPath"       : "/",
        "port"              : 80,
        "checkIntervalSec"  : 1,
        "timeoutSec"        : 1,
        "healthyThreshold"  : 1,
        "unhealthyThreshold": 1
    ]
  }

  void "should create an HTTP Load Balancer with host rule, path matcher, path rules, etc"() {
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

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpProxyOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def healthCheckListReal = new HttpHealthCheckList(items: [])
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new Operation(
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
      def insertOp = new Operation(targetLink: "link")

      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
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
                  ]
                ]
              ],
              "defaultService": [
                "name"       : DEFAULT_PM_SERVICE,
                "backends"   : [],
                "healthCheck": hc,
              ]
            ]
          ]
        ]
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new CreateGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
     operation.operate([])

    then:
      2 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal
      1 * httpHealthChecks.insert(PROJECT_NAME, _) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp

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

      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME, _) >> globalForwardingRulesInsert
      1 * globalForwardingRulesInsert.execute() >> insertOp

      6 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> httpHealthChecksInsertOp
      3 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      3 * globalBackendServiceOperationGet.execute() >> httpHealthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_OP_NAME) >> globalUrlMapOperationGet
      1 * globalUrlMapOperationGet.execute() >> httpHealthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_OP_NAME) >> globalTargetHttpProxyOperationGet
      1 * globalTargetHttpProxyOperationGet.execute() >> httpHealthChecksInsertOp
  }

  void "should create an HTTP Load Balancer with minimal description"() {
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

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpProxyOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def healthCheckListReal = new HttpHealthCheckList(items: [])
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new Operation(
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
      def insertOp = new Operation(targetLink: "link")
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
        ],
        "certificate"     : "",
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new CreateGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal
      1 * httpHealthChecks.insert(PROJECT_NAME, _) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp

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

      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME, _) >> globalForwardingRulesInsert
      1 * globalForwardingRulesInsert.execute() >> insertOp

      4 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> httpHealthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      1 * globalBackendServiceOperationGet.execute() >> httpHealthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_OP_NAME) >> globalUrlMapOperationGet
      1 * globalUrlMapOperationGet.execute() >> httpHealthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_OP_NAME) >> globalTargetHttpProxyOperationGet
      1 * globalTargetHttpProxyOperationGet.execute() >> httpHealthChecksInsertOp
  }

  void "should create an HTTPS Load Balancer when certificate specified"() {
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

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpsProxyOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def healthCheckListReal = new HttpHealthCheckList(items: [])
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new Operation(
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
      def insertOp = new Operation(targetLink: "link")
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "certificate"     : "my-cert",
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
        ],
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new CreateGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal
      1 * httpHealthChecks.insert(PROJECT_NAME, _) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp

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

      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME, _) >> globalForwardingRulesInsert
      1 * globalForwardingRulesInsert.execute() >> insertOp

      4 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> httpHealthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      1 * globalBackendServiceOperationGet.execute() >> httpHealthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_OP_NAME) >> globalUrlMapOperationGet
      1 * globalUrlMapOperationGet.execute() >> httpHealthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_OP_NAME) >> globalTargetHttpsProxyOperationGet
      1 * globalTargetHttpsProxyOperationGet.execute() >> httpHealthChecksInsertOp
  }

  void "should fail when health check exists"() {
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

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def healthCheckListReal = new HttpHealthCheckList(items: [new HttpHealthCheck(name: "$LOAD_BALANCER_NAME-hc-basic-check")])
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
        ],
        "certificate"     : "",
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new CreateGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal
      thrown GoogleOperationException
  }

  void "should fail if backend service exists"() {
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

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def healthCheckListReal = new HttpHealthCheckList(items: [])
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)

      def backendServices = Mock(Compute.BackendServices)
      def backendServicesList = Mock(Compute.BackendServices.List)
      def bsListReal = new BackendServiceList(items: [new BackendService(name: "$LOAD_BALANCER_NAME-bs-$DEFAULT_SERVICE")])
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
        ],
        "certificate"     : "",
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new CreateGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal
      1 * httpHealthChecks.insert(PROJECT_NAME, _) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp

      1 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal

      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> httpHealthChecksInsertOp
      thrown GoogleOperationException
  }

  void "should fail if url map exists"() {
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

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)

      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksList = Mock(Compute.HttpHealthChecks.List)
      def healthCheckListReal = new HttpHealthCheckList(items: [])
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new Operation(
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
      def urlMapReal = new UrlMap(name: "totally-real-url-map")
      def input = [
        accountName       : ACCOUNT_NAME,
        "loadBalancerName": LOAD_BALANCER_NAME,
        "portRange"       : PORT_RANGE,
        "defaultService"  : [
          "name"       : DEFAULT_SERVICE,
          "backends"   : [],
          "healthCheck": hc,
        ],
        "certificate"     : "",
        "hostRules"       : null,
      ]
      def description = converter.convertDescription(input)
      @Subject def operation = new CreateGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      2 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.list(PROJECT_NAME) >> httpHealthChecksList
      1 * httpHealthChecksList.execute() >> healthCheckListReal
      1 * httpHealthChecks.insert(PROJECT_NAME, _) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp

      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.list(PROJECT_NAME) >> backendServicesList
      1 * backendServicesList.execute() >> bsListReal
      1 * backendServices.insert(PROJECT_NAME, _) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      1 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, description.loadBalancerName) >> urlMapsGet

      1 * urlMapsGet.execute() >> urlMapReal

      2 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_OP_NAME) >> globalHealthCheckOperationGet
      1 * globalHealthCheckOperationGet.execute() >> httpHealthChecksInsertOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_OP_NAME) >> globalBackendServiceOperationGet
      1 * globalBackendServiceOperationGet.execute() >> httpHealthChecksInsertOp
      thrown GoogleOperationException
  }
}
