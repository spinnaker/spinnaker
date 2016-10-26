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
class GoogleInternalLoadBalancerCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent {

  final String region

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(INSTANCES.ns),
  ] as Set

  String agentType = "${accountName}/${region}/${GoogleInternalLoadBalancerCachingAgent.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"
  final OnDemandMetricsSupport metricsSupport

  GoogleInternalLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
                                         GoogleNamedAccountCredentials credentials,
                                         ObjectMapper objectMapper,
                                         String region,
                                         Registry registry) {
    super(clouddriverUserAgentApplicationName, credentials, objectMapper)
    this.region = region
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${GoogleCloudProvider.GCE}:${OnDemandAgent.OnDemandType.LoadBalancer}")
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<GoogleInternalLoadBalancer> loadBalancers = getInternalLoadBalancers()
    buildCacheResult(providerCache, loadBalancers)
  }

  List<GoogleInternalLoadBalancer> getInternalLoadBalancers() {
    List<GoogleInternalLoadBalancer> loadBalancers = []

    BatchRequest forwardingRulesRequest = buildBatchRequest()
    BatchRequest backendServiceRequest = buildBatchRequest()
    BatchRequest groupHealthRequest = buildBatchRequest()
    BatchRequest healthCheckRequest = buildBatchRequest()

    ForwardingRulesCallback callback = new ForwardingRulesCallback(
      loadBalancers: loadBalancers,
      backendServiceRequest: backendServiceRequest,
      healthCheckRequest: healthCheckRequest,
      groupHealthRequest: groupHealthRequest,
    )
    compute.forwardingRules().list(project, region).queue(forwardingRulesRequest, callback)

    executeIfRequestsAreQueued(forwardingRulesRequest)
    executeIfRequestsAreQueued(backendServiceRequest)
    executeIfRequestsAreQueued(healthCheckRequest)
    executeIfRequestsAreQueued(groupHealthRequest)

    return loadBalancers
  }

  CacheResult buildCacheResult(ProviderCache _, List<GoogleInternalLoadBalancer> googleLoadBalancers) {
    log.info "Describing items in ${agentType}"

    def cacheResultBuilder = new CacheResultBuilder()

    googleLoadBalancers.each { GoogleInternalLoadBalancer loadBalancer ->
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

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.LoadBalancer && cloudProvider == GoogleCloudProvider.GCE
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }

  /**
   * This is a "simpleton" way of handling on-demand cache requests. Load Balancer mutation (and thus the need for
   * cache refreshing) is not as common or complex as server group cache refreshes.
   *
   * This implementation has the potential for race condition between handle() and loadData(), which may
   * cause "flapping" in the UI. lwander@ has plans to make an abstract solution for this race condition, so this impl
   * will do until that is ready.
   */
  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (data.account != accountName || data.region != region) {
      return null
    }

    List<GoogleInternalLoadBalancer> loadBalancers = metricsSupport.readData {
      getInternalLoadBalancers()
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

    List<GoogleInternalLoadBalancer> loadBalancers
    BatchRequest backendServiceRequest

    // Pass through objects
    BatchRequest healthCheckRequest
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
      forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
        if (forwardingRule.backendService) {
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
          def backendServiceCallback = new BackendServiceCallback(
            googleLoadBalancer: newLoadBalancer,
            healthCheckRequest: healthCheckRequest,
            groupHealthRequest: groupHealthRequest,
          )
          compute.regionBackendServices()
            .get(project, region, backendServiceName)
            .queue(backendServiceRequest, backendServiceCallback)
        }
      }
    }
  }

  class BackendServiceCallback<BackendService> extends JsonBatchCallback<BackendService> implements FailureLogger {
    GoogleInternalLoadBalancer googleLoadBalancer
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
        backends: backendService.backends?.collect { Backend backend ->
          new GoogleLoadBalancedBackend(
            serverGroupUrl: backend.group,
            policy: new GoogleLoadBalancingPolicy(balancingMode: backend.balancingMode)
          )
        } ?: []
      )
      googleLoadBalancer.backendService = newService

      backendService.backends?.each { Backend backend ->
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
            def healthCheckCallback = new HttpHealthCheckCallback(
              googleBackendService: googleLoadBalancer.backendService
            )
            compute.httpHealthChecks().get(project, healthCheckName).queue(healthCheckRequest, healthCheckCallback)
            break
          case "httpsHealthChecks":
            def healthCheckCallback = new HttpsHealthCheckCallback(
              googleBackendService: googleLoadBalancer.backendService
            )
            compute.httpsHealthChecks().get(project, healthCheckName).queue(healthCheckRequest, healthCheckCallback)
            break
          case "healthChecks":
            def healthCheckCallback = new HealthCheckCallback(
              googleBackendService: googleLoadBalancer.backendService
            )
            compute.healthChecks().get(project, healthCheckName).queue(healthCheckRequest, healthCheckCallback)
            break
          default:
            log.warn("Unknown health check type for health check named: ${healthCheckName}. Not queueing any batch requests.")
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

  class HttpsHealthCheckCallback<HttpsHealthCheck> extends JsonBatchCallback<HttpsHealthCheck> implements FailureLogger {
    GoogleBackendService googleBackendService

    @Override
    void onSuccess(HttpsHealthCheck httpsHealthCheck, HttpHeaders responseHeaders) throws IOException {
      googleBackendService.healthCheck = new GoogleHealthCheck(
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
  }

  class HttpHealthCheckCallback<HttpHealthCheck> extends JsonBatchCallback<HttpHealthCheck> implements FailureLogger {
    GoogleBackendService googleBackendService

    @Override
    void onSuccess(HttpHealthCheck httpHealthCheck, HttpHeaders responseHeaders) throws IOException {
      googleBackendService.healthCheck = new GoogleHealthCheck(
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
  }

  class GroupHealthCallback<BackendServiceGroupHealth> extends JsonBatchCallback<BackendServiceGroupHealth> implements FailureLogger {
    GoogleInternalLoadBalancer googleLoadBalancer

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
