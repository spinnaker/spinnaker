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
import com.google.api.services.compute.model.Backend
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.google.api.services.compute.model.HealthStatus
import com.google.api.services.compute.model.ResourceGroupReference
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
class GoogleSslLoadBalancerCachingAgent extends AbstractGoogleLoadBalancerCachingAgent {

  GoogleSslLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
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
    List<String> failedLoadBalancers = []

    BatchRequest forwardingRulesRequest = buildBatchRequest()
    BatchRequest targetSslProxyRequest = buildBatchRequest()
    BatchRequest backendServiceRequest = buildBatchRequest()
    BatchRequest groupHealthRequest = buildBatchRequest()
    BatchRequest healthCheckRequest = buildBatchRequest()

    ForwardingRuleCallbacks forwardingRuleCallbacks = new ForwardingRuleCallbacks(
      loadBalancers: loadBalancers,
      failedLoadBalancers: failedLoadBalancers,
      targetSslProxyRequest: targetSslProxyRequest,
      backendServiceRequest: backendServiceRequest,
      healthCheckRequest: healthCheckRequest,
      groupHealthRequest: groupHealthRequest,
    )

    if (onDemandLoadBalancerName) {
      ForwardingRuleCallbacks.ForwardingRuleSingletonCallback frCallback = forwardingRuleCallbacks.newForwardingRuleSingletonCallback()
      compute.globalForwardingRules().get(project, onDemandLoadBalancerName).queue(forwardingRulesRequest, frCallback)
    } else {
      ForwardingRuleCallbacks.ForwardingRuleListCallback frlCallback = forwardingRuleCallbacks.newForwardingRuleListCallback()
      compute.globalForwardingRules().list(project).queue(forwardingRulesRequest, frlCallback)
    }

    executeIfRequestsAreQueued(forwardingRulesRequest, "SslLoadBalancerCaching.forwardingRules")
    executeIfRequestsAreQueued(targetSslProxyRequest, "SslLoadBalancerCaching.targetSslProxy")
    executeIfRequestsAreQueued(backendServiceRequest, "SslLoadBalancerCaching.backendService")
    executeIfRequestsAreQueued(healthCheckRequest, "SslLoadBalancerCaching.healthCheck")
    executeIfRequestsAreQueued(groupHealthRequest, "SslLoadBalancerCaching.groupHealthCheck")

