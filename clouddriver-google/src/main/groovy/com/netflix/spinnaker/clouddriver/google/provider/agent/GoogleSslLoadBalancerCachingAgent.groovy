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
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.Backend
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.HealthStatus
import com.google.api.services.compute.model.ResourceGroupReference
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.LOAD_BALANCERS

@Slf4j
class GoogleSslLoadBalancerCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent {

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(INSTANCES.ns),
  ] as Set

  String agentType = "${accountName}/global/${GoogleSslCertificateCachingAgent.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"
  final OnDemandMetricsSupport metricsSupport

  GoogleSslLoadBalancerCachingAgent(String googleApplicationName,
                                    GoogleNamedAccountCredentials credentials,
                                    ObjectMapper objectMapper,
                                    Registry registry) {
    super(googleApplicationName, credentials, objectMapper)
    this.metricsSupport = new OnDemandMetricsSupport(
        registry,
        this,
        "${GoogleCloudProvider.GCE}:${OnDemandAgent.OnDemandType.LoadBalancer}"
    )
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<GoogleSslLoadBalancer> loadBalancers = getSslLoadBalancers()
    buildCacheResult(providerCache, loadBalancers)
  }

  List<GoogleSslLoadBalancer> getSslLoadBalancers() {
    List<GoogleSslLoadBalancer> loadBalancers = []

    BatchRequest forwardingRulesRequest = buildBatchRequest()
    BatchRequest targetSslProxyRequest = buildBatchRequest()
    BatchRequest backendServiceRequest = buildBatchRequest()
    BatchRequest groupHealthRequest = buildBatchRequest()
    BatchRequest healthCheckRequest = buildBatchRequest()

    ForwardingRulesCallback frCallback = new ForwardingRulesCallback(
      loadBalancers: loadBalancers,
      targetSslProxyRequest: targetSslProxyRequest,
      backendServiceRequest: backendServiceRequest,
      healthCheckRequest: healthCheckRequest,
      groupHealthRequest: groupHealthRequest,
    )
    compute.globalForwardingRules().list(project).queue(forwardingRulesRequest, frCallback)

    executeIfRequestsAreQueued(forwardingRulesRequest)
    executeIfRequestsAreQueued(targetSslProxyRequest)
    executeIfRequestsAreQueued(backendServiceRequest)
    executeIfRequestsAreQueued(healthCheckRequest)
    executeIfRequestsAreQueued(groupHealthRequest)

    loadBalancers
  }

  CacheResult buildCacheResult(ProviderCache _, List<GoogleSslLoadBalancer> googleLoadBalancers) {
    log.info "Describing items in ${agentType}"

    def cacheResultBuilder = new CacheResultBuilder()

    googleLoadBalancers.each { GoogleSslLoadBalancer loadBalancer ->
      def loadBalancerKey = Keys.getLoadBalancerKey(
        loadBalancer.region,
        loadBalancer.account,
        loadBalancer.name
      )
      def instanceKeys = loadBalancer.healths.collect { GoogleLoadBalancerHealth health ->
        Keys.getInstanceKey(accountName, loadBalancer.region, health.instanceName)
      }

      cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keep(loadBalancerKey).with {
        attributes = objectMapper.convertValue(loadBalancer, ATTRIBUTES)
      }
      instanceKeys.each { String instanceKey ->
        cacheResultBuilder.namespace(INSTANCES.ns).keep(instanceKey).with {
          relationships[LOAD_BALANCERS.ns].add(loadBalancerKey)
        }
      }
    }

    log.info "Caching ${cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keepSize()} load balancers in ${agentType}"
    log.info "Caching ${cacheResultBuilder.namespace(INSTANCES.ns).keepSize()} instance relationships in ${agentType}"

    cacheResultBuilder.build()
  }

  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.LoadBalancer && cloudProvider == GoogleCloudProvider.GCE
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }

  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (data.account != accountName || data.region != 'global') {
      return null
    }

    List<GoogleSslLoadBalancer> loadBalancers = metricsSupport.readData {
      getSslLoadBalancers()
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult(providerCache, loadBalancers)
    }

    new OnDemandAgent.OnDemandResult(
      sourceAgentType: getAgentType(),
      authoritativeTypes: [LOAD_BALANCERS.ns],
      cacheResult: result
    )
  }

  class ForwardingRulesCallback<ForwardingRuleList> extends JsonBatchCallback<ForwardingRuleList> implements FailureLogger {

    List<GoogleSslLoadBalancer> loadBalancers
    BatchRequest targetSslProxyRequest

    // Pass through objects
    BatchRequest backendServiceRequest
    BatchRequest healthCheckRequest
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
      forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
        if (forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) == GoogleTargetProxyType.SSL) {
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
          )
          compute.targetSslProxies().get(project, targetSslProxyName).queue(targetSslProxyRequest, targetSslProxyCallback)
        }
      }
    }
  }

  class TargetSslProxyCallback<TargetSslProxy> extends JsonBatchCallback<TargetSslProxy> implements FailureLogger {

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
      )
      compute.backendServices().get(project, GCEUtil.getLocalName(targetSslProxy.service)).queue(backendServiceRequest, backendServiceCallback)
    }
  }

  class BackendServiceCallback<BackendService> extends JsonBatchCallback<BackendService> implements FailureLogger {
    GoogleSslLoadBalancer googleLoadBalancer
    BatchRequest healthCheckRequest
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(BackendService backendService, HttpHeaders responseHeaders) throws IOException {
      def groupHealthCallback = new GroupHealthCallback(
        googleLoadBalancer: googleLoadBalancer
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
              googleBackendService: googleLoadBalancer.backendService
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

  class HealthCheckCallback<HealthCheck> extends JsonBatchCallback<HealthCheck> implements FailureLogger {
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

  class GroupHealthCallback<BackendServiceGroupHealth> extends JsonBatchCallback<BackendServiceGroupHealth> implements FailureLogger {
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
