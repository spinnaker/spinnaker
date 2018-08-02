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
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.*
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.GroupHealthRequest
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.LoadBalancerHealthResolution
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

@Slf4j
class GoogleTcpLoadBalancerCachingAgent extends AbstractGoogleLoadBalancerCachingAgent {

  /**
   * Local cache of BackendServiceGroupHealth keyed by BackendService name.
   *
   * It turns out that the types in the GCE Batch callbacks aren't the actual Compute
   * types for some reason, which is why this map is String -> Object.
   */
  Map<String, Object> bsNameToGroupHealthsMap = [:]
  Set<GroupHealthRequest> queuedBsGroupHealthRequests = new HashSet<>()
  List<LoadBalancerHealthResolution> resolutions = []

  GoogleTcpLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
                                    GoogleNamedAccountCredentials credentials,
                                    ObjectMapper objectMapper,
                                    Registry registry) {
    super(clouddriverUserAgentApplicationName,
      credentials,
      objectMapper,
      registry,
      "global")
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    // Just let GoogleHttpLoadBalancerCachingAgent return the pending global on demand requests.
    []
  }

  @Override
  List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName = null) {
    List<GoogleLoadBalancer> loadBalancers = []
    List<String> failedSubjects = []

    BatchRequest forwardingRulesRequest = buildBatchRequest()
    BatchRequest targetTcpProxyRequest = buildBatchRequest()
    BatchRequest groupHealthRequest = buildBatchRequest()

    // Reset the local getHealth caches/queues each caching agent cycle.
    bsNameToGroupHealthsMap = [:]
    queuedBsGroupHealthRequests = new HashSet<>()
    resolutions = []

    List<BackendService> projectBackendServices = GCEUtil.fetchBackendServices(this, compute, project)
    List<HealthCheck> projectHealthChecks = GCEUtil.fetchHealthChecks(this, compute, project)

    ForwardingRuleCallbacks forwardingRuleCallbacks = new ForwardingRuleCallbacks(
      loadBalancers: loadBalancers,
      failedSubjects: failedSubjects,
      targetTcpProxyRequest: targetTcpProxyRequest,
      projectBackendServices: projectBackendServices,
      projectHealthChecks: projectHealthChecks,
      groupHealthRequest: groupHealthRequest,
    )

    if (onDemandLoadBalancerName) {
      ForwardingRuleCallbacks.ForwardingRuleSingletonCallback frCallback = forwardingRuleCallbacks.newForwardingRuleSingletonCallback()
      compute.globalForwardingRules().get(project, onDemandLoadBalancerName).queue(forwardingRulesRequest, frCallback)
    } else {
      ForwardingRuleCallbacks.ForwardingRuleListCallback frlCallback = forwardingRuleCallbacks.newForwardingRuleListCallback()
      compute.globalForwardingRules().list(project).queue(forwardingRulesRequest, frlCallback)
    }

    executeIfRequestsAreQueued(forwardingRulesRequest, "TcpLoadBalancerCaching.forwardingRules")
    executeIfRequestsAreQueued(targetTcpProxyRequest, "TcpLoadBalancerCaching.targetTcpProxy")
    executeIfRequestsAreQueued(groupHealthRequest, "TcpLoadBalancerCaching.groupHealthCheck")

    resolutions.each { LoadBalancerHealthResolution resolution ->
      bsNameToGroupHealthsMap.get(resolution.getTarget()).each { groupHealth ->
        GCEUtil.handleHealthObject(resolution.getGoogleLoadBalancer(), groupHealth)
      }
    }

    return loadBalancers.findAll { !(it.name in failedSubjects) }
  }

  class ForwardingRuleCallbacks {
    List<GoogleHttpLoadBalancer> loadBalancers
    List<String> failedSubjects = []
    BatchRequest targetTcpProxyRequest

    // Pass through objects
    BatchRequest groupHealthRequest
    List<BackendService> projectBackendServices
    List<HealthCheck> projectHealthChecks

    ForwardingRuleSingletonCallback<ForwardingRule> newForwardingRuleSingletonCallback() {
      return new ForwardingRuleSingletonCallback<ForwardingRule>()
    }

    ForwardingRuleListCallback<ForwardingRuleList> newForwardingRuleListCallback() {
      return new ForwardingRuleListCallback<ForwardingRuleList>()
    }

    class ForwardingRuleSingletonCallback<ForwardingRule> extends JsonBatchCallback<ForwardingRule> {

      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        // 404 is thrown if the forwarding rule does not exist in the given region. Any other exception needs to be propagated.
        if (e.code != 404) {
          def errorJson = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e)
          log.error errorJson
        }
      }

      @Override
      void onSuccess(ForwardingRule forwardingRule, HttpHeaders responseHeaders) throws IOException {
        if (forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) == GoogleTargetProxyType.TCP) {
          cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
        } else {
          throw new IllegalArgumentException("Not responsible for on demand caching of load balancers without target " +
            "proxy or without TCP proxy type.")
        }
      }
    }

    class ForwardingRuleListCallback<ForwardingRuleList> extends JsonBatchCallback<ForwardingRuleList> implements FailureLogger {

      @Override
      void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
        forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
          if (forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) == GoogleTargetProxyType.TCP) {
            cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
          }
        }
      }
    }

    void cacheRemainderOfLoadBalancerResourceGraph(ForwardingRule forwardingRule) {
      def newLoadBalancer = new GoogleTcpLoadBalancer(
        name: forwardingRule.name,
        account: accountName,
        region: 'global',
        createdTime: Utils.getTimeFromTimestamp(forwardingRule.creationTimestamp),
        ipAddress: forwardingRule.IPAddress,
        ipProtocol: forwardingRule.IPProtocol,
        portRange: forwardingRule.portRange,
        loadBalancingScheme: GoogleLoadBalancingScheme.valueOf(forwardingRule.getLoadBalancingScheme()),
        healths: [],
      )
      loadBalancers << newLoadBalancer

      def targetTcpProxyName = Utils.getLocalName(forwardingRule.target)
      def targetTcpProxyCallback = new TargetTcpProxyCallback(
        googleLoadBalancer: newLoadBalancer,
        projectBackendServices: projectBackendServices,
        projectHealthChecks: projectHealthChecks,
        groupHealthRequest: groupHealthRequest,
        subject: newLoadBalancer.name,
        failedSubjects: failedSubjects,
      )
      compute.targetTcpProxies().get(project, targetTcpProxyName).queue(targetTcpProxyRequest, targetTcpProxyCallback)
    }
  }

  class TargetTcpProxyCallback<TargetTcpProxy> extends JsonBatchCallback<TargetTcpProxy> implements FailedSubjectChronicler {
    GoogleTcpLoadBalancer googleLoadBalancer
    List<BackendService> projectBackendServices
    List<HealthCheck> projectHealthChecks
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(TargetTcpProxy targetTcpProxy, HttpHeaders responseHeaders) throws IOException {
      String backendServiceName = GCEUtil.getLocalName(targetTcpProxy.service)
      BackendService backendService = projectBackendServices?.find { BackendService bs -> bs.getName() == backendServiceName }
      if (backendService == null) {
        log.warn("Failed to read a component of subject ${googleLoadBalancer.name}. Could not find BackendService ${backendServiceName}.\n"
          + "Reporting it as 'Failed' to the caching agent.")
        failedSubjects << googleLoadBalancer.name
      } else {
        handleBackendService(backendService, googleLoadBalancer, projectHealthChecks, groupHealthRequest)
      }
    }
  }

  private void handleBackendService(BackendService backendService,
                                    GoogleTcpLoadBalancer googleLoadBalancer,
                                    List<HealthCheck> healthChecks,
                                    BatchRequest groupHealthRequest) {
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
        compute.backendServices()
          .getHealth(project, backendService.name, resourceGroup)
          .queue(groupHealthRequest, groupHealthCallback)
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
          log.warn("Illegal health check type 'httpHealthCheck' for health check named: ${healthCheckName}. Not processing the health check.")
          break
        case "httpsHealthChecks":
          log.warn("Illegal health check type 'httpsHealthCheck' for health check named: ${healthCheckName}. Not processing the health check.")
          break
        case "healthChecks":
          HealthCheck healthCheck = healthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          handleHealthCheck(healthCheck, googleLoadBalancer.backendService)
          break
        default:
          log.warn("Unknown health check type for health check named: ${healthCheckName}. Not processing the health check.")
          break
      }
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
     * Tolerate of the group health calls failing. Spinnaker reports empty load balancer healths as 'unknown'.
     * If healthStatus is null in the onSuccess() function, the same state is reported, so this shouldn't cause issues.
     */
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.debug("Failed backend service group health call for backend service ${backendServiceName} for Tcp load balancer." +
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
