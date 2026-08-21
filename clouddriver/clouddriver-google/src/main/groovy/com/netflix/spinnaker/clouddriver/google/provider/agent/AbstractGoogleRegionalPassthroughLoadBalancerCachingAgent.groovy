/*
 * Copyright 2026 Harness, Inc.
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
import com.google.api.services.compute.model.Backend
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.BackendServiceGroupHealth
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.ForwardingRuleList
import com.google.api.services.compute.model.ResourceGroupReference
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancedBackend
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSessionAffinity
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.GroupHealthRequest
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.LoadBalancerHealthResolution
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.PaginatedRequest
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory

/**
 * Shared regional passthrough cache graph walk for internal and external Network Load Balancers.
 *
 * <p>Both families start at a regional forwarding rule that points directly at a regional backend
 * service, then enrich that backend service with health checks and backend group health. Subclasses
 * own the scheme/protocol predicate and health-check source.
 */
@Slf4j
abstract class AbstractGoogleRegionalPassthroughLoadBalancerCachingAgent<T extends GoogleLoadBalancer>
  extends AbstractGoogleLoadBalancerCachingAgent {

  Map<String, List<BackendServiceGroupHealth>> backendServiceNameToGroupHealths = [:]
  Set<GroupHealthRequest> queuedBackendServiceGroupHealthRequests = new HashSet<>()
  Set<LoadBalancerHealthResolution> resolutions = new HashSet<>()

  AbstractGoogleRegionalPassthroughLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
                                                            GoogleNamedAccountCredentials credentials,
                                                            ObjectMapper objectMapper,
                                                            Registry registry,
                                                            String region) {
    super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region)
  }

  @Override
  Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    []
  }

  @Override
  List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName = null) {
    List<T> loadBalancers = []
    List<String> failedLoadBalancers = []

    GoogleBatchRequest forwardingRulesRequest = buildGoogleBatchRequest()
    GoogleBatchRequest groupHealthRequest = buildGoogleBatchRequest()

    // These callbacks run across staged batches; reset all callback-owned state for each refresh.
    backendServiceNameToGroupHealths = [:]
    queuedBackendServiceGroupHealthRequests = new HashSet<>()
    resolutions = new HashSet<>()

    List<BackendService> projectRegionBackendServices = GCEUtil.fetchRegionBackendServices(this, compute, project, region)
    def healthCheckContext = fetchHealthCheckContext()

    ForwardingRuleCallbacks forwardingRuleCallbacks = new ForwardingRuleCallbacks(
      loadBalancers: loadBalancers,
      failedLoadBalancers: failedLoadBalancers,
      groupHealthRequest: groupHealthRequest,
      projectRegionBackendServices: projectRegionBackendServices,
      healthCheckContext: healthCheckContext
    )

    if (onDemandLoadBalancerName) {
      ForwardingRuleCallbacks.ForwardingRuleSingletonCallback frCallback = forwardingRuleCallbacks.newForwardingRuleSingletonCallback()
      forwardingRulesRequest.queue(compute.forwardingRules().get(project, region, onDemandLoadBalancerName), frCallback)
    } else {
      ForwardingRuleCallbacks.ForwardingRuleListCallback frlCallback = forwardingRuleCallbacks.newForwardingRuleListCallback()
      new PaginatedRequest<ForwardingRuleList>(this) {
        @Override
        ComputeRequest<ForwardingRuleList> request(String pageToken) {
          return compute.forwardingRules().list(project, region).setPageToken(pageToken)
        }

        @Override
        String getNextPageToken(ForwardingRuleList forwardingRuleList) {
          return forwardingRuleList.getNextPageToken()
        }
      }.queue(forwardingRulesRequest, frlCallback, "${instrumentationPrefix()}.forwardingRules")
    }

    executeIfRequestsAreQueued(forwardingRulesRequest, "${instrumentationPrefix()}.forwardingRules")
    executeIfRequestsAreQueued(groupHealthRequest, "${instrumentationPrefix()}.groupHealth")

    // Two-phase health resolution: the graph walk above only queued getHealth() batches and recorded
    // a resolution per backend service; the GroupHealthCallback fills
    // backendServiceNameToGroupHealths as those batches execute, so health can only be applied
    // here, once every batch has completed.
    resolutions.each { LoadBalancerHealthResolution resolution ->
      (backendServiceNameToGroupHealths.get(resolution.getTarget()) ?: []).each { groupHealth ->
        GCEUtil.handleHealthObject(resolution.getGoogleLoadBalancer(), groupHealth)
      }
    }

    return loadBalancers.findAll { !(it.name in failedLoadBalancers) }
  }

  /** Metric/instrumentation prefix used to tag this agent's batched Compute calls. */
  abstract String instrumentationPrefix()

  /**
   * Returns true when this scheme owns the forwarding rule. The base only walks owned rules, so this
   * guard keeps the INTERNAL and EXTERNAL passthrough graphs (which can share a backend-service name)
   * from caching each other.
   */
  abstract boolean ownsForwardingRule(ForwardingRule forwardingRule)

  /** Creates the scheme-specific model and seeds its common forwarding-rule fields. */
  abstract T newLoadBalancer(ForwardingRule forwardingRule)

  /**
   * Fetches the opaque health-check lookup that {@link #attachHealthChecks} consumes, read once per
   * refresh. The shape is subclass-private and the base never inspects it: internal passthrough
   * returns a map of legacy HTTP/HTTPS/generic health checks, while regional external network
   * returns a flat list of regional health checks.
   */
  abstract Object fetchHealthCheckContext()

  /**
   * Resolves the backend service's health checks against {@code healthCheckContext} (the value
   * returned by {@link #fetchHealthCheckContext}) and attaches them to the model's backend service.
   */
  abstract void attachHealthChecks(BackendService backendService, T googleLoadBalancer, Object healthCheckContext)

  /** Message thrown when an on-demand refresh is asked to cache a rule this scheme does not own. */
  abstract String wrongSchemeMessage()

  class ForwardingRuleCallbacks {
    List<T> loadBalancers
    List<String> failedLoadBalancers = []

    GoogleBatchRequest groupHealthRequest
    List<BackendService> projectRegionBackendServices
    Object healthCheckContext

    ForwardingRuleSingletonCallback newForwardingRuleSingletonCallback() {
      return new ForwardingRuleSingletonCallback()
    }

    ForwardingRuleListCallback newForwardingRuleListCallback() {
      return new ForwardingRuleListCallback()
    }

    class ForwardingRuleSingletonCallback extends JsonBatchCallback<ForwardingRule> {

      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        if (e.code != 404) {
          def errorJson = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e)
          log.error errorJson
        }
      }

      @Override
      void onSuccess(ForwardingRule forwardingRule, HttpHeaders responseHeaders) throws IOException {
        if (ownsForwardingRule(forwardingRule)) {
          cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
        } else {
          throw new IllegalArgumentException(wrongSchemeMessage())
        }
      }
    }

    class ForwardingRuleListCallback extends JsonBatchCallback<ForwardingRuleList> implements FailureLogger {

      @Override
      void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
        forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
          if (ownsForwardingRule(forwardingRule)) {
            cacheRemainderOfLoadBalancerResourceGraph(forwardingRule)
          }
        }
      }

      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        LoggerFactory.getLogger(this.class).error e.getMessage()
      }
    }

    void cacheRemainderOfLoadBalancerResourceGraph(ForwardingRule forwardingRule) {
      T newLoadBalancer = newLoadBalancer(forwardingRule)
      loadBalancers << newLoadBalancer

      def backendServiceName = Utils.getLocalName(forwardingRule.backendService)
      BackendService backendService = projectRegionBackendServices?.find { BackendService bs -> bs.getName() == backendServiceName }
      if (backendService == null) {
        log.warn("Failed to read a component of subject ${newLoadBalancer.name}. Could not find BackendService ${backendServiceName}.\n"
          + "Reporting it as 'Failed' to the caching agent.")
        failedLoadBalancers << newLoadBalancer.name
      } else {
        handleBackendService(backendService, newLoadBalancer, healthCheckContext, groupHealthRequest)
      }
    }
  }

  private void handleBackendService(BackendService backendService,
                                    T googleLoadBalancer,
                                    Object healthCheckContext,
                                    GoogleBatchRequest groupHealthRequest) {
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

      GroupHealthRequest groupHealthRequestKey = new GroupHealthRequest(project, backendService.name as String, resourceGroup.getGroup())
      // A regional forwarding rule can share backend service/group data across listeners.
      if (!queuedBackendServiceGroupHealthRequests.contains(groupHealthRequestKey)) {
        log.debug("Queueing a batch call for getHealth(): {}", groupHealthRequestKey)
        queuedBackendServiceGroupHealthRequests.add(groupHealthRequestKey)
        groupHealthRequest
          .queue(compute.regionBackendServices().getHealth(project, region, backendService.name as String, resourceGroup),
            groupHealthCallback)
      } else {
        log.debug("Passing, batch call result cached for getHealth(): {}", groupHealthRequestKey)
      }
      resolutions.add(new LoadBalancerHealthResolution(googleLoadBalancer, backendService.name))
    }

    attachHealthChecks(backendService, googleLoadBalancer, healthCheckContext)
  }

  class GroupHealthCallback<BackendServiceGroupHealth> extends JsonBatchCallback<BackendServiceGroupHealth> {
    String backendServiceName

    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.debug("Failed backend service group health call for backend service ${backendServiceName} for regional passthrough load balancer." +
        " The platform error message was:\n ${e.getMessage()}.")
    }

    @Override
    void onSuccess(BackendServiceGroupHealth backendServiceGroupHealth, HttpHeaders responseHeaders) throws IOException {
      if (!backendServiceNameToGroupHealths.containsKey(backendServiceName)) {
        backendServiceNameToGroupHealths.put(backendServiceName, [backendServiceGroupHealth])
      } else {
        backendServiceNameToGroupHealths.get(backendServiceName) << backendServiceGroupHealth
      }
    }
  }
}
