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
import com.google.api.services.compute.model.*
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
class GoogleHttpLoadBalancerCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent {

  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
      INFORMATIVE.forType(INSTANCES.ns),
  ] as Set

  String agentType = "${accountName}/global/${GoogleHttpLoadBalancerCachingAgent.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"
  final OnDemandMetricsSupport metricsSupport

  GoogleHttpLoadBalancerCachingAgent(String googleApplicationName,
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
    List<GoogleHttpLoadBalancer> loadBalancers = getHttpLoadBalancers()
    buildCacheResult(providerCache, loadBalancers)
  }

  List<GoogleHttpLoadBalancer> getHttpLoadBalancers() {
    List<GoogleHttpLoadBalancer> loadBalancers = []

    BatchRequest forwardingRulesRequest = buildBatchRequest()
    BatchRequest targetProxyRequest = buildBatchRequest()
    BatchRequest urlMapRequest = buildBatchRequest()
    BatchRequest backendServiceRequest = buildBatchRequest()
    BatchRequest groupHealthRequest = buildBatchRequest()
    BatchRequest httpHealthCheckRequest = buildBatchRequest()

    ForwardingRulesCallback callback = new ForwardingRulesCallback(
        loadBalancers: loadBalancers,
        targetProxyRequest: targetProxyRequest,
        urlMapRequest: urlMapRequest,
        backendServiceRequest: backendServiceRequest,
        httpHealthCheckRequest: httpHealthCheckRequest,
        groupHealthRequest: groupHealthRequest,
    )
    compute.globalForwardingRules().list(project).queue(forwardingRulesRequest, callback)

    executeIfRequestsAreQueued(forwardingRulesRequest)
    executeIfRequestsAreQueued(targetProxyRequest)
    executeIfRequestsAreQueued(urlMapRequest)
    executeIfRequestsAreQueued(backendServiceRequest)
    executeIfRequestsAreQueued(httpHealthCheckRequest)
    executeIfRequestsAreQueued(groupHealthRequest)

    return loadBalancers
  }

  CacheResult buildCacheResult(ProviderCache _, List<GoogleHttpLoadBalancer> googleLoadBalancers) {
    log.info "Describing items in ${agentType}"

    def cacheResultBuilder = new CacheResultBuilder()

    googleLoadBalancers.each { GoogleHttpLoadBalancer loadBalancer ->
      def loadBalancerKey = Keys.getLoadBalancerKey(
          loadBalancer.region,
          loadBalancer.account,
          loadBalancer.name
      )
      def instanceKeys = loadBalancer.healths.collect { GoogleLoadBalancerHealth health ->
        // Http load balancers' region is "global", so we have to determine the instance region from its zone.
        def instanceZone = health.instanceZone
        def instanceRegion = credentials.regionFromZone(instanceZone)
        Keys.getInstanceKey(accountName, instanceRegion, health.instanceName)
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
    if (data.account != accountName || data.region != 'global') {
      return null
    }

    List<GoogleHttpLoadBalancer> loadBalancers = metricsSupport.readData {
      getHttpLoadBalancers()
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

    List<GoogleHttpLoadBalancer> loadBalancers
    BatchRequest targetProxyRequest

    // Pass through objects
    BatchRequest urlMapRequest
    BatchRequest backendServiceRequest
    BatchRequest httpHealthCheckRequest
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
      forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
        def newLoadBalancer = new GoogleHttpLoadBalancer(
            name: forwardingRule.name,
            account: accountName,
            region: 'global',
            createdTime: Utils.getTimeFromTimestamp(forwardingRule.creationTimestamp),
            ipAddress: forwardingRule.IPAddress,
            ipProtocol: forwardingRule.IPProtocol,
            portRange: forwardingRule.portRange,
            healths: [],
            hostRules: [],
        )
        loadBalancers << newLoadBalancer

        if (forwardingRule.target) {
          def targetProxyName = Utils.getLocalName(forwardingRule.target)
          def targetProxyCallback = new TargetProxyCallback(
              googleLoadBalancer: newLoadBalancer,
              urlMapRequest: urlMapRequest,
              backendServiceRequest: backendServiceRequest,
              httpHealthCheckRequest: httpHealthCheckRequest,
              groupHealthRequest: groupHealthRequest,
          )
          def targetHttpsProxyCallback = new TargetHttpsProxyCallback(
              googleLoadBalancer: newLoadBalancer,
              urlMapRequest: urlMapRequest,
              backendServiceRequest: backendServiceRequest,
              httpHealthCheckRequest: httpHealthCheckRequest,
              groupHealthRequest: groupHealthRequest,
          )

          String targetType = Utils.getTargetProxyType(forwardingRule.target)
          switch (targetType) {
            case "targetHttpProxies":
              compute.targetHttpProxies().get(project, targetProxyName).queue(targetProxyRequest, targetProxyCallback)
              break
            case "targetHttpsProxies":
              compute.targetHttpsProxies().get(project, targetProxyName).queue(targetProxyRequest, targetHttpsProxyCallback)
              break
            default:
              log.info("Non-Http target type found for global forwarding rule ${forwardingRule.name}")
              break
          }
        }
      }
    }
  }

  // Note: The TargetProxyCallbacks assume that each proxy points to a unique urlMap.
  class TargetHttpsProxyCallback<TargetHttpsProxy> extends JsonBatchCallback<TargetHttpsProxy> implements FailureLogger {
    GoogleHttpLoadBalancer googleLoadBalancer
    BatchRequest urlMapRequest

    // Pass through objects
    BatchRequest backendServiceRequest
    BatchRequest httpHealthCheckRequest
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(TargetHttpsProxy targetHttpsProxy, HttpHeaders responseHeaders) throws IOException {
      // SslCertificates is a required field for TargetHttpsProxy, and contains exactly one cert.
      googleLoadBalancer.certificate = Utils.getLocalName((targetHttpsProxy.getSslCertificates()[0]))

      def urlMapURL = targetHttpsProxy?.urlMap
      if (urlMapURL) {
        def urlMapName = Utils.getLocalName(urlMapURL)
        def urlMapCallback = new UrlMapCallback(
            googleLoadBalancer: googleLoadBalancer,
            backendServiceRequest: backendServiceRequest,
            httpHealthCheckRequest: httpHealthCheckRequest,
            groupHealthRequest: groupHealthRequest,
        )
        compute.urlMaps().get(project, urlMapName).queue(urlMapRequest, urlMapCallback)
      }
    }
  }

  // Note: The TargetProxyCallbacks assume that each proxy points to a unique urlMap.
  class TargetProxyCallback<TargetHttpProxy> extends JsonBatchCallback<TargetHttpProxy> implements FailureLogger {
    GoogleHttpLoadBalancer googleLoadBalancer
    BatchRequest urlMapRequest

    // Pass through objects
    BatchRequest backendServiceRequest
    BatchRequest httpHealthCheckRequest
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(TargetHttpProxy targetHttpProxy, HttpHeaders responseHeaders) throws IOException {
      def urlMapURL = targetHttpProxy?.urlMap
      if (urlMapURL) {
        def urlMapName = Utils.getLocalName(urlMapURL)
        def urlMapCallback = new UrlMapCallback(
            googleLoadBalancer: googleLoadBalancer,
            backendServiceRequest: backendServiceRequest,
            httpHealthCheckRequest: httpHealthCheckRequest,
            groupHealthRequest: groupHealthRequest,
        )
        compute.urlMaps().get(project, urlMapName).queue(urlMapRequest, urlMapCallback)
      }
    }
  }

  class UrlMapCallback<UrlMap> extends JsonBatchCallback<UrlMap> implements FailureLogger {
    GoogleHttpLoadBalancer googleLoadBalancer
    BatchRequest backendServiceRequest

    // Pass through objects
    BatchRequest httpHealthCheckRequest
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(UrlMap urlMap, HttpHeaders responseHeaders) throws IOException {
      // Check that we aren't stomping on our URL map. If we are, log an error.
      if (googleLoadBalancer.defaultService || googleLoadBalancer.hostRules) {
        log.error("Overwriting UrlMap ${urlMap.name}. You may have a TargetHttp(s)Proxy naming collision.")
      }

      Set queuedServices = [] as Set
      // Default service is mandatory.
      def urlMapDefaultService = Utils.getLocalName(urlMap.defaultService)
      def backendServiceCallback = new BackendServiceCallback(
          googleLoadBalancer: googleLoadBalancer,
          httpHealthCheckRequest: httpHealthCheckRequest,
          groupHealthRequest: groupHealthRequest,
      )
      compute.backendServices()
          .get(project, urlMapDefaultService)
          .queue(backendServiceRequest, backendServiceCallback)
      queuedServices.add(urlMapDefaultService)

      googleLoadBalancer.defaultService = new GoogleBackendService(name: urlMapDefaultService)
      urlMap.pathMatchers?.each { PathMatcher pathMatcher ->
        def pathMatchDefaultService = Utils.getLocalName(pathMatcher.defaultService)
        urlMap.hostRules?.each { HostRule hostRule ->
          if (hostRule.pathMatcher && hostRule.pathMatcher == pathMatcher.name) {
            def gPathMatcher = new GooglePathMatcher(
                defaultService: new GoogleBackendService(name: pathMatchDefaultService)
            )
            def gHostRule = new GoogleHostRule(
                hostPatterns: hostRule.hosts,
                pathMatcher: gPathMatcher,
            )
            gPathMatcher.pathRules = pathMatcher.pathRules?.collect { PathRule pathRule ->
              new GooglePathRule(
                  paths: pathRule.paths,
                  backendService: new GoogleBackendService(name: Utils.getLocalName(pathRule.service)),
              )
            }
            googleLoadBalancer.hostRules << gHostRule
          }
        }

        if (!queuedServices.contains(pathMatchDefaultService)) {
          compute.backendServices()
              .get(project, pathMatchDefaultService)
              .queue(backendServiceRequest, backendServiceCallback)
          queuedServices.add(pathMatchDefaultService)
        }
        pathMatcher.pathRules?.each { PathRule pathRule ->
          if (pathRule.service) {
            def serviceName = Utils.getLocalName(pathRule.service)
            if (!queuedServices.contains(serviceName)) {
              compute.backendServices().get(project, serviceName).queue(backendServiceRequest, backendServiceCallback)
              queuedServices.add(serviceName)
            }
          }
        }
      }
    }
  }

  class BackendServiceCallback<BackendService> extends JsonBatchCallback<BackendService> implements FailureLogger {
    GoogleHttpLoadBalancer googleLoadBalancer
    BatchRequest httpHealthCheckRequest
    BatchRequest groupHealthRequest

    @Override
    void onSuccess(BackendService backendService, HttpHeaders responseHeaders) throws IOException {
      def groupHealthCallback = new GroupHealthCallback(
          googleLoadBalancer: googleLoadBalancer,
      )
      Boolean isHttps = backendService.protocol == 'HTTPS'

      // We have to update the backend service objects we created from the UrlMapCallback.
      // The UrlMapCallback knows which backend service is the defaultService, etc and the
      // BackendServiceCallback has the actual serving capacity and server group data.
      List<GoogleBackendService> backendServicesInMap = Utils.getBackendServicesFromHttpLoadBalancerView(googleLoadBalancer.view)
      def backendServicesToUpdate = backendServicesInMap.findAll { it && it.name == backendService.name }
      backendServicesToUpdate.each { GoogleBackendService service ->
        service.backends = backendService.backends?.collect { Backend backend ->
          new GoogleLoadBalancedBackend(
              serverGroupUrl: backend.group,
              policy: GCEUtil.loadBalancingPolicyFromBackend(backend)
          )
        } ?: []
      }

      backendService.backends?.each { Backend backend ->
        def resourceGroup = new ResourceGroupReference()
        resourceGroup.setGroup(backend.group)
        compute.backendServices()
            .getHealth(project, backendService.name, resourceGroup)
            .queue(groupHealthRequest, groupHealthCallback)
      }

      backendService.healthChecks?.each { String healthCheckURL ->
        def healthCheckName = Utils.getLocalName(healthCheckURL)
        if (isHttps) {
          def healthCheckCallback = new HttpsHealthCheckCallback(
              googleBackendServices: backendServicesToUpdate
          )
          compute.httpsHealthChecks().get(project, healthCheckName).queue(httpHealthCheckRequest, healthCheckCallback)
        } else {
          def healthCheckCallback = new HttpHealthCheckCallback(
              googleBackendServices: backendServicesToUpdate
          )
          compute.httpHealthChecks().get(project, healthCheckName).queue(httpHealthCheckRequest, healthCheckCallback)
        }
      }
    }
  }

  class HttpsHealthCheckCallback<HttpsHealthCheck> extends JsonBatchCallback<HttpsHealthCheck> implements FailureLogger {
    List<GoogleBackendService> googleBackendServices

    @Override
    void onSuccess(HttpsHealthCheck httpsHealthCheck, HttpHeaders responseHeaders) throws IOException {
      googleBackendServices.each { GoogleBackendService service ->
        service.healthCheck = new GoogleHealthCheck(
            name: httpsHealthCheck.name,
            requestPath: httpsHealthCheck.requestPath,
            port: httpsHealthCheck.port,
            checkIntervalSec: httpsHealthCheck.checkIntervalSec,
            timeoutSec: httpsHealthCheck.timeoutSec,
            unhealthyThreshold: httpsHealthCheck.unhealthyThreshold,
            healthyThreshold: httpsHealthCheck.healthyThreshold,
        )
      }
    }
  }

  class HttpHealthCheckCallback<HttpHealthCheck> extends JsonBatchCallback<HttpHealthCheck> implements FailureLogger {
    List<GoogleBackendService> googleBackendServices

    @Override
    void onSuccess(HttpHealthCheck httpHealthCheck, HttpHeaders responseHeaders) throws IOException {
      googleBackendServices.each { GoogleBackendService service ->
        service.healthCheck = new GoogleHealthCheck(
            name: httpHealthCheck.name,
            requestPath: httpHealthCheck.requestPath,
            port: httpHealthCheck.port,
            checkIntervalSec: httpHealthCheck.checkIntervalSec,
            timeoutSec: httpHealthCheck.timeoutSec,
            unhealthyThreshold: httpHealthCheck.unhealthyThreshold,
            healthyThreshold: httpHealthCheck.healthyThreshold,
        )
      }
    }
  }

  class GroupHealthCallback<BackendServiceGroupHealth> extends JsonBatchCallback<BackendServiceGroupHealth> implements FailureLogger {
    GoogleHttpLoadBalancer googleLoadBalancer

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
