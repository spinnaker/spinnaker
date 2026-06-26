/*
 * Copyright 2016 Google, Inc.
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
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.*
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.ForwardingRuleCallbackHelper
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.GroupHealthRequest
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.HealthCheckHelper
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.LoadBalancerHealthResolution
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.PaginatedRequest
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory

@Slf4j
class GoogleInternalLoadBalancerCachingAgent extends AbstractGoogleLoadBalancerCachingAgent {

  /**
   * Local cache of BackendServiceGroupHealth keyed by BackendService name.
   *
   * It turns out that the types in the GCE Batch callbacks aren't the actual Compute
   * types for some reason, which is why this map is String -> Object.
   */
  Map<String, Object> bsNameToGroupHealthsMap = [:]
  Set<GroupHealthRequest> queuedBsGroupHealthRequests = new HashSet<>()
  Set<LoadBalancerHealthResolution> resolutions = new HashSet<>()

  GoogleInternalLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
                                         GoogleNamedAccountCredentials credentials,
                                         ObjectMapper objectMapper,
                                         Registry registry,
                                         String region) {
    super(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper,
          registry,
          region)
  }

  @Override
  Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    // Just let GoogleNetworkLoadBalancerCachingAgent return the pending regional on demand requests.
    []
  }

  @Override
  List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName = null) {
    List<GoogleInternalLoadBalancer> loadBalancers = []
    List<String> failedLoadBalancers = []

    GoogleBatchRequest forwardingRulesRequest = buildGoogleBatchRequest()
    GoogleBatchRequest groupHealthRequest = buildGoogleBatchRequest()

    // Reset the local getHealth caches/queues each caching agent cycle.
    bsNameToGroupHealthsMap = [:]
    queuedBsGroupHealthRequests = new HashSet<>()
    resolutions = new HashSet<>()

    List<BackendService> projectRegionBackendServices = GCEUtil.fetchRegionBackendServices(this, compute, project, region)
    List<HttpHealthCheck> projectHttpHealthChecks = GCEUtil.fetchHttpHealthChecks(this, compute, project)
    List<HttpsHealthCheck> projectHttpsHealthChecks = GCEUtil.fetchHttpsHealthChecks(this, compute, project)
    List<HealthCheck> projectHealthChecks = GCEUtil.fetchHealthChecks(this, compute, project)

    ForwardingRuleCallbacks forwardingRuleCallbacks = new ForwardingRuleCallbacks(
      loadBalancers: loadBalancers,
      failedLoadBalancers: failedLoadBalancers,
      groupHealthRequest: groupHealthRequest,
      projectRegionBackendServices: projectRegionBackendServices,
      projectHttpHealthChecks: projectHttpHealthChecks,
      projectHttpsHealthChecks: projectHttpsHealthChecks,
      projectHealthChecks: projectHealthChecks
    )

    if (onDemandLoadBalancerName) {
      def frCallback = forwardingRuleCallbacks.newForwardingRuleSingletonCallback()
      forwardingRulesRequest.queue(compute.forwardingRules().get(project, region, onDemandLoadBalancerName), frCallback)
    } else {
      def frlCallback = forwardingRuleCallbacks.newForwardingRuleListCallback()
      new PaginatedRequest<ForwardingRuleList>(this) {
        @Override
        ComputeRequest<ForwardingRuleList> request(String pageToken) {
          return compute.forwardingRules().list(project, region).setPageToken(pageToken)
        }

        @Override
        String getNextPageToken(ForwardingRuleList forwardingRuleList) {
          return forwardingRuleList.getNextPageToken()
        }
      }.queue(forwardingRulesRequest, frlCallback, "InternalLoadBalancerCaching.forwardingRules")
    }

    executeIfRequestsAreQueued(forwardingRulesRequest, "InternalLoadBalancerCaching.forwardingRules")
    executeIfRequestsAreQueued(groupHealthRequest, "InternalLoadBalancerCaching.groupHealth")

    resolutions.each { LoadBalancerHealthResolution resolution ->
      bsNameToGroupHealthsMap.get(resolution.getTarget()).each { groupHealth ->
        GCEUtil.handleHealthObject(resolution.getGoogleLoadBalancer(), groupHealth)
      }
    }

    return loadBalancers.findAll {!(it.name in failedLoadBalancers)}
  }

  class ForwardingRuleCallbacks extends ForwardingRuleCallbackHelper {
    List<GoogleInternalLoadBalancer> loadBalancers
    List<String> failedLoadBalancers = []

    // Pass through objects
    GoogleBatchRequest groupHealthRequest
    List<BackendService> projectRegionBackendServices
    List<HttpHealthCheck> projectHttpHealthChecks
    List<HttpsHealthCheck> projectHttpsHealthChecks
    List<HealthCheck> projectHealthChecks

    @Override
    protected boolean shouldProcessForwardingRule(ForwardingRule forwardingRule) {
      return forwardingRule.backendService != null
    }

    @Override
    protected String getFilterErrorMessage() {
      return "Not responsible for on demand caching of load balancers without backend services."
    }

    @Override
    protected void processForwardingRule(ForwardingRule forwardingRule) {
      def newLoadBalancer = new GoogleInternalLoadBalancer(
        name: forwardingRule.name,
        account: accountName,
        region: Utils.getLocalName(forwardingRule.getRegion()),
        createdTime: Utils.getTimeFromTimestamp(forwardingRule.creationTimestamp),
        ipAddress: forwardingRule.IPAddress,
        ipProtocol: forwardingRule.IPProtocol,
        ports: forwardingRule.ports,
        loadBalancingScheme: GoogleLoadBalancingScheme.valueOf(forwardingRule.getLoadBalancingScheme()),
        network: forwardingRule.getNetwork(),
        subnet: forwardingRule.getSubnetwork(),
        healths: [],
      )
      loadBalancers << newLoadBalancer

      def backendServiceName = Utils.getLocalName(forwardingRule.backendService)
      BackendService backendService = projectRegionBackendServices?.find { BackendService bs -> bs.getName() == backendServiceName }
      if (backendService == null) {
        log.warn("Failed to read a component of subject ${newLoadBalancer.name}. Could not find BackendService ${backendServiceName}.\n"
          + "Reporting it as 'Failed' to the caching agent.")
        failedLoadBalancers << newLoadBalancer.name
      } else {
        handleBackendService(backendService, newLoadBalancer, projectHttpHealthChecks, projectHttpsHealthChecks, projectHealthChecks, groupHealthRequest)
      }
    }
  }

  private void handleBackendService(BackendService backendService,
                                    GoogleInternalLoadBalancer googleLoadBalancer,
                                    List<HttpHealthCheck> httpHealthChecks,
                                    List<HttpsHealthCheck> httpsHealthChecks,
                                    List<HealthCheck> healthChecks,
                                    GoogleBatchRequest groupHealthRequest) {
    def groupHealthCallback = new GroupHealthCallback(backendServiceName: backendService.name)

    GoogleBackendService newService = new GoogleBackendService(
      name: backendService.name,
      loadBalancingScheme: backendService.loadBalancingScheme,
      sessionAffinity: backendService.sessionAffinity,
      backends: backendService.backends?.findAll { Backend backend -> backend.group }?.collect { Backend backend ->
        new GoogleLoadBalancedBackend(
          serverGroupUrl: backend.group,
          policy: new GoogleLoadBalancingPolicy(balancingMode: backend.balancingMode)
        )
      } ?: []
    )
    googleLoadBalancer.backendService = newService

    backendService.backends?.findAll { Backend backend -> backend.group }?.each { Backend backend ->
      def resourceGroup = new ResourceGroupReference()
      resourceGroup.setGroup(backend.group as String)


      // Make only the group health request calls we need to.
      GroupHealthRequest ghr = new GroupHealthRequest(project, backendService.name as String, resourceGroup.getGroup())
      if (!queuedBsGroupHealthRequests.contains(ghr)) {
        // The groupHealthCallback updates the local cache.
        log.debug("Queueing a batch call for getHealth(): {}", ghr)
        queuedBsGroupHealthRequests.add(ghr)
        groupHealthRequest
          .queue(compute.regionBackendServices().getHealth(project, region, backendService.name as String, resourceGroup),
          groupHealthCallback)
      } else {
        log.debug("Passing, batch call result cached for getHealth(): {}", ghr)
      }
      resolutions.add(new LoadBalancerHealthResolution(googleLoadBalancer, backendService.name))
    }

    backendService.healthChecks?.each { String healthCheckURL ->
      def healthCheckName = Utils.getLocalName(healthCheckURL)
      def healthCheckType = Utils.getHealthCheckType(healthCheckURL)
      switch (healthCheckType) {
        case "httpHealthChecks":
          HttpHealthCheck httpHealthCheck = httpHealthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          HealthCheckHelper.handleHttpHealthCheck(httpHealthCheck, googleLoadBalancer.backendService)
          break
        case "httpsHealthChecks":
          HttpsHealthCheck httpsHealthCheck = httpsHealthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          HealthCheckHelper.handleHttpsHealthCheck(httpsHealthCheck, googleLoadBalancer.backendService)
          break
        case "healthChecks":
          HealthCheck healthCheck = healthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          HealthCheckHelper.handleHealthCheck(healthCheck, googleLoadBalancer.backendService)
          break
        default:
          log.warn("Unknown health check type for health check named: ${healthCheckName}. Not queueing any batch requests.")
          break
      }
    }
  }


  class GroupHealthCallback<BackendServiceGroupHealth> extends JsonBatchCallback<BackendServiceGroupHealth> {
    String backendServiceName

    /**
     * Tolerate of the group health calls failing. Spinnaker reports empty load balancer healths as 'unknown'.
     * If healthStatus is null in the onSuccess() function, the same state is reported, so this shouldn't cause issues.
     */
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.debug("Failed backend service group health call for backend service ${backendServiceName} for Internal load balancer." +
        " The platform error message was:\n ${e.getMessage()}.")
    }

    @Override
    void onSuccess(BackendServiceGroupHealth backendServiceGroupHealth, HttpHeaders responseHeaders) throws IOException {
      if (!bsNameToGroupHealthsMap.containsKey(backendServiceName)) {
        bsNameToGroupHealthsMap.put(backendServiceName, [backendServiceGroupHealth])
      } else {
        bsNameToGroupHealthsMap.get(backendServiceName) << backendServiceGroupHealth
      }
    }
  }
}
