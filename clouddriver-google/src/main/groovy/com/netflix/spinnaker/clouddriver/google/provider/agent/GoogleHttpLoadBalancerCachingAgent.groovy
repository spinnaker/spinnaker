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
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.GroupHealthRequest
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.LoadBalancerHealthResolution
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.PaginatedRequest
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
import groovy.util.logging.Slf4j

@Slf4j
class GoogleHttpLoadBalancerCachingAgent extends AbstractGoogleLoadBalancerCachingAgent {

  /**
   * Local cache of BackendServiceGroupHealth keyed by BackendService name.
   *
   * It turns out that the types in the GCE Batch callbacks aren't the actual Compute
   * types for some reason, which is why this map is String -> Object.
   */
  Map<String, List<Object>> bsNameToGroupHealthsMap = [:]
  Set<GroupHealthRequest> queuedBsGroupHealthRequests = new HashSet<>()
  Set<LoadBalancerHealthResolution> resolutions = new HashSet<>()


  GoogleHttpLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
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
  List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName = null) {
    List<GoogleHttpLoadBalancer> loadBalancers = []
    List<String> failedLoadBalancers = []

    GoogleBatchRequest forwardingRulesRequest = buildGoogleBatchRequest()
    GoogleBatchRequest targetProxyRequest = buildGoogleBatchRequest()
    GoogleBatchRequest urlMapRequest = buildGoogleBatchRequest()
    GoogleBatchRequest groupHealthRequest = buildGoogleBatchRequest()

    // Reset the local getHealth caches/queues each caching agent cycle.
    bsNameToGroupHealthsMap = [:]
    queuedBsGroupHealthRequests = new HashSet<>()
    resolutions = new HashSet<>()

    List<BackendService> projectBackendServices = GCEUtil.fetchBackendServices(this, compute, project)
    List<HttpHealthCheck> projectHttpHealthChecks = GCEUtil.fetchHttpHealthChecks(this, compute, project)
    List<HttpsHealthCheck> projectHttpsHealthChecks = GCEUtil.fetchHttpsHealthChecks(this, compute, project)
    List<HealthCheck> projectHealthChecks = GCEUtil.fetchHealthChecks(this, compute, project)

    ForwardingRuleCallbacks forwardingRuleCallbacks = new ForwardingRuleCallbacks(
      loadBalancers: loadBalancers,
      failedLoadBalancers: failedLoadBalancers,
      targetProxyRequest: targetProxyRequest,
      urlMapRequest: urlMapRequest,
      groupHealthRequest: groupHealthRequest,
      projectBackendServices: projectBackendServices,
      projectHttpHealthChecks: projectHttpHealthChecks,
      projectHttpsHealthChecks: projectHttpsHealthChecks,
      projectHealthChecks: projectHealthChecks
    )

    if (onDemandLoadBalancerName) {
      ForwardingRuleCallbacks.ForwardingRuleSingletonCallback frCallback = forwardingRuleCallbacks.newForwardingRuleSingletonCallback()
      forwardingRulesRequest.queue(compute.globalForwardingRules().get(project, onDemandLoadBalancerName), frCallback)
    } else {
      ForwardingRuleCallbacks.ForwardingRuleListCallback frlCallback = forwardingRuleCallbacks.newForwardingRuleListCallback()
      new PaginatedRequest<ForwardingRuleList>(this) {
        @Override
        ComputeRequest<ForwardingRuleList> request(String pageToken) {
          return compute.globalForwardingRules().list(project).setPageToken(pageToken)
        }

        @Override
        String getNextPageToken(ForwardingRuleList forwardingRuleList) {
          return forwardingRuleList.getNextPageToken()
        }
      }.queue(forwardingRulesRequest, frlCallback, "HttpLoadBalancerCaching.forwardingRules")
    }

    executeIfRequestsAreQueued(forwardingRulesRequest, "HttpLoadBalancerCaching.forwardingRules")
    executeIfRequestsAreQueued(targetProxyRequest, "HttpLoadBalancerCaching.targetProxy")
    executeIfRequestsAreQueued(urlMapRequest, "HttpLoadBalancerCaching.urlMapRequest")
    executeIfRequestsAreQueued(groupHealthRequest, "HttpLoadBalancerCaching.groupHealth")

    resolutions.each { LoadBalancerHealthResolution resolution ->
      bsNameToGroupHealthsMap.get(resolution.getTarget()).each { groupHealth ->
        GCEUtil.handleHealthObject(resolution.getGoogleLoadBalancer(), groupHealth)
      }
    }

    // Filter out all LBs that contain backend buckets, since we don't support them in our model.
    loadBalancers = loadBalancers.findAll { !it.containsBackendBucket }

    return loadBalancers.findAll {!(it.name in failedLoadBalancers)}
  }

  @Override
  String determineInstanceKey(GoogleLoadBalancer loadBalancer, GoogleLoadBalancerHealth health) {
    // Http load balancers' region is "global", so we have to determine the instance region from its zone.
    def instanceZone = health.instanceZone
    def instanceRegion = credentials.regionFromZone(instanceZone)

    return Keys.getInstanceKey(accountName, instanceRegion, health.instanceName)
  }

  class ForwardingRuleCallbacks {

    List<GoogleHttpLoadBalancer> loadBalancers
    List<String> failedLoadBalancers = []
    GoogleBatchRequest targetProxyRequest

    // Pass through objects
    GoogleBatchRequest urlMapRequest
    GoogleBatchRequest groupHealthRequest
    List<BackendService> projectBackendServices
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
        if (forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) in [GoogleTargetProxyType.HTTP, GoogleTargetProxyType.HTTPS]) {
          cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
        } else {
          throw new IllegalArgumentException("Not responsible for on demand caching of load balancers without target " +
            "proxy or with SSL proxy type.")
        }
      }
    }

    class ForwardingRuleListCallback<ForwardingRuleList> extends JsonBatchCallback<ForwardingRuleList> implements FailureLogger {

      @Override
      void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
        forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
          if (forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) in [GoogleTargetProxyType.HTTP, GoogleTargetProxyType.HTTPS]) {
            cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
          }
        }
      }
    }

    void cacheRemainderOfLoadBalancerResourceGraph(ForwardingRule forwardingRule) {
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

      def targetProxyName = Utils.getLocalName(forwardingRule.target)
      def targetProxyCallback = new TargetProxyCallback(
        googleLoadBalancer: newLoadBalancer,
        urlMapRequest: urlMapRequest,
        groupHealthRequest: groupHealthRequest,
        subject: newLoadBalancer.name,
        failedSubjects: failedLoadBalancers,
        projectBackendServices: projectBackendServices,
        projectHttpHealthChecks: projectHttpHealthChecks,
        projectHttpsHealthChecks: projectHttpsHealthChecks,
        projectHealthChecks: projectHealthChecks
      )
      def targetHttpsProxyCallback = new TargetHttpsProxyCallback(
        googleLoadBalancer: newLoadBalancer,
        urlMapRequest: urlMapRequest,
        groupHealthRequest: groupHealthRequest,
        subject: newLoadBalancer.name,
        failedSubjects: failedLoadBalancers,
        projectBackendServices: projectBackendServices,
        projectHttpHealthChecks: projectHttpHealthChecks,
        projectHttpsHealthChecks: projectHttpsHealthChecks,
        projectHealthChecks: projectHealthChecks
      )

      switch (Utils.getTargetProxyType(forwardingRule.target)) {
        case GoogleTargetProxyType.HTTP:
          targetProxyRequest.queue(compute.targetHttpProxies().get(project, targetProxyName), targetProxyCallback)
          break
        case GoogleTargetProxyType.HTTPS:
          targetProxyRequest.queue(compute.targetHttpsProxies().get(project, targetProxyName), targetHttpsProxyCallback)
          break
        default:
          log.info("Non-Http target type found for global forwarding rule ${forwardingRule.name}")
          break
      }
    }
  }

  // Note: The TargetProxyCallbacks assume that each proxy points to a unique urlMap.
  class TargetHttpsProxyCallback<TargetHttpsProxy> extends JsonBatchCallback<TargetHttpsProxy> implements FailedSubjectChronicler {
    GoogleHttpLoadBalancer googleLoadBalancer
    GoogleBatchRequest urlMapRequest

    // Pass through objects
    GoogleBatchRequest groupHealthRequest
    List<BackendService> projectBackendServices
    List<HttpHealthCheck> projectHttpHealthChecks
    List<HttpsHealthCheck> projectHttpsHealthChecks
    List<HealthCheck> projectHealthChecks

    @Override
    void onSuccess(TargetHttpsProxy targetHttpsProxy, HttpHeaders responseHeaders) throws IOException {
      // SslCertificates is a required field for TargetHttpsProxy, and contains exactly one cert.
      googleLoadBalancer.certificate = Utils.getLocalName((targetHttpsProxy.getSslCertificates()[0]))

      def urlMapURL = targetHttpsProxy?.urlMap
      if (urlMapURL) {
        def urlMapName = Utils.getLocalName(urlMapURL)
        def urlMapCallback = new UrlMapCallback(
            googleLoadBalancer: googleLoadBalancer,
            groupHealthRequest: groupHealthRequest,
            subject: googleLoadBalancer.name,
            failedSubjects: failedSubjects,
            projectBackendServices: projectBackendServices,
            projectHttpHealthChecks: projectHttpHealthChecks,
            projectHttpsHealthChecks: projectHttpsHealthChecks,
            projectHealthChecks: projectHealthChecks
        )
        urlMapRequest.queue(compute.urlMaps().get(project, urlMapName), urlMapCallback)
      }
    }
  }

  // Note: The TargetProxyCallbacks assume that each proxy points to a unique urlMap.
  class TargetProxyCallback<TargetHttpProxy> extends JsonBatchCallback<TargetHttpProxy> implements FailedSubjectChronicler {
    GoogleHttpLoadBalancer googleLoadBalancer
    GoogleBatchRequest urlMapRequest

    // Pass through objects
    GoogleBatchRequest groupHealthRequest
    List<BackendService> projectBackendServices
    List<HttpHealthCheck> projectHttpHealthChecks
    List<HttpsHealthCheck> projectHttpsHealthChecks
    List<HealthCheck> projectHealthChecks

    @Override
    void onSuccess(TargetHttpProxy targetHttpProxy, HttpHeaders responseHeaders) throws IOException {
      def urlMapURL = targetHttpProxy?.urlMap
      if (urlMapURL) {
        def urlMapName = Utils.getLocalName(urlMapURL)
        def urlMapCallback = new UrlMapCallback(
            googleLoadBalancer: googleLoadBalancer,
            groupHealthRequest: groupHealthRequest,
            subject: googleLoadBalancer.name,
            failedSubjects: failedSubjects,
            projectBackendServices: projectBackendServices,
            projectHttpHealthChecks: projectHttpHealthChecks,
            projectHttpsHealthChecks: projectHttpsHealthChecks,
            projectHealthChecks: projectHealthChecks
        )
        urlMapRequest.queue(compute.urlMaps().get(project, urlMapName), urlMapCallback)
      }
    }
  }

  class UrlMapCallback<UrlMap> extends JsonBatchCallback<UrlMap> implements FailedSubjectChronicler {
    GoogleHttpLoadBalancer googleLoadBalancer
    List<BackendService> projectBackendServices
    List<HttpHealthCheck> projectHttpHealthChecks
    List<HttpsHealthCheck> projectHttpsHealthChecks
    List<HealthCheck> projectHealthChecks
    GoogleBatchRequest groupHealthRequest

    @Override
    void onSuccess(UrlMap urlMap, HttpHeaders responseHeaders) throws IOException {
      // Check that we aren't stomping on our URL map. If we are, log an error.
      if (googleLoadBalancer.defaultService || googleLoadBalancer.hostRules) {
        log.error("Overwriting UrlMap ${urlMap.name}. You may have a TargetHttp(s)Proxy naming collision.")
      }

      googleLoadBalancer.urlMapName = urlMap.name
      // Queue up the backend services to process.
      Set queuedServices = [] as Set

      // Default service is mandatory.
      def urlMapDefaultService = Utils.getLocalName(urlMap.defaultService)
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
            } ?: []
            googleLoadBalancer.hostRules << gHostRule
          }
        }

        if (!queuedServices.contains(pathMatchDefaultService)) {
          queuedServices.add(pathMatchDefaultService)
        }
        pathMatcher.pathRules?.each { PathRule pathRule ->
          if (pathRule.service) {
            def serviceName = Utils.getLocalName(pathRule.service)
            if (!queuedServices.contains(serviceName)) {
              queuedServices.add(serviceName)
            }
          }
        }
      }

      // Process queued backend services.
      queuedServices?.each { backendServiceName ->
        BackendService service = projectBackendServices?.find { bs -> Utils.getLocalName(bs.getName()) == backendServiceName }
        handleBackendService(service, googleLoadBalancer, projectHttpHealthChecks, projectHttpsHealthChecks, projectHealthChecks, groupHealthRequest)
      }
    }
  }

  private void handleBackendService(BackendService backendService,
                                    GoogleHttpLoadBalancer googleHttpLoadBalancer,
                                    List<HttpHealthCheck> httpHealthChecks,
                                    List<HttpsHealthCheck> httpsHealthChecks,
                                    List<HealthCheck> healthChecks,
                                    GoogleBatchRequest groupHealthRequest) {
    if (!backendService) {
      return
    }
    def groupHealthCallback = new GroupHealthCallback(backendServiceName: backendService.name)
    Boolean isHttps = backendService.protocol == 'HTTPS'

    // We have to update the backend service objects we created from the UrlMapCallback.
    // The UrlMapCallback knows which backend service is the defaultService, etc and the
    // BackendServiceCallback has the actual serving capacity and server group data.
    List<GoogleBackendService> backendServicesInMap = Utils.getBackendServicesFromHttpLoadBalancerView(googleHttpLoadBalancer.view)
    List<GoogleBackendService> backendServicesToUpdate = backendServicesInMap.findAll { it && it.name == backendService.name }
    backendServicesToUpdate.each { GoogleBackendService service ->
      service.sessionAffinity = GoogleSessionAffinity.valueOf(backendService.sessionAffinity)
      service.affinityCookieTtlSec = backendService.affinityCookieTtlSec
      service.enableCDN = backendService.enableCDN
      service.portName = backendService.portName ?: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME
      service.connectionDrainingTimeoutSec = backendService.connectionDraining?.drainingTimeoutSec ?: 0
      // Note: It's possible for a backend service to have backends that point to a null group.
      service.backends = backendService.backends?.findAll { Backend backend -> backend.group }?.collect { Backend backend ->
        new GoogleLoadBalancedBackend(
          serverGroupUrl: backend.group,
          policy: GCEUtil.loadBalancingPolicyFromBackend(backend)
        )
      } ?: []
    }

    // Note: It's possible for a backend service to have backends that point to a null group.
    backendService.backends?.findAll { Backend backend -> backend.group }?.each { Backend backend ->
      def resourceGroup = new ResourceGroupReference()
      resourceGroup.setGroup(backend.group)

      // Make only the group health request calls we need to.
      GroupHealthRequest ghr = new GroupHealthRequest(project, backendService.name as String, resourceGroup.getGroup())
      if (!queuedBsGroupHealthRequests.contains(ghr)) {
        // The groupHealthCallback updates the local cache.
        log.debug("Queueing a batch call for getHealth(): {}", ghr)
        queuedBsGroupHealthRequests.add(ghr)
        groupHealthRequest
          .queue(compute.backendServices().getHealth(project, backendService.name as String, resourceGroup),
          groupHealthCallback)
      } else {
        log.debug("Passing, batch call result cached for getHealth(): {}", ghr)
      }
      resolutions.add(new LoadBalancerHealthResolution(googleHttpLoadBalancer, backendService.name))
    }

    backendService.healthChecks?.each { String healthCheckURL ->
      def healthCheckName = Utils.getLocalName(healthCheckURL)
      def healthCheckType = Utils.getHealthCheckType(healthCheckURL)
      switch (healthCheckType) {
        case "httpHealthChecks":
          HttpHealthCheck httpHealthCheck = httpHealthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          handleHttpHealthCheck(httpHealthCheck, backendServicesToUpdate)
          break
        case "httpsHealthChecks":
          HttpsHealthCheck httpsHealthCheck = httpsHealthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          handleHttpsHealthCheck(httpsHealthCheck, backendServicesToUpdate)
          break
        case "healthChecks":
          HealthCheck healthCheck = healthChecks.find { hc -> Utils.getLocalName(hc.getName()) == healthCheckName }
          handleHealthCheck(healthCheck, backendServicesToUpdate)
          break
        default:
          log.warn("Unknown health check type for health check named: ${healthCheckName}. Not queueing any batch requests.")
          break
      }
    }
  }

  private static void handleHttpHealthCheck(HttpHealthCheck httpHealthCheck, List<GoogleBackendService> googleBackendServices) {
    if (!httpHealthCheck) {
      return
    }
    googleBackendServices.each { GoogleBackendService service ->
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
  }

  private static void handleHttpsHealthCheck(HttpsHealthCheck httpsHealthCheck, List<GoogleBackendService> googleBackendServices) {
    if (!httpsHealthCheck) {
      return
    }
    googleBackendServices.each { GoogleBackendService service ->
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
  }

  private static void handleHealthCheck(HealthCheck healthCheck, List<GoogleBackendService> googleBackendServices) {
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
      googleBackendServices?.each { googleBackendService ->
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

  class GroupHealthCallback<BackendServiceGroupHealth> extends JsonBatchCallback<BackendServiceGroupHealth> {
    String backendServiceName

    /**
     * Tolerate of the group health calls failing. Spinnaker reports empty load balancer healths as 'unknown'.
     * If healthStatus is null in the onSuccess() function, the same state is reported, so this shouldn't cause issues.
     */
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.debug("Failed backend service group health call for backend service ${backendServiceName} for Http load balancer." +
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
