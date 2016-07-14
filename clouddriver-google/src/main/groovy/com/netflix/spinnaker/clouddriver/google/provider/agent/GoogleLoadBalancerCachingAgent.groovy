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
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.HealthStatus
import com.google.api.services.compute.model.InstanceReference
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
import com.netflix.spinnaker.clouddriver.google.model.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.LOAD_BALANCERS

@Slf4j
class GoogleLoadBalancerCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent {

  final String region

  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
      INFORMATIVE.forType(INSTANCES.ns),
  ] as Set

  String agentType = "${accountName}/${region}/${GoogleLoadBalancerCachingAgent.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"
  final OnDemandMetricsSupport metricsSupport

  GoogleLoadBalancerCachingAgent(String googleApplicationName,
                                 GoogleNamedAccountCredentials credentials,
                                 ObjectMapper objectMapper,
                                 String region,
                                 Registry registry) {
    super(googleApplicationName,
          credentials,
          objectMapper)
    this.region = region
    this.metricsSupport = new OnDemandMetricsSupport(
        registry,
        this,
        "${GoogleCloudProvider.GCE}:${OnDemandAgent.OnDemandType.LoadBalancer}")
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<GoogleLoadBalancer> loadBalancers = getLoadBalancers()
    buildCacheResult(providerCache, loadBalancers)
  }

  List<GoogleLoadBalancer> getLoadBalancers() {
    List<GoogleLoadBalancer> loadBalancers = []

    BatchRequest forwardingRulesRequest = buildBatchRequest()
    BatchRequest targetPoolsRequest = buildBatchRequest()
    BatchRequest httpHealthChecksRequest = buildBatchRequest()
    BatchRequest instanceHealthRequest = buildBatchRequest()

    ForwardingRulesCallback callback = new ForwardingRulesCallback(loadBalancers: loadBalancers,
                                                                   targetPoolsRequest: targetPoolsRequest,
                                                                   httpHealthChecksRequest: httpHealthChecksRequest,
                                                                   instanceHealthRequest: instanceHealthRequest)
    compute.forwardingRules().list(project, region).queue(forwardingRulesRequest, callback)

    executeIfRequestsAreQueued(forwardingRulesRequest)
    executeIfRequestsAreQueued(targetPoolsRequest)
    executeIfRequestsAreQueued(httpHealthChecksRequest)
    executeIfRequestsAreQueued(instanceHealthRequest)

    return loadBalancers
  }

  CacheResult buildCacheResult(ProviderCache _, List<GoogleLoadBalancer> googleLoadBalancers) {
    log.info "Describing items in ${agentType}"

    def cacheResultBuilder = new CacheResultBuilder()

    googleLoadBalancers.each { GoogleLoadBalancer loadBalancer ->
      def loadBalancerKey = Keys.getLoadBalancerKey(loadBalancer.region,
                                                    loadBalancer.account,
                                                    loadBalancer.name)
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

    List<GoogleLoadBalancer> loadBalancers = metricsSupport.readData {
      getLoadBalancers()
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

    List<GoogleLoadBalancer> loadBalancers
    BatchRequest targetPoolsRequest

    // Pass through objects
    BatchRequest httpHealthChecksRequest
    BatchRequest instanceHealthRequest

    @Override
    void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
      forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
        def newLoadBalancer = new GoogleLoadBalancer(
            name: forwardingRule.name,
            account: accountName,
            region: region,
            createdTime: Utils.getTimeFromTimestamp(forwardingRule.creationTimestamp),
            ipAddress: forwardingRule.IPAddress,
            ipProtocol: forwardingRule.IPProtocol,
            portRange: forwardingRule.portRange,
            healths: [])
        loadBalancers << newLoadBalancer

        if (forwardingRule.target) {
          def targetPoolName = Utils.getLocalName(forwardingRule.target)
          def targetPoolsCallback = new TargetPoolCallback(googleLoadBalancer: newLoadBalancer,
                                                           httpHealthChecksRequest: httpHealthChecksRequest,
                                                           instanceHealthRequest: instanceHealthRequest)

          compute.targetPools().get(project, region, targetPoolName).queue(targetPoolsRequest, targetPoolsCallback)
        }
      }
    }
  }

  class TargetPoolCallback<TargetPool> extends JsonBatchCallback<TargetPool> implements FailureLogger {

    GoogleLoadBalancer googleLoadBalancer

    BatchRequest httpHealthChecksRequest
    BatchRequest instanceHealthRequest

    @Override
    void onSuccess(TargetPool targetPool, HttpHeaders responseHeaders) throws IOException {
      googleLoadBalancer.targetPool = targetPool?.selfLink
      boolean hasHealthChecks = targetPool?.healthChecks
      targetPool?.healthChecks?.each { def healthCheckUrl ->
        def localHealthCheckName = Utils.getLocalName(healthCheckUrl)
        def httpHealthCheckCallback = new HttpHealthCheckCallback(googleLoadBalancer: googleLoadBalancer,
                                                                  targetPool: targetPool,
                                                                  instanceHealthRequest: instanceHealthRequest)

        compute.httpHealthChecks().get(project, localHealthCheckName).queue(httpHealthChecksRequest, httpHealthCheckCallback)
      }
      if (!hasHealthChecks) {
        new TargetPoolInstanceHealthCallInvoker(googleLoadBalancer: googleLoadBalancer,
                                                targetPool: targetPool,
                                                instanceHealthRequest: instanceHealthRequest).doCall()
      }
    }
  }

  class HttpHealthCheckCallback<HttpHealthCheck> extends JsonBatchCallback<HttpHealthCheck> implements FailureLogger {

    GoogleLoadBalancer googleLoadBalancer
    def targetPool

    BatchRequest instanceHealthRequest

    @Override
    void onSuccess(HttpHealthCheck httpHealthCheck, HttpHeaders responseHeaders) throws IOException {
      if (httpHealthCheck) {
        googleLoadBalancer.healthCheck = new GoogleHealthCheck(
            name: httpHealthCheck.name,
            port: httpHealthCheck.port,
            requestPath: httpHealthCheck.requestPath,
            checkIntervalSec: httpHealthCheck.checkIntervalSec,
            timeoutSec: httpHealthCheck.timeoutSec,
            unhealthyThreshold: httpHealthCheck.unhealthyThreshold,
            healthyThreshold: httpHealthCheck.healthyThreshold)
      }

      new TargetPoolInstanceHealthCallInvoker(googleLoadBalancer: googleLoadBalancer,
                                              targetPool: targetPool,
                                              instanceHealthRequest: instanceHealthRequest).doCall()
    }
  }

  class TargetPoolInstanceHealthCallInvoker {

    GoogleLoadBalancer googleLoadBalancer
    def targetPool

    BatchRequest instanceHealthRequest

    def doCall() {
      def region = Utils.getLocalName(targetPool.region as String)
      def targetPoolName = targetPool.name as String

      targetPool?.instances?.each { String instanceUrl ->
        def instanceReference = new InstanceReference(instance: instanceUrl)
        def instanceHealthCallback = new TargetPoolInstanceHealthCallback(googleLoadBalancer: googleLoadBalancer,
                                                                          instanceName: Utils.getLocalName(instanceUrl),
                                                                          instanceZone: Utils.getZoneFromInstanceUrl(instanceUrl))

        compute.targetPools().getHealth(project,
                                        region,
                                        targetPoolName,
                                        instanceReference).queue(instanceHealthRequest, instanceHealthCallback)
      }
    }
  }


  class TargetPoolInstanceHealthCallback<TargetPoolInstanceHealth> extends JsonBatchCallback<TargetPoolInstanceHealth> implements FailureLogger {

    GoogleLoadBalancer googleLoadBalancer
    String instanceName
    String instanceZone

    @Override
    void onSuccess(TargetPoolInstanceHealth targetPoolInstanceHealth, HttpHeaders responseHeaders) throws IOException {
      targetPoolInstanceHealth?.healthStatus?.each { HealthStatus healthStatus ->
        def googleLBHealthStatus = GoogleLoadBalancerHealth.PlatformStatus.valueOf(healthStatus.healthState)

        // Google APIs return instances as UNHEALTHY if an instance is associated with a target pool (load balancer)
        // but that target pool does not have a health check. This is the wrong behavior, because the instance may still
        // receive traffic if it is in the RUNNING state.
        if (!googleLoadBalancer.healthCheck) {
          googleLBHealthStatus = GoogleLoadBalancerHealth.PlatformStatus.HEALTHY
        }

        googleLoadBalancer.healths << new GoogleLoadBalancerHealth(
            instanceName: instanceName,
            instanceZone: instanceZone,
            status: googleLBHealthStatus,
            lbHealthSummaries: [
                new GoogleLoadBalancerHealth.LBHealthSummary(
                    loadBalancerName: googleLoadBalancer.name,
                    instanceId: instanceName,
                    state: googleLBHealthStatus.toServiceStatus())
            ])
      }
    }
  }
}
