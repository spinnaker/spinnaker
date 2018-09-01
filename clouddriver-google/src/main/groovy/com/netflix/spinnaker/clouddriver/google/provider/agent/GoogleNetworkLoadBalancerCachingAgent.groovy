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
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.google.api.services.compute.model.InstanceReference
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleNetworkLoadBalancer
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.LoadBalancerHealthResolution
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.PaginatedRequest
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.TargetPoolHealthRequest
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

@Slf4j
class GoogleNetworkLoadBalancerCachingAgent extends AbstractGoogleLoadBalancerCachingAgent {

  /**
   * Local cache of targetPoolInstanceHealth keyed by TargetPool name.
   *
   * It turns out that the types in the GCE Batch callbacks aren't the actual Compute
   * types for some reason, which is why this map is String -> Object.
   */
  Map<String, Object> tpNameToInstanceHealthsMap = [:]
  Set<TargetPoolHealthRequest> queuedTpHealthRequests = new HashSet<>()
  List<LoadBalancerHealthResolution> resolutions = []

  GoogleNetworkLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
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
  List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName = null) {
    List<GoogleNetworkLoadBalancer> loadBalancers = []
    List<String> failedLoadBalancers = []

    // Reset the local getHealth caches/queues each caching agent cycle.
    tpNameToInstanceHealthsMap = [:]
    queuedTpHealthRequests = new HashSet<>()
    resolutions = []

    BatchRequest forwardingRulesRequest = buildBatchRequest()
    BatchRequest targetPoolsRequest = buildBatchRequest()
    BatchRequest httpHealthChecksRequest = buildBatchRequest()
    BatchRequest instanceHealthRequest = buildBatchRequest()

    ForwardingRuleCallbacks forwardingRuleCallbacks = new ForwardingRuleCallbacks(
      loadBalancers: loadBalancers,
      failedLoadBalancers: failedLoadBalancers,
      targetPoolsRequest: targetPoolsRequest,
      httpHealthChecksRequest: httpHealthChecksRequest,
      instanceHealthRequest: instanceHealthRequest,
    )

    if (onDemandLoadBalancerName) {
      ForwardingRuleCallbacks.ForwardingRuleSingletonCallback frCallback = forwardingRuleCallbacks.newForwardingRuleSingletonCallback()
      compute.forwardingRules().get(project, region, onDemandLoadBalancerName).queue(forwardingRulesRequest, frCallback)
    } else {
      ForwardingRuleCallbacks.ForwardingRuleListCallback frlCallback = forwardingRuleCallbacks.newForwardingRuleListCallback()
      new PaginatedRequest<ForwardingRuleList>(this) {
        @Override
        protected AbstractGoogleJsonClientRequest<ForwardingRuleList> request(String pageToken) {
          return compute.forwardingRules().list(project, region).setPageToken(pageToken)
        }

        @Override
        protected String getNextPageToken(ForwardingRuleList forwardingRuleList) {
          return forwardingRuleList.getNextPageToken()
        }
      }.queue(forwardingRulesRequest, frlCallback, "NetworkLoadBalancerCaching.forwardingRules");
    }

    executeIfRequestsAreQueued(forwardingRulesRequest, "NetworkLoadBalancerCaching.forwardingRules")
    executeIfRequestsAreQueued(targetPoolsRequest, "NetworkLoadBalancerCaching.targetPools")
    executeIfRequestsAreQueued(httpHealthChecksRequest, "NetworkLoadBalancerCaching.httpHealthChecks")
    executeIfRequestsAreQueued(instanceHealthRequest, "NetworkLoadBalancerCaching.instanceHealth")

    resolutions.each { LoadBalancerHealthResolution resolution ->
      tpNameToInstanceHealthsMap.get(resolution.getTarget()).each { targetPoolHealth ->
        GCEUtil.handleHealthObject(resolution.getGoogleLoadBalancer(), targetPoolHealth)
      }
    }

    return loadBalancers.findAll {!(it.name in failedLoadBalancers)}
  }

  class ForwardingRuleCallbacks {

    List<GoogleNetworkLoadBalancer> loadBalancers
    List<String> failedLoadBalancers = []
    BatchRequest targetPoolsRequest

    // Pass through objects
    BatchRequest httpHealthChecksRequest
    BatchRequest instanceHealthRequest

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
        if (forwardingRule.target) {
          cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
        } else {
          throw new IllegalArgumentException("Not responsible for on demand caching of load balancers without target pools.")
        }
      }
    }

    class ForwardingRuleListCallback<ForwardingRuleList> extends JsonBatchCallback<ForwardingRuleList> implements FailureLogger {

      @Override
      void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
        forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
          if (forwardingRule.target) {
            cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
          }
        }
      }
    }

    void cacheRemainderOfLoadBalancerResourceGraph(ForwardingRule forwardingRule) {
      def newLoadBalancer = new GoogleNetworkLoadBalancer(
        name: forwardingRule.name,
        account: accountName,
        region: region,
        createdTime: Utils.getTimeFromTimestamp(forwardingRule.creationTimestamp),
        ipAddress: forwardingRule.IPAddress,
        ipProtocol: forwardingRule.IPProtocol,
        portRange: forwardingRule.portRange,
        healths: [])
      loadBalancers << newLoadBalancer

      def forwardingRuleTokens = forwardingRule.target.split("/")

      if (forwardingRuleTokens[forwardingRuleTokens.size() - 2] != "targetVpnGateways"
        && forwardingRuleTokens[forwardingRuleTokens.size() - 2] != "targetInstances") {
        def targetPoolName = Utils.getLocalName(forwardingRule.target)
        def targetPoolsCallback = new TargetPoolCallback(
          googleLoadBalancer: newLoadBalancer,
          httpHealthChecksRequest: httpHealthChecksRequest,
          instanceHealthRequest: instanceHealthRequest,
          subject: newLoadBalancer.name,
          failedSubjects: failedLoadBalancers
        )

        compute.targetPools().get(project, region, targetPoolName).queue(targetPoolsRequest, targetPoolsCallback)
      }
    }
  }

  class TargetPoolCallback<TargetPool> extends JsonBatchCallback<TargetPool> implements FailedSubjectChronicler {

    GoogleNetworkLoadBalancer googleLoadBalancer

    BatchRequest httpHealthChecksRequest
    BatchRequest instanceHealthRequest

    @Override
    void onSuccess(TargetPool targetPool, HttpHeaders responseHeaders) throws IOException {
      googleLoadBalancer.targetPool = targetPool?.selfLink
      boolean hasHealthChecks = targetPool?.healthChecks
      targetPool?.healthChecks?.each { def healthCheckUrl ->
        def localHealthCheckName = Utils.getLocalName(healthCheckUrl)
        def httpHealthCheckCallback = new HttpHealthCheckCallback(
            googleLoadBalancer: googleLoadBalancer,
            targetPool: targetPool,
            instanceHealthRequest: instanceHealthRequest,
            subject: googleLoadBalancer.name,
            failedSubjects: failedSubjects
        )

        compute.httpHealthChecks().get(project, localHealthCheckName).queue(httpHealthChecksRequest, httpHealthCheckCallback)
      }
      if (!hasHealthChecks) {
        new TargetPoolInstanceHealthCallInvoker(googleLoadBalancer: googleLoadBalancer,
                                                targetPool: targetPool,
                                                instanceHealthRequest: instanceHealthRequest).doCall()
      }
    }
  }

  class HttpHealthCheckCallback<HttpHealthCheck> extends JsonBatchCallback<HttpHealthCheck> implements FailedSubjectChronicler {

    GoogleNetworkLoadBalancer googleLoadBalancer
    def targetPool

    BatchRequest instanceHealthRequest

    @Override
    void onSuccess(HttpHealthCheck httpHealthCheck, HttpHeaders responseHeaders) throws IOException {
      if (httpHealthCheck) {
        googleLoadBalancer.healthCheck = new GoogleHealthCheck(
            name: httpHealthCheck.name,
            healthCheckType: GoogleHealthCheck.HealthCheckType.HTTP, // Uses HTTP even though it's a network LB -- https://cloud.google.com/compute/docs/load-balancing/network
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

    GoogleNetworkLoadBalancer googleLoadBalancer
    def targetPool

    BatchRequest instanceHealthRequest

    def doCall() {
      def region = Utils.getLocalName(targetPool.region as String)
      def targetPoolName = targetPool.name as String

      targetPool?.instances?.each { String instanceUrl ->
        def instanceReference = new InstanceReference(instance: instanceUrl)
        def instanceHealthCallback = new TargetPoolInstanceHealthCallback(targetPoolName: targetPoolName)

        // Make only the group health request calls we need to.
        TargetPoolHealthRequest tphr = new TargetPoolHealthRequest(project, region, targetPoolName, instanceReference.getInstance())
        if (!queuedTpHealthRequests.contains(tphr)) {
          // The groupHealthCallback updates the local cache along with running handleHealthObject.
          log.debug("Queueing a batch call for getHealth(): {}", tphr)
          queuedTpHealthRequests.add(tphr)
          compute.targetPools()
            .getHealth(project, region, targetPoolName, instanceReference)
            .queue(instanceHealthRequest, instanceHealthCallback)
        } else {
          log.debug("Passing, batch call result cached for getHealth(): {}", tphr)
        }
        resolutions.add(new LoadBalancerHealthResolution(googleLoadBalancer, targetPoolName))
      }
    }
  }

  class TargetPoolInstanceHealthCallback<TargetPoolInstanceHealth> extends JsonBatchCallback<TargetPoolInstanceHealth> {
    String targetPoolName

    @Override
    void onSuccess(TargetPoolInstanceHealth targetPoolInstanceHealth, HttpHeaders responseHeaders) throws IOException {
      if (!tpNameToInstanceHealthsMap.containsKey(targetPoolName)) {
        tpNameToInstanceHealthsMap.put(targetPoolName, [targetPoolInstanceHealth])
      } else {
        tpNameToInstanceHealthsMap.get(targetPoolName) << targetPoolInstanceHealth
      }
    }

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.error "Error while querying target pool instance health: {}", e.getMessage()
    }
  }
}
