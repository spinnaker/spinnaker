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
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

@Slf4j
class GoogleHttpLoadBalancerCachingAgent extends AbstractGoogleLoadBalancerCachingAgent {

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

    BatchRequest forwardingRulesRequest = buildBatchRequest()
    BatchRequest targetProxyRequest = buildBatchRequest()
    BatchRequest urlMapRequest = buildBatchRequest()
    BatchRequest backendServiceRequest = buildBatchRequest()
    BatchRequest groupHealthRequest = buildBatchRequest()
    BatchRequest httpHealthCheckRequest = buildBatchRequest()

    ForwardingRuleCallbacks forwardingRuleCallbacks = new ForwardingRuleCallbacks(
      loadBalancers: loadBalancers,
      failedLoadBalancers: failedLoadBalancers,
      targetProxyRequest: targetProxyRequest,
      urlMapRequest: urlMapRequest,
      backendServiceRequest: backendServiceRequest,
      httpHealthCheckRequest: httpHealthCheckRequest,
      groupHealthRequest: groupHealthRequest,
    )

    if (onDemandLoadBalancerName) {
      ForwardingRuleCallbacks.ForwardingRuleSingletonCallback frCallback = forwardingRuleCallbacks.newForwardingRuleSingletonCallback()
      compute.globalForwardingRules().get(project, onDemandLoadBalancerName).queue(forwardingRulesRequest, frCallback)
    } else {
      ForwardingRuleCallbacks.ForwardingRuleListCallback frlCallback = forwardingRuleCallbacks.newForwardingRuleListCallback()
      compute.globalForwardingRules().list(project).queue(forwardingRulesRequest, frlCallback)
    }

    executeIfRequestsAreQueued(forwardingRulesRequest, "HttpLoadBalancerCaching.forwardingRules")
    executeIfRequestsAreQueued(targetProxyRequest, "HttpLoadBalancerCaching.targetProxy")
    executeIfRequestsAreQueued(urlMapRequest, "HttpLoadBalancerCaching.urlMapRequest")
    executeIfRequestsAreQueued(backendServiceRequest, "HttpLoadBalancerCaching.backendService")
    executeIfRequestsAreQueued(httpHealthCheckRequest, "HttpLoadBalancerCaching.httpHealthCheck")
    executeIfRequestsAreQueued(groupHealthRequest, "HttpLoadBalancerCaching.groupHealth")

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
    BatchRequest targetProxyRequest

