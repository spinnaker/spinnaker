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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.*
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.GroupHealthRequest
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.LoadBalancerHealthResolution
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.PaginatedRequest
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory

/**
 * Caches regional external passthrough Network Load Balancers from GCP regional resources.
 *
 * <p>The resource graph starts at a regional forwarding rule with no target proxy, follows its
 * regional backend service, then attaches regional health checks and backend group health.
 */
@Slf4j
class GoogleRegionalExternalNetworkLoadBalancerCachingAgent extends AbstractGoogleLoadBalancerCachingAgent {

  Map<String, Object> bsNameToGroupHealthsMap = [:]
  Set<GroupHealthRequest> queuedBsGroupHealthRequests = new HashSet<>()
  Set<LoadBalancerHealthResolution> resolutions = new HashSet<>()

  GoogleRegionalExternalNetworkLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
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
    // Keep regional forwarding-rule on-demand reporting owned by the existing regional network agent.
    []
  }

  @Override
  List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName = null) {
    List<GoogleRegionalExternalNetworkLoadBalancer> loadBalancers = []
    List<String> failedLoadBalancers = []

    GoogleBatchRequest forwardingRulesRequest = buildGoogleBatchRequest()
    GoogleBatchRequest groupHealthRequest = buildGoogleBatchRequest()

    bsNameToGroupHealthsMap = [:]
    queuedBsGroupHealthRequests = new HashSet<>()
    resolutions = new HashSet<>()

    List<BackendService> projectRegionBackendServices = GCEUtil.fetchRegionBackendServices(this, compute, project, region)
    List<HealthCheck> projectRegionalHealthChecks = GCEUtil.fetchRegionalHealthChecks(this, compute, project, region)

    ForwardingRuleCallbacks forwardingRuleCallbacks = new ForwardingRuleCallbacks(
      loadBalancers: loadBalancers,
      failedLoadBalancers: failedLoadBalancers,
      groupHealthRequest: groupHealthRequest,
      projectRegionBackendServices: projectRegionBackendServices,
      projectRegionalHealthChecks: projectRegionalHealthChecks
    )

    if (onDemandLoadBalancerName) {
      ForwardingRuleCallbacks.ForwardingRuleSingletonCallback frCallback = forwardingRuleCallbacks.newForwardingRuleSingletonCallback()
      forwardingRulesRequest.queue(compute.forwardingRules().get(project, region, onDemandLoadBalancerName), frCallback)
    } else {
      ForwardingRuleCallbacks.ForwardingRuleListCallback frlCallback = forwardingRuleCallbacks.newForwardingRuleListCallback()
      new PaginatedRequest<ForwardingRuleList>(this) {
        @Override
        ComputeRequest<ForwardingRuleList> request(String pageToken) {
          return compute.forwardingRules().list(project, region).setPageToken(pageToken)
        }

        @Override
        String getNextPageToken(ForwardingRuleList forwardingRuleList) {
          return forwardingRuleList.getNextPageToken()
        }
      }.queue(forwardingRulesRequest, frlCallback, "RegionalExternalNetworkLoadBalancerCaching.forwardingRules")
    }

    executeIfRequestsAreQueued(forwardingRulesRequest, "RegionalExternalNetworkLoadBalancerCaching.forwardingRules")
    executeIfRequestsAreQueued(groupHealthRequest, "RegionalExternalNetworkLoadBalancerCaching.groupHealth")

    resolutions.each { LoadBalancerHealthResolution resolution ->
      (bsNameToGroupHealthsMap.get(resolution.getTarget()) ?: []).each { groupHealth ->
        GCEUtil.handleHealthObject(resolution.getGoogleLoadBalancer(), groupHealth)
      }
    }

    return loadBalancers.findAll { !(it.name in failedLoadBalancers) }
  }

  static boolean isRegionalExternalNetworkPassthroughRule(ForwardingRule forwardingRule) {
    // Regional external proxy LBs also use EXTERNAL forwarding rules, but they point at targets.
    // This agent only owns passthrough rules that point directly at a regional backend service.
    forwardingRule?.backendService &&
      !forwardingRule?.target &&
      forwardingRule?.loadBalancingScheme == "EXTERNAL" &&
      forwardingRule?.IPProtocol in ["TCP", "UDP"]
  }

  class ForwardingRuleCallbacks {
    List<GoogleRegionalExternalNetworkLoadBalancer> loadBalancers
    List<String> failedLoadBalancers = []

    GoogleBatchRequest groupHealthRequest
    List<BackendService> projectRegionBackendServices
    List<HealthCheck> projectRegionalHealthChecks

    ForwardingRuleSingletonCallback<ForwardingRule> newForwardingRuleSingletonCallback() {
      return new ForwardingRuleSingletonCallback<ForwardingRule>()
    }

    ForwardingRuleListCallback<ForwardingRuleList> newForwardingRuleListCallback() {
      return new ForwardingRuleListCallback<ForwardingRuleList>()
    }

    class ForwardingRuleSingletonCallback<ForwardingRule> extends JsonBatchCallback<ForwardingRule> {

      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        if (e.code != 404) {
          def errorJson = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e)
          log.error errorJson
        }
      }

      @Override
      void onSuccess(ForwardingRule forwardingRule, HttpHeaders responseHeaders) throws IOException {
        if (isRegionalExternalNetworkPassthroughRule(forwardingRule)) {
          cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
        } else {
          throw new IllegalArgumentException("Not responsible for on demand caching of non-EXTERNAL regional passthrough load balancers.")
        }
      }
    }

    class ForwardingRuleListCallback<ForwardingRuleList> extends JsonBatchCallback<ForwardingRuleList> implements FailureLogger {

      @Override
      void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
        forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
          if (isRegionalExternalNetworkPassthroughRule(forwardingRule)) {
            cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
          }
        }
      }

      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        LoggerFactory.getLogger(this.class).error e.getMessage()
      }
    }

    void cacheRemainderOfLoadBalancerResourceGraph(ForwardingRule forwardingRule) {
      def newLoadBalancer = new GoogleRegionalExternalNetworkLoadBalancer(
        name: forwardingRule.name,
        account: accountName,
        region: Utils.getLocalName(forwardingRule.getRegion()),
        createdTime: Utils.getTimeFromTimestamp(forwardingRule.creationTimestamp),
        ipAddress: forwardingRule.IPAddress,
        ipProtocol: forwardingRule.IPProtocol,
        ports: forwardingRule.ports,
        loadBalancingScheme: GoogleLoadBalancingScheme.valueOf(forwardingRule.getLoadBalancingScheme()),
        network: forwardingRule.getNetwork(),
        networkTier: forwardingRule.getNetworkTier(),
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
        handleBackendService(backendService, newLoadBalancer, projectRegionalHealthChecks, groupHealthRequest)
      }
    }
  }

  private void handleBackendService(BackendService backendService,
                                    GoogleRegionalExternalNetworkLoadBalancer googleLoadBalancer,
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

      GroupHealthRequest ghr = new GroupHealthRequest(project, backendService.name as String, resourceGroup.getGroup())
      if (!queuedBsGroupHealthRequests.contains(ghr)) {
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
      HealthCheck healthCheck = healthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
      handleHealthCheck(healthCheck, googleLoadBalancer.backendService)
    }
  }

  private static void handleHealthCheck(HealthCheck healthCheck, GoogleBackendService service) {
    if (!healthCheck) {
      return
    }

    def port = null
    def hcType = null
    def requestPath = null
    if (healthCheck.tcpHealthCheck) {
      port = healthCheck.tcpHealthCheck.port
      hcType = GoogleHealthCheck.HealthCheckType.TCP
    } else if (healthCheck.sslHealthCheck) {
      port = healthCheck.sslHealthCheck.port
      hcType = GoogleHealthCheck.HealthCheckType.SSL
    } else if (healthCheck.httpHealthCheck) {
      port = healthCheck.httpHealthCheck.port
      requestPath = healthCheck.httpHealthCheck.requestPath
      hcType = GoogleHealthCheck.HealthCheckType.HTTP
    } else if (healthCheck.httpsHealthCheck) {
      port = healthCheck.httpsHealthCheck.port
      requestPath = healthCheck.httpsHealthCheck.requestPath
      hcType = GoogleHealthCheck.HealthCheckType.HTTPS
    } else if (healthCheck.udpHealthCheck) {
      port = healthCheck.udpHealthCheck.port
      hcType = GoogleHealthCheck.HealthCheckType.UDP
    }

    if (port && hcType) {
      service.healthCheck = new GoogleHealthCheck(
        name: healthCheck.name,
        healthCheckType: hcType,
        port: port,
        requestPath: requestPath ?: "",
        checkIntervalSec: healthCheck.checkIntervalSec,
        timeoutSec: healthCheck.timeoutSec,
        unhealthyThreshold: healthCheck.unhealthyThreshold,
        healthyThreshold: healthCheck.healthyThreshold,
      )
    }
  }

  class GroupHealthCallback<BackendServiceGroupHealth> extends JsonBatchCallback<BackendServiceGroupHealth> {
    String backendServiceName

    /**
     * Tolerate the group health calls failing. Spinnaker reports empty load balancer healths as 'unknown'.
     */
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.debug("Failed backend service group health call for backend service ${backendServiceName} for regional external network load balancer." +
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
