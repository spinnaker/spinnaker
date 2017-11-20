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
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

@Slf4j
class GoogleInternalLoadBalancerCachingAgent extends AbstractGoogleLoadBalancerCachingAgent {

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
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    // Just let GoogleNetworkLoadBalancerCachingAgent return the pending regional on demand requests.
    []
  }

  @Override
  List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName = null) {
    List<GoogleInternalLoadBalancer> loadBalancers = []
    List<String> failedLoadBalancers = []

    BatchRequest forwardingRulesRequest = buildBatchRequest()
    BatchRequest groupHealthRequest = buildBatchRequest()

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
      ForwardingRuleCallbacks.ForwardingRuleSingletonCallback frCallback = forwardingRuleCallbacks.newForwardingRuleSingletonCallback()
      compute.forwardingRules().get(project, region, onDemandLoadBalancerName).queue(forwardingRulesRequest, frCallback)
    } else {
      ForwardingRuleCallbacks.ForwardingRuleListCallback frlCallback = forwardingRuleCallbacks.newForwardingRuleListCallback()
      compute.forwardingRules().list(project, region).queue(forwardingRulesRequest, frlCallback)
    }

    executeIfRequestsAreQueued(forwardingRulesRequest, "InternalLoadBalancerCaching.forwardingRules")
    executeIfRequestsAreQueued(groupHealthRequest, "InternalLoadBalancerCaching.groupHealth")

    return loadBalancers.findAll {!(it.name in failedLoadBalancers)}
  }

  class ForwardingRuleCallbacks {
    List<GoogleInternalLoadBalancer> loadBalancers
    List<String> failedLoadBalancers = []

    // Pass through objects
    BatchRequest groupHealthRequest
    List<BackendService> projectRegionBackendServices
    List<HttpHealthCheck> projectHttpHealthChecks
    List<HttpsHealthCheck> projectHttpsHealthChecks
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
        if (forwardingRule.backendService) {
          cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
        } else {
          throw new IllegalArgumentException("Not responsible for on demand caching of load balancers without backend services.")
        }
      }
    }

    class ForwardingRuleListCallback<ForwardingRuleList> extends JsonBatchCallback<ForwardingRuleList> implements FailureLogger {

      @Override
      void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
        forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
          if (forwardingRule.backendService) {
            cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
          }
        }
      }
    }

    void cacheRemainderOfLoadBalancerResourceGraph(ForwardingRule forwardingRule) {
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
      handleBackendService(backendService, newLoadBalancer, projectHttpHealthChecks, projectHttpsHealthChecks, projectHealthChecks, groupHealthRequest)
    }
  }

  private void handleBackendService(BackendService backendService,
                                    GoogleInternalLoadBalancer googleLoadBalancer,
                                    List<HttpHealthCheck> httpHealthChecks,
                                    List<HttpsHealthCheck> httpsHealthChecks,
                                    List<HealthCheck> healthChecks,
                                    BatchRequest groupHealthRequest) {
    if (!backendService) {
      return
    }

    def groupHealthCallback = new GroupHealthCallback(
      googleLoadBalancer: googleLoadBalancer,
      backendServiceName: backendService.name
    )

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
      compute.regionBackendServices()
        .getHealth(project, region, backendService.name, resourceGroup)
        .queue(groupHealthRequest, groupHealthCallback)
    }

    backendService.healthChecks?.each { String healthCheckURL ->
      def healthCheckName = Utils.getLocalName(healthCheckURL)
      def healthCheckType = Utils.getHealthCheckType(healthCheckURL)
      switch (healthCheckType) {
        case "httpHealthChecks":
          HttpHealthCheck httpHealthCheck = httpHealthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          handleHttpHealthCheck(httpHealthCheck, googleLoadBalancer.backendService)
          break
        case "httpsHealthChecks":
          HttpsHealthCheck httpsHealthCheck = httpsHealthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          handleHttpsHealthCheck(httpsHealthCheck, googleLoadBalancer.backendService)
          break
        case "healthChecks":
          HealthCheck healthCheck = healthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          handleHealthCheck(healthCheck, googleLoadBalancer.backendService)
          break
        default:
          log.warn("Unknown health check type for health check named: ${healthCheckName}. Not queueing any batch requests.")
          break
      }
    }
  }

  private static void handleHttpHealthCheck(HttpHealthCheck httpHealthCheck, GoogleBackendService service) {
    if (!httpHealthCheck) {
      return
    }
    service.healthCheck = new GoogleHealthCheck(
      name: httpHealthCheck.name,
      healthCheckType: GoogleHealthCheck.HealthCheckType.HTTP,
      requestPath: httpHealthCheck.requestPath,
      port: httpHealthCheck.port,
      checkIntervalSec: httpHealthCheck.checkIntervalSec,
      timeoutSec: httpHealthCheck.timeoutSec,
      unhealthyThreshold: httpHealthCheck.unhealthyThreshold,
      healthyThreshold: httpHealthCheck.healthyThreshold,
    )
  }

  private static void handleHttpsHealthCheck(HttpsHealthCheck httpsHealthCheck, GoogleBackendService service) {
    if (!httpsHealthCheck) {
      return
    }
    service.healthCheck = new GoogleHealthCheck(
      name: httpsHealthCheck.name,
      healthCheckType: GoogleHealthCheck.HealthCheckType.HTTPS,
      requestPath: httpsHealthCheck.requestPath,
      port: httpsHealthCheck.port,
      checkIntervalSec: httpsHealthCheck.checkIntervalSec,
      timeoutSec: httpsHealthCheck.timeoutSec,
      unhealthyThreshold: httpsHealthCheck.unhealthyThreshold,
      healthyThreshold: httpsHealthCheck.healthyThreshold,
    )
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
    GoogleInternalLoadBalancer googleLoadBalancer
    String backendServiceName

    /**
     * Tolerate of the group health calls failing. Spinnaker reports empty load balancer healths as 'unknown'.
     * If healthStatus is null in the onSuccess() function, the same state is reported, so this shouldn't cause issues.
     */
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.debug("Failed backend service group health call for backend service ${backendServiceName} for Http load balancer ${googleLoadBalancer.name}." +
        " The platform error message was:\n ${e.getMessage()}.")
    }

    @Override
    void onSuccess(BackendServiceGroupHealth backendServiceGroupHealth, HttpHeaders responseHeaders) throws IOException {
      backendServiceGroupHealth.healthStatus?.each { HealthStatus status ->
        def instanceName = Utils.getLocalName(status.instance)
        def googleLBHealthStatus = GoogleLoadBalancerHealth.PlatformStatus.valueOf(status.healthState)

        googleLoadBalancer.healths << new GoogleLoadBalancerHealth(
          instanceName: instanceName,
          instanceZone: Utils.getZoneFromInstanceUrl(status.instance),
          status: googleLBHealthStatus,
          lbHealthSummaries: [
            new GoogleLoadBalancerHealth.LBHealthSummary(
              loadBalancerName: googleLoadBalancer.name,
              instanceId: instanceName,
              state: googleLBHealthStatus.toServiceStatus(),
            )
          ]
        )
      }
    }
  }
}