    // Pass through objects
    BatchRequest urlMapRequest
    BatchRequest backendServiceRequest
    BatchRequest httpHealthCheckRequest
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
        if (forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) != GoogleTargetProxyType.SSL) {
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
          if (forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) != GoogleTargetProxyType.SSL) {
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
        backendServiceRequest: backendServiceRequest,
        httpHealthCheckRequest: httpHealthCheckRequest,
        groupHealthRequest: groupHealthRequest,
        subject: newLoadBalancer.name,
        failedSubjects: failedLoadBalancers,
      )
      def targetHttpsProxyCallback = new TargetHttpsProxyCallback(
        googleLoadBalancer: newLoadBalancer,
        urlMapRequest: urlMapRequest,
        backendServiceRequest: backendServiceRequest,
        httpHealthCheckRequest: httpHealthCheckRequest,
        groupHealthRequest: groupHealthRequest,
        subject: newLoadBalancer.name,
        failedSubjects: failedLoadBalancers,
      )

      switch (Utils.getTargetProxyType(forwardingRule.target)) {
        case GoogleTargetProxyType.HTTP:
          compute.targetHttpProxies().get(project, targetProxyName).queue(targetProxyRequest, targetProxyCallback)
          break
        case GoogleTargetProxyType.HTTPS:
          compute.targetHttpsProxies().get(project, targetProxyName).queue(targetProxyRequest, targetHttpsProxyCallback)
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
  class TargetProxyCallback<TargetHttpProxy> extends JsonBatchCallback<TargetHttpProxy> implements FailedSubjectChronicler {
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
            subject: googleLoadBalancer.name,
            failedSubjects: failedSubjects,
        )
        compute.urlMaps().get(project, urlMapName).queue(urlMapRequest, urlMapCallback)
      }
    }
  }

  class UrlMapCallback<UrlMap> extends JsonBatchCallback<UrlMap> implements FailedSubjectChronicler {
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

      googleLoadBalancer.urlMapName = urlMap.name
      Set queuedServices = [] as Set
      // Default service is mandatory.
      def urlMapDefaultService = Utils.getLocalName(urlMap.defaultService)
      def backendServiceCallback = new BackendServiceCallback(
          googleLoadBalancer: googleLoadBalancer,
          httpHealthCheckRequest: httpHealthCheckRequest,
          groupHealthRequest: groupHealthRequest,
          failedLoadBalancers: failedSubjects
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
            } ?: []
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

  class BackendServiceCallback<BackendService> extends JsonBatchCallback<BackendService> {
    List<String> failedLoadBalancers = []
    GoogleHttpLoadBalancer googleLoadBalancer
    BatchRequest httpHealthCheckRequest
    BatchRequest groupHealthRequest

    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      if (e.getCode() == 404) {
        log.warn(e.getMessage())
        googleLoadBalancer.containsBackendBucket = true;
      } else {
        throw new GoogleOperationException(e.getMessage())
      }
    }

    @Override
    void onSuccess(BackendService backendService, HttpHeaders responseHeaders) throws IOException {
      def groupHealthCallback = new GroupHealthCallback(
          subject: googleLoadBalancer.name,
          failedSubjects: failedLoadBalancers,
          googleLoadBalancer: googleLoadBalancer,
      )
      Boolean isHttps = backendService.protocol == 'HTTPS'

      // We have to update the backend service objects we created from the UrlMapCallback.
      // The UrlMapCallback knows which backend service is the defaultService, etc and the
      // BackendServiceCallback has the actual serving capacity and server group data.
      List<GoogleBackendService> backendServicesInMap = Utils.getBackendServicesFromHttpLoadBalancerView(googleLoadBalancer.view)
      def backendServicesToUpdate = backendServicesInMap.findAll { it && it.name == backendService.name }
      backendServicesToUpdate.each { GoogleBackendService service ->
        service.sessionAffinity = GoogleSessionAffinity.valueOf(backendService.sessionAffinity)
        service.affinityCookieTtlSec = backendService.affinityCookieTtlSec
        service.enableCDN = backendService.enableCDN
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
              subject: googleLoadBalancer.name,
              failedSubjects: failedLoadBalancers,
              googleBackendServices: backendServicesToUpdate
          )
          compute.httpsHealthChecks().get(project, healthCheckName).queue(httpHealthCheckRequest, healthCheckCallback)
        } else {
          def healthCheckCallback = new HttpHealthCheckCallback(
              subject: googleLoadBalancer.name,
              failedSubjects: failedLoadBalancers,
              googleBackendServices: backendServicesToUpdate
          )
          compute.httpHealthChecks().get(project, healthCheckName).queue(httpHealthCheckRequest, healthCheckCallback)
        }
      }
    }
  }

  class HttpsHealthCheckCallback<HttpsHealthCheck> extends JsonBatchCallback<HttpsHealthCheck> implements FailedSubjectChronicler {
    List<GoogleBackendService> googleBackendServices

    @Override
    void onSuccess(HttpsHealthCheck httpsHealthCheck, HttpHeaders responseHeaders) throws IOException {
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
  }

  class HttpHealthCheckCallback<HttpHealthCheck> extends JsonBatchCallback<HttpHealthCheck> implements FailedSubjectChronicler {
    List<GoogleBackendService> googleBackendServices

    @Override
    void onSuccess(HttpHealthCheck httpHealthCheck, HttpHeaders responseHeaders) throws IOException {
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
  }

  class GroupHealthCallback<BackendServiceGroupHealth> extends JsonBatchCallback<BackendServiceGroupHealth> implements FailedSubjectChronicler {
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
