/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DeleteGoogleExternalHttpLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final REGION = "us-central1"
  private static final LOAD_BALANCER_NAME = "external-listener"
  private static final INTERNAL_LISTENER_NAME = "internal-listener"
  private static final TARGET_HTTP_PROXY_NAME = "target-http-proxy"
  private static final TARGET_HTTP_PROXY_URL =
    "projects/" + PROJECT_NAME + "/regions/" + REGION + "/targetHttpProxies/" + TARGET_HTTP_PROXY_NAME
  private static final INTERNAL_TARGET_HTTP_PROXY_URL =
    "projects/" + PROJECT_NAME + "/regions/" + REGION + "/targetHttpProxies/internal-target-http-proxy"
  private static final URL_MAP_NAME = "shared-url-map"
  private static final URL_MAP_URL =
    "projects/" + PROJECT_NAME + "/regions/" + REGION + "/urlMaps/" + URL_MAP_NAME
  private static final BACKEND_SERVICE_NAME = "backend-service"
  private static final BACKEND_SERVICE_URL =
    "projects/" + PROJECT_NAME + "/regions/" + REGION + "/backendServices/" + BACKEND_SERVICE_NAME
  private static final HEALTH_CHECK_NAME = "health-check"
  private static final HEALTH_CHECK_URL =
    "projects/" + PROJECT_NAME + "/regions/" + REGION + "/healthChecks/" + HEALTH_CHECK_NAME

  @Shared
  SafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = SafeRetry.withoutDelay()
  }

  void "uses EXTERNAL_MANAGED load balancing scheme"() {
    setup:
      @Subject def operation = new DeleteGoogleExternalHttpLoadBalancerAtomicOperation(
        new DeleteGoogleLoadBalancerDescription())

    expect:
      operation.loadBalancingScheme == "EXTERNAL_MANAGED"
  }

  void "deletes only external managed listeners for a shared regional url map"() {
    setup:
      def compute = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def forwardingRulesDelete = Mock(Compute.ForwardingRules.Delete)
      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.RegionTargetHttpProxies.Get)
      def internalTargetHttpProxiesGet = Mock(Compute.RegionTargetHttpProxies.Get)
      def targetHttpProxiesDelete = Mock(Compute.RegionTargetHttpProxies.Delete)
      def urlMaps = Mock(Compute.RegionUrlMaps)
      def urlMapsList = Mock(Compute.RegionUrlMaps.List)
      def urlMapsDelete = Mock(Compute.RegionUrlMaps.Delete)
      def backendServices = Mock(Compute.RegionBackendServices)
      def backendServicesGet = Mock(Compute.RegionBackendServices.Get)
      def backendServicesDelete = Mock(Compute.RegionBackendServices.Delete)
      def healthChecks = Mock(Compute.RegionHealthChecks)
      def healthChecksDelete = Mock(Compute.RegionHealthChecks.Delete)
      def poller = Mock(GoogleOperationPoller)
      def operationResult = new Operation(name: "operation", status: "DONE")
      def forwardingRule = new ForwardingRule(
        name: LOAD_BALANCER_NAME,
        target: TARGET_HTTP_PROXY_URL,
        loadBalancingScheme: "EXTERNAL_MANAGED")
      def internalForwardingRule = new ForwardingRule(
        name: INTERNAL_LISTENER_NAME,
        target: INTERNAL_TARGET_HTTP_PROXY_URL,
        loadBalancingScheme: "INTERNAL_MANAGED")
      def unrelatedL4Rule = new ForwardingRule(
        name: "unrelated-l4",
        backendService: "projects/" + PROJECT_NAME + "/regions/" + REGION + "/backendServices/l4-backend",
        loadBalancingScheme: "INTERNAL")
      def targetHttpProxy = new TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMap = new UrlMap(name: URL_MAP_NAME, defaultService: BACKEND_SERVICE_URL)
      def backendService = new BackendService(healthChecks: [HEALTH_CHECK_URL], backends: [])
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(compute).build()
      def description = new DeleteGoogleLoadBalancerDescription(
        loadBalancerName: LOAD_BALANCER_NAME,
        region: REGION,
        accountName: ACCOUNT_NAME,
        credentials: credentials,
        deleteHealthChecks: true)
      @Subject def operation = new DeleteGoogleExternalHttpLoadBalancerAtomicOperation(description)
      setPrivateField(operation, DeleteGoogleInternalHttpLoadBalancerAtomicOperation, "googleOperationPoller", poller)
      operation.registry = new DefaultRegistry()
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      _ * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, REGION) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> new ForwardingRuleList(items: [forwardingRule, internalForwardingRule, unrelatedL4Rule])
      1 * forwardingRules.get(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> forwardingRule
      1 * forwardingRules.delete(PROJECT_NAME, REGION, LOAD_BALANCER_NAME) >> forwardingRulesDelete
      1 * forwardingRulesDelete.execute() >> operationResult
      0 * forwardingRules.delete(PROJECT_NAME, REGION, INTERNAL_LISTENER_NAME)

      _ * compute.regionTargetHttpProxies() >> targetHttpProxies
      2 * targetHttpProxies.get(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      2 * targetHttpProxiesGet.execute() >> targetHttpProxy
      1 * targetHttpProxies.get(PROJECT_NAME, REGION, "internal-target-http-proxy") >> internalTargetHttpProxiesGet
      1 * internalTargetHttpProxiesGet.execute() >> targetHttpProxy
      1 * targetHttpProxies.delete(PROJECT_NAME, REGION, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> operationResult

      _ * compute.regionUrlMaps() >> urlMaps
      1 * urlMaps.list(PROJECT_NAME, REGION) >> urlMapsList
      1 * urlMapsList.execute() >> new UrlMapList(items: [urlMap])
      0 * urlMaps.delete(PROJECT_NAME, REGION, URL_MAP_NAME)
      _ * compute.regionBackendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService
      0 * backendServices.delete(PROJECT_NAME, REGION, BACKEND_SERVICE_NAME)
      0 * healthChecks.delete(PROJECT_NAME, REGION, HEALTH_CHECK_NAME)
      1 * poller.waitForRegionalOperation(*_)
  }

  private static void setPrivateField(Object target, Class owner, String fieldName, Object value) {
    def field = owner.getDeclaredField(fieldName)
    field.accessible = true
    field.set(target, value)
  }
}
