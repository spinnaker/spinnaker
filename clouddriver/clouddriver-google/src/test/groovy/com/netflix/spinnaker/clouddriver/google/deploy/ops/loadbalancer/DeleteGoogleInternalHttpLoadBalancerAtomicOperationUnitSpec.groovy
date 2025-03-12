/*
 * Copyright 2015 Google, Inc.
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
import com.google.api.services.compute.model.*
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationTimedOutException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DeleteGoogleInternalHttpLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final BASE_PHASE = "test-phase"
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final HTTP_LOAD_BALANCER_NAME = "default"
  private static final URL_MAP_NAME = "url-map"
  private static final REGION = "us-central1"
  private static final TARGET_HTTP_PROXY_URL = "projects/$PROJECT_NAME/global/targetHttpProxies/target-http-proxy"
  private static final TARGET_HTTP_PROXY_NAME = "target-http-proxy"
  private static final URL_MAP_URL = "project/url-map"
  private static final BACKEND_SERVICE_URL = "project/backend-service"
  private static final BACKEND_SERVICE_NAME = "backend-service"
  private static final HEALTH_CHECK_URL = "project/health-check"
  private static final HEALTH_CHECK_NAME = "health-check"
  private static final FORWARDING_RULE_DELETE_OP_NAME = "delete-forwarding-rule"
  private static final TARGET_HTTP_PROXY_DELETE_OP_NAME = "delete-target-http-proxy"
  private static final URL_MAP_DELETE_OP_NAME = "delete-url-map"
  private static final BACKEND_SERVICE_DELETE_OP_NAME = "delete-backend-service"
  private static final HEALTH_CHECK_DELETE_OP_NAME = "delete-health-check"
  private static final PENDING = "PENDING"
  private static final DONE = "DONE"

  @Shared
  def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
  @Shared
  def registry = new DefaultRegistry()
  @Shared
  SafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = SafeRetry.withoutDelay()
  }

  void "should delete Internal Http Load Balancer with one backend service"() {
    setup:
      def computeMock = Mock(Compute)
      def regionForwardingRules = Mock(Compute.ForwardingRules)
      def regionForwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def regionForwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRule = new ForwardingRule(target: TARGET_HTTP_PROXY_URL, name: HTTP_LOAD_BALANCER_NAME, loadBalancingScheme: "INTERNAL_MANAGED")
      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.RegionTargetHttpProxies.Get)
      def targetHttpProxy = new TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsList = Mock(Compute.RegionUrlMaps.List)
      def urlMap = new UrlMap(defaultService: BACKEND_SERVICE_URL, name: URL_MAP_NAME)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.RegionHealthChecks)

      def regionForwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def regionForwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: DONE)
      def targetHttpProxiesDelete = Mock(Compute.RegionTargetHttpProxies.Delete)
      def targetHttpProxiesDeleteOp = new Operation(
          name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
          status: DONE)
      def urlMapsDelete = Mock(Compute.RegionUrlMaps.Delete)
      def urlMapsDeleteOp = new Operation(
          name: URL_MAP_DELETE_OP_NAME,
          status: DONE)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME,
          status: DONE)
      def healthChecksDelete = Mock(Compute.RegionHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: DONE)

      def regionOperations = Mock(Compute.RegionOperations)
      def targetHttpProxiesOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapsOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServicesOperationGet = Mock(Compute.RegionOperations.Get)
      def healthChecksOperationGet = Mock(Compute.RegionOperations.Get)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalHttpLoadBalancerAtomicOperation(description)
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
      3 * computeMock.forwardingRules() >> regionForwardingRules
      1 * regionForwardingRules.list(PROJECT_NAME, REGION) >> regionForwardingRulesList
      1 * regionForwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * regionForwardingRules.get(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesGet
      1 * regionForwardingRulesGet.execute() >> forwardingRule
      3 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      2 * targetHttpProxies.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      2 * targetHttpProxiesGet.execute() >> targetHttpProxy
      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.list(PROJECT_NAME, REGION) >> urlMapsList
      1 * urlMapsList.execute() >> new UrlMapList(items: [urlMap])
      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService

      1 * regionForwardingRules.delete(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesDelete
      1 * regionForwardingRulesDelete.execute() >> regionForwardingRulesDeleteOp
      1 * targetHttpProxies.delete(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> targetHttpProxiesDeleteOp
      1 * urlMaps.delete(PROJECT_NAME, REGION, URL_MAP_NAME) >> urlMapsDelete
      1 * urlMapsDelete.execute() >> urlMapsDeleteOp
      1 * backendServices.delete(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp
      1 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.delete(PROJECT_NAME, REGION, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp

      4 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_DELETE_OP_NAME) >> targetHttpProxiesOperationGet
      1 * targetHttpProxiesOperationGet.execute() >> targetHttpProxiesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_DELETE_OP_NAME) >> urlMapsOperationGet
      1 * urlMapsOperationGet.execute() >> urlMapsDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_DELETE_OP_NAME) >> backendServicesOperationGet
      1 * backendServicesOperationGet.execute() >> backendServicesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_DELETE_OP_NAME) >> healthChecksOperationGet
      1 * healthChecksOperationGet.execute() >> healthChecksDeleteOp
  }

  void "should delete Internal Http Load Balancer with multiple backend services/health checks"() {
    setup:
      def computeMock = Mock(Compute)
      def regionForwardingRules = Mock(Compute.ForwardingRules)
      def regionForwardingRulesList = Mock(Compute.ForwardingRules.List)
      def regionForwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRule = new ForwardingRule(target: TARGET_HTTP_PROXY_URL, name: HTTP_LOAD_BALANCER_NAME, loadBalancingScheme: "INTERNAL_MANAGED")
      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.RegionTargetHttpProxies.Get)
      def targetHttpProxy = new TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsList = Mock(Compute.RegionUrlMaps.List)
      def urlMap = new UrlMap(
        name: URL_MAP_NAME,
        defaultService: BACKEND_SERVICE_URL,
        pathMatchers: [
          new PathMatcher(defaultService: BACKEND_SERVICE_URL + "2",
            pathRules: [
              new PathRule(service: BACKEND_SERVICE_URL + "3"), new PathRule(service: BACKEND_SERVICE_URL)
            ]
          )
        ])
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesGet2 = Mock(Compute.RegionBackendServices.Get)
      def backendServicesGet3 = Mock(Compute.RegionBackendServices.Get)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL])
      def backendService2 = new BackendService(healthChecks: [HEALTH_CHECK_URL+"2"])
      def backendService3 = new BackendService(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.RegionHealthChecks)

      def regionForwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def regionForwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: DONE)
      def targetHttpProxiesDelete = Mock(Compute.RegionTargetHttpProxies.Delete)
      def targetHttpProxiesDeleteOp = new Operation(
          name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
          status: DONE)
      def urlMapsDelete = Mock(Compute.RegionUrlMaps.Delete)
      def urlMapsDeleteOp = new Operation(
          name: URL_MAP_DELETE_OP_NAME,
          status: DONE)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME,
          status: DONE)
      def backendServicesDelete2 = Mock(Compute.RegionBackendServices.Delete)
      def backendServicesDeleteOp2 = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME+"2",
          status: DONE)
      def backendServicesDelete3 = Mock(Compute.RegionBackendServices.Delete)
      def backendServicesDeleteOp3 = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME+"3",
          status: DONE)
      def healthChecksDelete = Mock(Compute.RegionHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: DONE)
      def healthChecksDelete2 = Mock(Compute.RegionHealthChecks.Delete)
      def healthChecksDeleteOp2 = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME+"2",
          status: DONE)

      def regionOperations = Mock(Compute.RegionOperations)
      def targetHttpProxiesOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapsOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServicesOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServicesOperationGet2 = Mock(Compute.RegionOperations.Get)
      def backendServicesOperationGet3 = Mock(Compute.RegionOperations.Get)
      def healthChecksOperationGet = Mock(Compute.RegionOperations.Get)
      def healthChecksOperationGet2 = Mock(Compute.RegionOperations.Get)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalHttpLoadBalancerAtomicOperation(description)
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
      3 * computeMock.forwardingRules() >> regionForwardingRules
      1 * regionForwardingRules.list(PROJECT_NAME, REGION) >> regionForwardingRulesList
      1 * regionForwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * regionForwardingRules.get(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesGet
      1 * regionForwardingRulesGet.execute() >> forwardingRule
      3 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      2 * targetHttpProxies.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      2 * targetHttpProxiesGet.execute() >> targetHttpProxy
      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.list(PROJECT_NAME, REGION) >> urlMapsList
      1 * urlMapsList.execute() >> new UrlMapList(items: [urlMap])

      6 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService
      1 * backendServices.get(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME+"2") >> backendServicesGet2
      1 * backendServicesGet2.execute() >> backendService2
      1 * backendServices.get(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME+"3") >> backendServicesGet3
      1 * backendServicesGet3.execute() >> backendService3

      1 * regionForwardingRules.delete(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesDelete
      1 * regionForwardingRulesDelete.execute() >> regionForwardingRulesDeleteOp
      1 * targetHttpProxies.delete(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> targetHttpProxiesDeleteOp
      1 * urlMaps.delete(PROJECT_NAME, REGION, URL_MAP_NAME) >> urlMapsDelete
      1 * urlMapsDelete.execute() >> urlMapsDeleteOp

      1 * backendServices.delete(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp
      1 * backendServices.delete(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME+"2") >> backendServicesDelete2
      1 * backendServicesDelete2.execute() >> backendServicesDeleteOp2
      1 * backendServices.delete(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME+"3") >> backendServicesDelete3
      1 * backendServicesDelete3.execute() >> backendServicesDeleteOp3
      2 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.delete(PROJECT_NAME, REGION, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp
      1 * healthChecks.delete(PROJECT_NAME, REGION, HEALTH_CHECK_NAME+"2") >> healthChecksDelete2
      1 * healthChecksDelete2.execute() >> healthChecksDeleteOp2

      7 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_DELETE_OP_NAME) >> targetHttpProxiesOperationGet
      1 * targetHttpProxiesOperationGet.execute() >> targetHttpProxiesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_DELETE_OP_NAME) >> urlMapsOperationGet
      1 * urlMapsOperationGet.execute() >> urlMapsDeleteOp

      1 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_DELETE_OP_NAME) >> backendServicesOperationGet
      1 * backendServicesOperationGet.execute() >> backendServicesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_DELETE_OP_NAME+"2") >> backendServicesOperationGet2
      1 * backendServicesOperationGet2.execute() >> backendServicesDeleteOp2
      1 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_DELETE_OP_NAME+"3") >> backendServicesOperationGet3
      1 * backendServicesOperationGet3.execute() >> backendServicesDeleteOp3
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_DELETE_OP_NAME) >> healthChecksOperationGet
      1 * healthChecksOperationGet.execute() >> healthChecksDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_DELETE_OP_NAME+"2") >> healthChecksOperationGet2
      1 * healthChecksOperationGet2.execute() >> healthChecksDeleteOp2
  }

  void "should fail to delete an Internal Http Load Balancer that does not exist"() {
    setup:
      def computeMock = Mock(Compute)
      def regionForwardingRules = Mock(Compute.ForwardingRules)
      def regionForwardingRulesList = Mock(Compute.ForwardingRules.List)
      def regionForwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalHttpLoadBalancerAtomicOperation(description)
      operation.registry = registry

    when:
      operation.operate([])

    then:
      1 * computeMock.forwardingRules() >> regionForwardingRules
      1 * regionForwardingRules.list(PROJECT_NAME, REGION) >> regionForwardingRulesList
      1 * regionForwardingRulesList.execute() >> [items: []]
      thrown GoogleResourceNotFoundException
  }

  void "should fail to delete Internal Http Load Balancer if failed to delete a resource"() {
    setup:
      def computeMock = Mock(Compute)
      def regionForwardingRules = Mock(Compute.ForwardingRules)
      def regionForwardingRulesList = Mock(Compute.ForwardingRules.List)
      def regionForwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRule = new ForwardingRule(target: TARGET_HTTP_PROXY_URL, name: HTTP_LOAD_BALANCER_NAME, loadBalancingScheme: "INTERNAL_MANAGED")
      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.RegionTargetHttpProxies.Get)
      def targetHttpProxy = new TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsList = Mock(Compute.RegionUrlMaps.List)
      def urlMap = new UrlMap(defaultService: BACKEND_SERVICE_URL, name: URL_MAP_NAME)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.RegionHealthChecks)

      def regionForwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def regionForwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: DONE)
      def targetHttpProxiesDelete = Mock(Compute.RegionTargetHttpProxies.Delete)
      def targetHttpProxiesDeleteOp = new Operation(
          name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
          status: DONE)
      def urlMapsDelete = Mock(Compute.RegionUrlMaps.Delete)
      def urlMapsDeleteOp = new Operation(
          name: URL_MAP_DELETE_OP_NAME,
          status: DONE)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME,
          status: DONE)
      def healthChecksDelete = Mock(Compute.RegionHealthChecks.Delete)
      def healthChecksPendingDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: PENDING)
      def healthChecksFailingDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: DONE,
          error: new Operation.Error(errors: [new Operation.Error.Errors(message: "error")]))

      def regionOperations = Mock(Compute.RegionOperations)
      def targetHttpProxiesOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapsOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServicesOperationGet = Mock(Compute.RegionOperations.Get)
      def healthChecksOperationGet = Mock(Compute.RegionOperations.Get)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalHttpLoadBalancerAtomicOperation(description)
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
      3 * computeMock.forwardingRules() >> regionForwardingRules
      1 * regionForwardingRules.list(PROJECT_NAME, REGION) >> regionForwardingRulesList
      1 * regionForwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * regionForwardingRules.get(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesGet
      1 * regionForwardingRulesGet.execute() >> forwardingRule
      3 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      2 * targetHttpProxies.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      2 * targetHttpProxiesGet.execute() >> targetHttpProxy
      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.list(PROJECT_NAME, REGION) >> urlMapsList
      1 * urlMapsList.execute() >> new UrlMapList(items: [urlMap])
      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService

      1 * regionForwardingRules.delete(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesDelete
      1 * regionForwardingRulesDelete.execute() >> regionForwardingRulesDeleteOp
      1 * targetHttpProxies.delete(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> targetHttpProxiesDeleteOp
      1 * urlMaps.delete(PROJECT_NAME, REGION, URL_MAP_NAME) >> urlMapsDelete
      1 * urlMapsDelete.execute() >> urlMapsDeleteOp
      1 * backendServices.delete(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp
      1 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.delete(PROJECT_NAME, REGION, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksPendingDeleteOp

      4 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_DELETE_OP_NAME) >> targetHttpProxiesOperationGet
      1 * targetHttpProxiesOperationGet.execute() >> targetHttpProxiesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_DELETE_OP_NAME) >> urlMapsOperationGet
      1 * urlMapsOperationGet.execute() >> urlMapsDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_DELETE_OP_NAME) >> backendServicesOperationGet
      1 * backendServicesOperationGet.execute() >> backendServicesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_DELETE_OP_NAME) >> healthChecksOperationGet
      1 * healthChecksOperationGet.execute() >> healthChecksFailingDeleteOp
      thrown GoogleOperationException
  }

  void "should fail to delete Internal Http Load Balancer if timed out while deleting a resource"() {
    setup:
      def computeMock = Mock(Compute)
      def regionForwardingRules = Mock(Compute.ForwardingRules)
      def regionForwardingRulesList = Mock(Compute.ForwardingRules.List)
      def regionForwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def regionForwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def forwardingRule = new ForwardingRule(target: TARGET_HTTP_PROXY_URL, name: HTTP_LOAD_BALANCER_NAME, loadBalancingScheme: "INTERNAL_MANAGED")
      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.RegionTargetHttpProxies.Get)
      def targetHttpProxiesDelete = Mock(Compute.RegionTargetHttpProxies.Delete)
      def targetHttpProxy = new TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsList = Mock(Compute.RegionUrlMaps.List)
      def urlMap = new UrlMap(defaultService: BACKEND_SERVICE_URL, name: URL_MAP_NAME)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL])

      def regionOperations = Mock(Compute.RegionOperations)
      def targetHttpProxiesOperationGet = Mock(Compute.RegionOperations.Get)
      def targetHttpProxiesDeleteOp = new Operation(
        name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
        status: PENDING)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          deleteOperationTimeoutSeconds: 0,
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
     @Subject def operation = new DeleteGoogleInternalHttpLoadBalancerAtomicOperation(description)
     GCEUtil.deleteGlobalListener(computeMock, PROJECT_NAME, HTTP_LOAD_BALANCER_NAME, BASE_PHASE, safeRetry, operation) >> targetHttpProxiesDeleteOp
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
      3 * computeMock.forwardingRules() >> regionForwardingRules
      1 * regionForwardingRules.list(PROJECT_NAME, REGION) >> regionForwardingRulesList
      1 * regionForwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * regionForwardingRules.get(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesGet
      1 * regionForwardingRulesGet.execute() >> forwardingRule
      3 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      2 * targetHttpProxies.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      2 * targetHttpProxiesGet.execute() >> targetHttpProxy
      1 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.list(PROJECT_NAME, REGION) >> urlMapsList
      1 * urlMapsList.execute() >> new UrlMapList(items: [urlMap])
      1 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService

      1 * regionForwardingRules.delete(PROJECT_NAME, REGION, _) >> regionForwardingRulesDelete
      1 * regionForwardingRulesDelete.execute()
      1 * targetHttpProxies.delete(PROJECT_NAME, REGION, _) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> targetHttpProxiesDeleteOp

      1 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_DELETE_OP_NAME) >> targetHttpProxiesOperationGet
      1 *  targetHttpProxiesOperationGet.execute() >> targetHttpProxiesDeleteOp
      thrown GoogleOperationTimedOutException
  }

  void "should wait on slow deletion of target HTTP proxy and successfully delete simple HTTP Load Balancer"() {
    setup:
      def computeMock = Mock(Compute)
      def regionForwardingRules = Mock(Compute.ForwardingRules)
      def regionForwardingRulesList = Mock(Compute.ForwardingRules.List)
      def regionForwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRule = new ForwardingRule(target: TARGET_HTTP_PROXY_URL, name: HTTP_LOAD_BALANCER_NAME, loadBalancingScheme: "INTERNAL_MANAGED")
      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.RegionTargetHttpProxies.Get)
      def targetHttpProxy = new TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsList = Mock(Compute.RegionUrlMaps.List)
      def urlMap = new UrlMap(defaultService: BACKEND_SERVICE_URL, name: URL_MAP_NAME)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.RegionHealthChecks)

      def regionForwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def regionForwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: DONE)
      def targetHttpProxiesDelete = Mock(Compute.RegionTargetHttpProxies.Delete)
      def targetHttpProxiesDeleteOpPending = new Operation(
          name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
          status: PENDING)
      def targetHttpProxiesDeleteOpDone = new Operation(
          name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
          status: DONE)
      def urlMapsDelete = Mock(Compute.RegionUrlMaps.Delete)
      def urlMapsDeleteOp = new Operation(
          name: URL_MAP_DELETE_OP_NAME,
          status: DONE)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME,
          status: DONE)
      def healthChecksDelete = Mock(Compute.RegionHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: DONE)

      def regionOperations = Mock(Compute.RegionOperations)
      def targetHttpProxiesOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapsOperationGet = Mock(Compute.RegionOperations.Get)
      def backendServicesOperationGet = Mock(Compute.RegionOperations.Get)
      def healthChecksOperationGet = Mock(Compute.RegionOperations.Get)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalHttpLoadBalancerAtomicOperation(description)
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
      3 * computeMock.forwardingRules() >> regionForwardingRules
      1 * regionForwardingRules.list(PROJECT_NAME, REGION) >> regionForwardingRulesList
      1 * regionForwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * regionForwardingRules.get(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesGet
      1 * regionForwardingRulesGet.execute() >> forwardingRule
      3 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      2 * targetHttpProxies.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      2 * targetHttpProxiesGet.execute() >> targetHttpProxy
      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.list(PROJECT_NAME, REGION) >> urlMapsList
      1 * urlMapsList.execute() >> new UrlMapList(items: [urlMap])
      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService

      1 * regionForwardingRules.delete(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesDelete
      1 * regionForwardingRulesDelete.execute() >> regionForwardingRulesDeleteOp
      1 * targetHttpProxies.delete(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> targetHttpProxiesDeleteOpPending
      1 * urlMaps.delete(PROJECT_NAME, REGION, URL_MAP_NAME) >> urlMapsDelete
      1 * urlMapsDelete.execute() >> urlMapsDeleteOp
      1 * backendServices.delete(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp
      1 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.delete(PROJECT_NAME, REGION, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp

      6 * computeMock.regionOperations() >> regionOperations
      3 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_DELETE_OP_NAME) >> targetHttpProxiesOperationGet
      2 * targetHttpProxiesOperationGet.execute() >> targetHttpProxiesDeleteOpPending
      1 * targetHttpProxiesOperationGet.execute() >> targetHttpProxiesDeleteOpDone
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_DELETE_OP_NAME) >> urlMapsOperationGet
      1 * urlMapsOperationGet.execute() >> urlMapsDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, BACKEND_SERVICE_DELETE_OP_NAME) >> backendServicesOperationGet
      1 * backendServicesOperationGet.execute() >> backendServicesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_DELETE_OP_NAME) >> healthChecksOperationGet
      1 * healthChecksOperationGet.execute() >> healthChecksDeleteOp
  }

  void "should not delete backend service in more than one url map"() {
    setup:
      def computeMock = Mock(Compute)
      def regionForwardingRules = Mock(Compute.ForwardingRules)
      def regionForwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def regionForwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRule = new ForwardingRule(target: TARGET_HTTP_PROXY_URL, name: HTTP_LOAD_BALANCER_NAME, loadBalancingScheme: "INTERNAL_MANAGED")
      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.RegionTargetHttpProxies.Get)
      def targetHttpProxy = new TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsList = Mock(Compute.RegionUrlMaps.List)
      def urlMap = new UrlMap(defaultService: BACKEND_SERVICE_URL, name: URL_MAP_NAME)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.RegionHealthChecks)

      def regionForwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def regionForwardingRulesDeleteOp = new Operation(
        name: FORWARDING_RULE_DELETE_OP_NAME,
        status: DONE)
      def targetHttpProxiesDelete = Mock(Compute.RegionTargetHttpProxies.Delete)
      def targetHttpProxiesDeleteOp = new Operation(
        name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
        status: DONE)
      def urlMapsDelete = Mock(Compute.RegionUrlMaps.Delete)
      def urlMapsDeleteOp = new Operation(
        name: URL_MAP_DELETE_OP_NAME,
        status: DONE)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def healthChecksDelete = Mock(Compute.RegionHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
        name: HEALTH_CHECK_DELETE_OP_NAME,
        status: DONE)

      def regionOperations = Mock(Compute.RegionOperations)
      def targetHttpProxiesOperationGet = Mock(Compute.RegionOperations.Get)
      def urlMapsOperationGet = Mock(Compute.RegionOperations.Get)
      def healthChecksOperationGet = Mock(Compute.RegionOperations.Get)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          region: REGION,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      def conflictingMap = new UrlMap(defaultService: BACKEND_SERVICE_URL, name: "conflicting")
      @Subject def operation = new DeleteGoogleInternalHttpLoadBalancerAtomicOperation(description)
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
      3 * computeMock.forwardingRules() >> regionForwardingRules
      1 * regionForwardingRules.list(PROJECT_NAME, REGION) >> regionForwardingRulesList
      1 * regionForwardingRulesList.execute() >> [items: [forwardingRule]]
      1 * regionForwardingRules.get(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesGet
      1 * regionForwardingRulesGet.execute() >> forwardingRule
      3 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      2 * targetHttpProxies.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      2 * targetHttpProxiesGet.execute() >> targetHttpProxy
      2 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.list(PROJECT_NAME, REGION) >> urlMapsList
      1 * urlMapsList.execute() >> new UrlMapList(items: [urlMap, conflictingMap])
      2 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService

      1 * regionForwardingRules.delete(PROJECT_NAME, REGION, HTTP_LOAD_BALANCER_NAME) >> regionForwardingRulesDelete
      1 * regionForwardingRulesDelete.execute() >> regionForwardingRulesDeleteOp
      1 * targetHttpProxies.delete(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> targetHttpProxiesDeleteOp
      1 * urlMaps.delete(PROJECT_NAME, REGION, URL_MAP_NAME) >> urlMapsDelete
      1 * urlMapsDelete.execute() >> urlMapsDeleteOp
      1 * backendServices.delete(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> null
      1 * computeMock.regionHealthChecks() >> healthChecks
      1 * healthChecks.delete(PROJECT_NAME, REGION, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp

      3 * computeMock.regionOperations() >> regionOperations
      1 * regionOperations.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_DELETE_OP_NAME) >> targetHttpProxiesOperationGet
      1 * targetHttpProxiesOperationGet.execute() >> targetHttpProxiesDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, URL_MAP_DELETE_OP_NAME) >> urlMapsOperationGet
      1 * urlMapsOperationGet.execute() >> urlMapsDeleteOp
      1 * regionOperations.get(PROJECT_NAME, REGION, HEALTH_CHECK_DELETE_OP_NAME) >> healthChecksOperationGet
      1 * healthChecksOperationGet.execute() >> healthChecksDeleteOp
  }

  void "should fail if server group still associated"() {
    setup:
      def computeMock = Mock(Compute)
      def regionForwardingRules = Mock(Compute.ForwardingRules)
      def regionForwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRule = new ForwardingRule(target: TARGET_HTTP_PROXY_URL, name: HTTP_LOAD_BALANCER_NAME, loadBalancingScheme: "INTERNAL_MANAGED")
      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.RegionTargetHttpProxies.Get)
      def targetHttpProxy = new TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsList = Mock(Compute.RegionUrlMaps.List)
      def urlMap = new UrlMap(defaultService: BACKEND_SERVICE_URL, name: URL_MAP_NAME)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL], backends: [new Backend()])
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleInternalHttpLoadBalancerAtomicOperation(description)
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
      1 * computeMock.forwardingRules() >> regionForwardingRules
      1 * regionForwardingRules.list(PROJECT_NAME, REGION) >> regionForwardingRulesList
      1 * regionForwardingRulesList.execute() >> [items: [forwardingRule]]
      2 * computeMock.regionTargetHttpProxies() >> targetHttpProxies
      2 * targetHttpProxies.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      2 * targetHttpProxiesGet.execute() >> targetHttpProxy
      1 * computeMock.regionUrlMaps() >> urlMaps
      1 * urlMaps.list(PROJECT_NAME, REGION) >> urlMapsList
      1 * urlMapsList.execute() >> new UrlMapList(items: [urlMap])
      1 * computeMock.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService
      thrown IllegalStateException
  }
}
