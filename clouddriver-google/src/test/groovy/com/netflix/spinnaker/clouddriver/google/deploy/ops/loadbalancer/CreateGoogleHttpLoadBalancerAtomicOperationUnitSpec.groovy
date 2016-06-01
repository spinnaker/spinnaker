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

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Operation
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.CreateGoogleHttpLoadBalancerTestConstants.*

class CreateGoogleHttpLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final PROJECT_NAME = "my_project"
  private static final HEALTH_CHECK_OP_NAME = "health-check-op"
  private static final BACKEND_SERVICE_OP_NAME = "backend-service-op"
  private static final URL_MAP_OP_NAME = "url-map-op"
  private static final TARGET_HTTP_PROXY_OP_NAME = "target-http-proxy-op"
  private static final DONE = "DONE"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should create an HTTP Load Balancer with path matcher and backend"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpProxyOperationGet = Mock(Compute.GlobalOperations.Get)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def backendServices = Mock(Compute.BackendServices)
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
          targetLink: "backend-service",
          name: BACKEND_SERVICE_OP_NAME,
          status: DONE)
      def urlMaps = Mock(Compute.UrlMaps)
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
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new CreateGoogleHttpLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          credentials: credentials,
          healthCheck: [checkIntervalSec: CHECK_INTERVAL_SEC],
          backends: [[group: INSTANCE_GROUP]],
          ipAddress: IP_ADDRESS,
          portRange: PORT_RANGE,
          hostRules: [[hosts: [HOST], pathMatcher: MATCHER]],
          pathMatchers: [[name: MATCHER, defaultService: SERVICE, pathRules: [[paths: [PATH], service: SERVICE]]]]
      )
      @Subject def operation = new CreateGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
     operation.operate([])

    then:
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.insert(PROJECT_NAME, {it.checkIntervalSec == CHECK_INTERVAL_SEC && it.port == null}) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp

      1 * computeMock.backendServices() >> backendServices
      1 * backendServices.insert(PROJECT_NAME,
          {it.backends.size() == 1 && it.backends.get(0).group == INSTANCE_GROUP && it.healthChecks.size() == 1 &&
           it.healthChecks.get(0) == httpHealthChecksInsertOp.targetLink}) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp

      1 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.insert(PROJECT_NAME,
          {it.pathMatchers.size() == 1 && it.pathMatchers.get(0).defaultService == SERVICE &&
           it.pathMatchers.get(0).pathRules.size() == 1 && it.pathMatchers.get(0).pathRules.get(0).service == SERVICE &&
           it.pathMatchers.get(0).pathRules.get(0).paths.size() == 1 && it.pathMatchers.get(0).pathRules.get(0).paths.get(0) == PATH &&
           it.defaultService == backendServicesInsertOp.targetLink}) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp

      1 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp
      1 * computeMock.globalForwardingRules() >> globalForwardingRules

      1 * globalForwardingRules.insert(PROJECT_NAME,
          {it.iPAddress == IP_ADDRESS && it.portRange == PORT_RANGE && it.name == LOAD_BALANCER_NAME
           it.target == targetHttpProxiesInsertOp.targetLink}) >> globalForwardingRulesInsert
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

  void "should create an HTTP Load Balancer with minimal description"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def globalHealthCheckOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalBackendServiceOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalUrlMapOperationGet = Mock(Compute.GlobalOperations.Get)
      def globalTargetHttpProxyOperationGet = Mock(Compute.GlobalOperations.Get)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new Operation(
          targetLink: "health-check",
          name: HEALTH_CHECK_OP_NAME,
          status: DONE)
      def backendServices = Mock(Compute.BackendServices)
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new Operation(
          targetLink: "backend-service",
          name: BACKEND_SERVICE_OP_NAME,
          status: DONE)
      def urlMaps = Mock(Compute.UrlMaps)
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
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new CreateGoogleHttpLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          credentials: credentials,
      )
      @Subject def operation = new CreateGoogleHttpLoadBalancerAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.insert(PROJECT_NAME, {it.port == null}) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp
      1 * computeMock.backendServices() >> backendServices
      1 * backendServices.insert(PROJECT_NAME,
          {it.healthChecks.size() == 1 && it.healthChecks.get(0) == httpHealthChecksInsertOp.targetLink}) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp
      1 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.insert(PROJECT_NAME, {it.defaultService == backendServicesInsertOp.targetLink}) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp
      1 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp
      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME,
          {it.name == LOAD_BALANCER_NAME && it.target == targetHttpProxiesInsertOp.targetLink}) >> globalForwardingRulesInsert
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
}