    return loadBalancers.findAll {!(it.name in failedLoadBalancers)}
  }

  class ForwardingRuleCallbacks {

    List<GoogleHttpLoadBalancer> loadBalancers
    List<String> failedLoadBalancers = []
    BatchRequest targetSslProxyRequest

    // Pass through objects
    BatchRequest backendServiceRequest
    BatchRequest healthCheckRequest
    BatchRequest groupHealthRequest

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
        if (forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) == GoogleTargetProxyType.SSL) {
          cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
        } else {
          throw new IllegalArgumentException("Not responsible for on demand caching of load balancers without target " +
            "proxy or without SSL proxy type.")
        }
      }
    }

    class ForwardingRuleListCallback<ForwardingRuleList> extends JsonBatchCallback<ForwardingRuleList> implements FailureLogger {

      @Override
      void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
        forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
          if (forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) == GoogleTargetProxyType.SSL) {
            cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
          }
        }
      }
    }

    void cacheRemainderOfLoadBalancerResourceGraph(ForwardingRule forwardingRule) {
      def newLoadBalancer = new GoogleSslLoadBalancer(
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

      def targetSslProxyName = Utils.getLocalName(forwardingRule.target)
      def targetSslProxyCallback = new TargetSslProxyCallback(
        googleLoadBalancer: newLoadBalancer,
        backendServiceRequest: backendServiceRequest,
        healthCheckRequest: healthCheckRequest,
        groupHealthRequest: groupHealthRequest,
        subject: newLoadBalancer.name,
        failedSubjects: failedLoadBalancers,
      )
      compute.targetSslProxies().get(project, targetSslProxyName).queue(targetSslProxyRequest, targetSslProxyCallback)
    }
  }

  class TargetSslProxyCallback<TargetSslProxy> extends JsonBatchCallback<TargetSslProxy> implements FailedSubjectChronicler {

    GoogleSslLoadBalancer googleLoadBalancer
    BatchRequest backendServiceRequest

    // Pass through objects
    BatchRequest healthCheckRequest
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(TargetSslProxy targetSslProxy, HttpHeaders responseHeaders) throws IOException {
      googleLoadBalancer.certificate = targetSslProxy.sslCertificates[0]

      def backendServiceCallback = new BackendServiceCallback(
        googleLoadBalancer: googleLoadBalancer,
        healthCheckRequest: healthCheckRequest,
        groupHealthRequest: groupHealthRequest,
        subject: googleLoadBalancer.name,
        failedSubjects: failedSubjects,
      )
      compute.backendServices().get(project, GCEUtil.getLocalName(targetSslProxy.service)).queue(backendServiceRequest, backendServiceCallback)
    }
  }

  class BackendServiceCallback<BackendService> extends JsonBatchCallback<BackendService> implements FailedSubjectChronicler {
    GoogleSslLoadBalancer googleLoadBalancer
    BatchRequest healthCheckRequest
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(BackendService backendService, HttpHeaders responseHeaders) throws IOException {
      def groupHealthCallback = new GroupHealthCallback(
        googleLoadBalancer: googleLoadBalancer,
        subject: googleLoadBalancer.name,
        failedSubjects: failedSubjects
      )

      GoogleBackendService newService = new GoogleBackendService(
        name: backendService.name,
        loadBalancingScheme: backendService.loadBalancingScheme,
        sessionAffinity: backendService.sessionAffinity,
        affinityCookieTtlSec: backendService.affinityCookieTtlSec,
        backends: backendService.backends?.collect { Backend backend ->
          new GoogleLoadBalancedBackend(
            serverGroupUrl: backend.group,
            policy: GCEUtil.loadBalancingPolicyFromBackend(backend)
          )
        } ?: []
      )
      googleLoadBalancer.backendService = newService

      backendService.backends?.each { Backend backend ->
        def resourceGroup = new ResourceGroupReference()
        resourceGroup.setGroup(backend.group as String)
        compute.backendServices()
          .getHealth(project, backendService.name, resourceGroup)
          .queue(groupHealthRequest, groupHealthCallback)
      }

      backendService.healthChecks?.each { String healthCheckURL ->
        def healthCheckName = Utils.getLocalName(healthCheckURL)
        def healthCheckType = Utils.getHealthCheckType(healthCheckURL)
        switch (healthCheckType) {
          case "healthChecks":
            def healthCheckCallback = new HealthCheckCallback(
              googleBackendService: googleLoadBalancer.backendService,
              subject: googleLoadBalancer.name,
              failedSubjects: failedSubjects
            )
            compute.healthChecks().get(project, healthCheckName).queue(healthCheckRequest, healthCheckCallback)
            break
          default:
            log.warn("Unsupported health check type for health check named: ${healthCheckName}. Not queueing any batch requests.")
            break
        }
      }
    }
  }

  class HealthCheckCallback<HealthCheck> extends JsonBatchCallback<HealthCheck> implements FailedSubjectChronicler {
    GoogleBackendService googleBackendService

    @Override
    void onSuccess(HealthCheck healthCheck, HttpHeaders responseHeaders) throws IOException {
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
        googleBackendService.healthCheck = new GoogleHealthCheck(
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
  }

  class GroupHealthCallback<BackendServiceGroupHealth> extends JsonBatchCallback<BackendServiceGroupHealth> implements FailedSubjectChronicler {
    GoogleSslLoadBalancer googleLoadBalancer

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
