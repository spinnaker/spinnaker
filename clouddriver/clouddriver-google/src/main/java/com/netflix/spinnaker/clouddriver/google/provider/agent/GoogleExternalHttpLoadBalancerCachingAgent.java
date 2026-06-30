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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.*;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*;
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.GroupHealthRequest;
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.LoadBalancerHealthResolution;
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.PaginatedRequest;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches regional external Application Load Balancers backed by the Compute `EXTERNAL_MANAGED`
 * resource graph.
 *
 * <p>GCP models each listener as a regional forwarding rule. Spinnaker exposes the URL map as the
 * logical load balancer, so this agent walks forwarding rule -> regional target proxy -> regional
 * URL map -> regional backend services and health checks.
 */
public class GoogleExternalHttpLoadBalancerCachingAgent
    extends AbstractGoogleLoadBalancerCachingAgent {
  private static final Logger log = LoggerFactory.getLogger(GoogleExternalHttpLoadBalancer.class);

  public GoogleExternalHttpLoadBalancerCachingAgent(
      String clouddriverUserAgentApplicationName,
      GoogleNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region);
  }

  @Override
  public List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName) {
    List<GoogleExternalHttpLoadBalancer> loadBalancers = new ArrayList<>();
    List<String> failedLoadBalancers = new ArrayList<>();

    GoogleBatchRequest forwardingRulesRequest = buildGoogleBatchRequest();
    GoogleBatchRequest targetProxyRequest = buildGoogleBatchRequest();
    GoogleBatchRequest urlMapRequest = buildGoogleBatchRequest();
    GoogleBatchRequest groupHealthRequest = buildGoogleBatchRequest();

    bsNameToGroupHealthsMap = new HashMap<>();
    queuedBsGroupHealthRequests = new HashSet<>();
    resolutions = new HashSet<>();

    try {
      // Backend services and health checks are shared by every URL map in the region. Reading them
      // once keeps the per-listener graph walk cheap while preserving the full backend model.
      List<BackendService> projectBackendServices =
          GCEUtil.fetchRegionBackendServices(this, getCompute(), getProject(), getRegion());
      List<HealthCheck> projectHealthChecks =
          GCEUtil.fetchRegionalHealthChecks(this, getCompute(), getProject(), getRegion());

      ForwardingRuleCallbacks forwardingRuleCallbacks =
          new ForwardingRuleCallbacks(
              loadBalancers,
              failedLoadBalancers,
              targetProxyRequest,
              urlMapRequest,
              groupHealthRequest,
              projectBackendServices,
              projectHealthChecks);
      if (onDemandLoadBalancerName != null) {
        // On-demand requests are routed by forwarding-rule name. Returning an empty list for a 404
        // tells the cache layer to evict stale data; throwing for the wrong scheme lets the owning
        // regional HTTP-family agent handle its own forwarding rule.
        forwardingRulesRequest.queue(
            getCompute().forwardingRules().get(getProject(), getRegion(), onDemandLoadBalancerName),
            forwardingRuleCallbacks.newForwardingRuleSingletonCallback());
      } else {
        new PaginatedRequest<ForwardingRuleList>(this) {
          @Override
          public ComputeRequest<ForwardingRuleList> request(String pageToken) {
            try {
              return getCompute()
                  .forwardingRules()
                  .list(getProject(), getRegion())
                  .setPageToken(pageToken);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          }

          @Override
          public String getNextPageToken(ForwardingRuleList forwardingRuleList) {
            return forwardingRuleList.getNextPageToken();
          }
        }.queue(
            forwardingRulesRequest,
            forwardingRuleCallbacks.newForwardingRuleListCallback(),
            "ExternalHttpLoadBalancerCaching.forwardingRules");
      }

      executeIfRequestsAreQueued(
          forwardingRulesRequest, "ExternalHttpLoadBalancerCaching.forwardingRules");
      executeIfRequestsAreQueued(targetProxyRequest, "ExternalHttpLoadBalancerCaching.targetProxy");
      executeIfRequestsAreQueued(urlMapRequest, "ExternalHttpLoadBalancerCaching.urlMapRequest");
      executeIfRequestsAreQueued(groupHealthRequest, "ExternalHttpLoadBalancerCaching.groupHealth");

      for (LoadBalancerHealthResolution resolution : resolutions) {
        for (Object groupHealth :
            bsNameToGroupHealthsMap.getOrDefault(resolution.getTarget(), Collections.emptyList())) {
          GCEUtil.handleHealthObject(resolution.getGoogleLoadBalancer(), groupHealth);
        }
      }

      return loadBalancers.stream()
          .filter(lb -> !failedLoadBalancers.contains(lb.getName()))
          .collect(toList());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public List<GoogleLoadBalancer> constructLoadBalancers() {
    return constructLoadBalancers(null);
  }

  @Override
  public String determineInstanceKey(
      GoogleLoadBalancer loadBalancer, GoogleLoadBalancerHealth health) {
    String instanceZone = health.getInstanceZone();
    String instanceRegion = getCredentials().regionFromZone(instanceZone);
    return com.netflix.spinnaker.clouddriver.google.cache.Keys.getInstanceKey(
        getAccountName(), instanceRegion, health.getInstanceName());
  }

  static boolean isExternalManagedHttpForwardingRule(ForwardingRule forwardingRule) {
    return GoogleLoadBalancerCacheSupport.isRegionalManagedHttpForwardingRule(
        forwardingRule, "EXTERNAL_MANAGED");
  }

  static String getFirstSslCertificateForExternalManaged(TargetHttpsProxy targetHttpsProxy) {
    if (targetHttpsProxy == null
        || targetHttpsProxy.getSslCertificates() == null
        || targetHttpsProxy.getSslCertificates().isEmpty()) {
      return null;
    }
    String certificate = targetHttpsProxy.getSslCertificates().get(0);
    String certificateManagerCertificate =
        GCEUtil.normalizeRegionalCertificateManagerCertificate(certificate);
    return certificateManagerCertificate != null
        ? certificateManagerCertificate
        : GCEUtil.getLocalName(certificate);
  }

  private Map<String, List<BackendServiceGroupHealth>> bsNameToGroupHealthsMap = new HashMap<>();
  private Set<GroupHealthRequest> queuedBsGroupHealthRequests = new HashSet<>();
  private Set<LoadBalancerHealthResolution> resolutions = new HashSet<>();

  public class ForwardingRuleCallbacks {
    private final List<GoogleExternalHttpLoadBalancer> loadBalancers;
    private final List<String> failedLoadBalancers;
    private final GoogleBatchRequest targetProxyRequest;
    private final GoogleBatchRequest urlMapRequest;
    private final GoogleBatchRequest groupHealthRequest;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;

    ForwardingRuleCallbacks(
        List<GoogleExternalHttpLoadBalancer> loadBalancers,
        List<String> failedLoadBalancers,
        GoogleBatchRequest targetProxyRequest,
        GoogleBatchRequest urlMapRequest,
        GoogleBatchRequest groupHealthRequest,
        List<BackendService> projectBackendServices,
        List<HealthCheck> projectHealthChecks) {
      this.loadBalancers = loadBalancers;
      this.failedLoadBalancers = failedLoadBalancers;
      this.targetProxyRequest = targetProxyRequest;
      this.urlMapRequest = urlMapRequest;
      this.groupHealthRequest = groupHealthRequest;
      this.projectBackendServices = projectBackendServices;
      this.projectHealthChecks = projectHealthChecks;
    }

    public ForwardingRuleSingletonCallback newForwardingRuleSingletonCallback() {
      return new ForwardingRuleSingletonCallback();
    }

    public ForwardingRuleListCallback newForwardingRuleListCallback() {
      return new ForwardingRuleListCallback();
    }

    public void cacheRemainderOfLoadBalancerResourceGraph(ForwardingRule forwardingRule) {
      GoogleExternalHttpLoadBalancer loadBalancer = new GoogleExternalHttpLoadBalancer();
      loadBalancer.setName(forwardingRule.getName());
      loadBalancer.setAccount(getAccountName());
      loadBalancer.setRegion(Utils.getLocalName(forwardingRule.getRegion()));
      loadBalancer.setCreatedTime(
          Utils.getTimeFromTimestamp(forwardingRule.getCreationTimestamp()));
      loadBalancer.setIpAddress(forwardingRule.getIPAddress());
      loadBalancer.setIpProtocol(forwardingRule.getIPProtocol());
      loadBalancer.setPortRange(forwardingRule.getPortRange());
      loadBalancer.setNetwork(forwardingRule.getNetwork());
      loadBalancer.setHealths(new ArrayList<>());
      loadBalancer.setHostRules(new ArrayList<>());
      loadBalancers.add(loadBalancer);

      String targetProxyName = Utils.getLocalName(forwardingRule.getTarget());
      try {
        switch (Utils.getTargetProxyType(forwardingRule.getTarget())) {
          case HTTP:
            targetProxyRequest.queue(
                getCompute()
                    .regionTargetHttpProxies()
                    .get(getProject(), getRegion(), targetProxyName),
                new TargetProxyCallback(
                    loadBalancer,
                    urlMapRequest,
                    groupHealthRequest,
                    projectBackendServices,
                    projectHealthChecks,
                    loadBalancer.getName(),
                    failedLoadBalancers));
            break;
          case HTTPS:
            targetProxyRequest.queue(
                getCompute()
                    .regionTargetHttpsProxies()
                    .get(getProject(), getRegion(), targetProxyName),
                new TargetHttpsProxyCallback(
                    loadBalancer,
                    urlMapRequest,
                    groupHealthRequest,
                    projectBackendServices,
                    projectHealthChecks,
                    loadBalancer.getName(),
                    failedLoadBalancers));
            break;
          default:
            failedLoadBalancers.add(loadBalancer.getName());
            log.debug(
                "Unsupported target proxy for regional external HTTP load balancer {}",
                forwardingRule.getName());
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public class ForwardingRuleSingletonCallback extends JsonBatchCallback<ForwardingRule> {
      @Override
      public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        if (e.getCode() != 404) {
          // A 404 means the on-demand item was deleted and should be evicted. Other read failures
          // leave ownership unknown, so fail the cycle instead of publishing an empty cache result.
          log.error(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e));
          throw new IOException(e.getMessage());
        }
      }

      @Override
      public void onSuccess(ForwardingRule forwardingRule, HttpHeaders responseHeaders)
          throws IOException {
        if (isExternalManagedHttpForwardingRule(forwardingRule)) {
          cacheRemainderOfLoadBalancerResourceGraph(forwardingRule);
        } else {
          throw new IllegalArgumentException(
              "Not responsible for on demand caching of load balancers without "
                  + "EXTERNAL_MANAGED HTTP(S) target proxy.");
        }
      }
    }

    public class ForwardingRuleListCallback extends JsonBatchCallback<ForwardingRuleList> {
      @Override
      public void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) {
        if (forwardingRuleList.getItems() == null) {
          return;
        }
        forwardingRuleList.getItems().stream()
            .filter(GoogleExternalHttpLoadBalancerCachingAgent::isExternalManagedHttpForwardingRule)
            .forEach(ForwardingRuleCallbacks.this::cacheRemainderOfLoadBalancerResourceGraph);
      }

      @Override
      public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        // The forwarding-rule list is the authoritative source for this regional cache cycle.
        // Failing open would look like all external managed LBs disappeared.
        log.error(e.getMessage());
        throw new IOException(e.getMessage());
      }
    }
  }

  abstract static class BaseCallback<T> extends JsonBatchCallback<T> {
    List<String> failedSubjects;
    String subject;

    BaseCallback(String subject, List<String> failedSubjects) {
      this.subject = subject;
      this.failedSubjects = failedSubjects;
    }

    @Override
    public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
      log.warn(
          "Failed to read a component of regional external HTTP load balancer {}. "
              + "The platform error message was:\n{}. Reporting it as failed to the caching agent.",
          subject,
          e.getMessage());
      failedSubjects.add(subject);
    }
  }

  public class TargetProxyCallback extends BaseCallback<TargetHttpProxy> {
    private final GoogleExternalHttpLoadBalancer loadBalancer;
    private final GoogleBatchRequest urlMapRequest;
    private final GoogleBatchRequest groupHealthRequest;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;

    TargetProxyCallback(
        GoogleExternalHttpLoadBalancer loadBalancer,
        GoogleBatchRequest urlMapRequest,
        GoogleBatchRequest groupHealthRequest,
        List<BackendService> projectBackendServices,
        List<HealthCheck> projectHealthChecks,
        String subject,
        List<String> failedSubjects) {
      super(subject, failedSubjects);
      this.loadBalancer = loadBalancer;
      this.urlMapRequest = urlMapRequest;
      this.groupHealthRequest = groupHealthRequest;
      this.projectBackendServices = projectBackendServices;
      this.projectHealthChecks = projectHealthChecks;
    }

    @Override
    public void onSuccess(TargetHttpProxy targetHttpProxy, HttpHeaders responseHeaders)
        throws IOException {
      queueUrlMap(
          targetHttpProxy.getUrlMap(),
          loadBalancer,
          urlMapRequest,
          groupHealthRequest,
          projectBackendServices,
          projectHealthChecks,
          subject,
          failedSubjects);
    }
  }

  public class TargetHttpsProxyCallback extends BaseCallback<TargetHttpsProxy> {
    private final GoogleExternalHttpLoadBalancer loadBalancer;
    private final GoogleBatchRequest urlMapRequest;
    private final GoogleBatchRequest groupHealthRequest;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;

    TargetHttpsProxyCallback(
        GoogleExternalHttpLoadBalancer loadBalancer,
        GoogleBatchRequest urlMapRequest,
        GoogleBatchRequest groupHealthRequest,
        List<BackendService> projectBackendServices,
        List<HealthCheck> projectHealthChecks,
        String subject,
        List<String> failedSubjects) {
      super(subject, failedSubjects);
      this.loadBalancer = loadBalancer;
      this.urlMapRequest = urlMapRequest;
      this.groupHealthRequest = groupHealthRequest;
      this.projectBackendServices = projectBackendServices;
      this.projectHealthChecks = projectHealthChecks;
    }

    @Override
    public void onSuccess(TargetHttpsProxy targetHttpsProxy, HttpHeaders responseHeaders)
        throws IOException {
      loadBalancer.setCertificate(getFirstSslCertificateForExternalManaged(targetHttpsProxy));
      queueUrlMap(
          targetHttpsProxy.getUrlMap(),
          loadBalancer,
          urlMapRequest,
          groupHealthRequest,
          projectBackendServices,
          projectHealthChecks,
          subject,
          failedSubjects);
    }
  }

  private void queueUrlMap(
      String urlMapUrl,
      GoogleExternalHttpLoadBalancer loadBalancer,
      GoogleBatchRequest urlMapRequest,
      GoogleBatchRequest groupHealthRequest,
      List<BackendService> projectBackendServices,
      List<HealthCheck> projectHealthChecks,
      String subject,
      List<String> failedSubjects)
      throws IOException {
    if (urlMapUrl != null) {
      urlMapRequest.queue(
          getCompute()
              .regionUrlMaps()
              .get(getProject(), getRegion(), Utils.getLocalName(urlMapUrl)),
          new UrlMapCallback(
              loadBalancer,
              groupHealthRequest,
              projectBackendServices,
              projectHealthChecks,
              subject,
              failedSubjects));
    }
  }

  public class UrlMapCallback extends BaseCallback<UrlMap> {
    private final GoogleExternalHttpLoadBalancer loadBalancer;
    private final GoogleBatchRequest groupHealthRequest;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;

    UrlMapCallback(
        GoogleExternalHttpLoadBalancer loadBalancer,
        GoogleBatchRequest groupHealthRequest,
        List<BackendService> projectBackendServices,
        List<HealthCheck> projectHealthChecks,
        String subject,
        List<String> failedSubjects) {
      super(subject, failedSubjects);
      this.loadBalancer = loadBalancer;
      this.groupHealthRequest = groupHealthRequest;
      this.projectBackendServices = projectBackendServices;
      this.projectHealthChecks = projectHealthChecks;
    }

    @Override
    public void onSuccess(UrlMap urlMap, HttpHeaders responseHeaders) {
      applyUrlMap(
          loadBalancer, urlMap, projectBackendServices, projectHealthChecks, groupHealthRequest);
    }
  }

  private void applyUrlMap(
      GoogleExternalHttpLoadBalancer loadBalancer,
      UrlMap urlMap,
      List<BackendService> projectBackendServices,
      List<HealthCheck> projectHealthChecks,
      GoogleBatchRequest groupHealthRequest) {
    // URL maps are the logical HTTP(S) load balancer boundary in Deck and Clouddriver. Each
    // default service, path-matcher default, and path rule can point at a distinct regional
    // backend service, so collect every referenced service before enriching backend details.
    loadBalancer.setUrlMapName(urlMap.getName());
    Set<String> queuedServices = new HashSet<>();

    String urlMapDefaultService = Utils.getLocalName(urlMap.getDefaultService());
    queuedServices.add(urlMapDefaultService);
    GoogleBackendService defaultService = new GoogleBackendService();
    defaultService.setName(urlMapDefaultService);
    loadBalancer.setDefaultService(defaultService);

    if (urlMap.getPathMatchers() != null && urlMap.getHostRules() != null) {
      for (PathMatcher pathMatcher : urlMap.getPathMatchers()) {
        String pathMatchDefaultService = Utils.getLocalName(pathMatcher.getDefaultService());
        List<PathRule> pathRules =
            pathMatcher.getPathRules() != null ? pathMatcher.getPathRules() : new ArrayList<>();
        for (HostRule hostRule : urlMap.getHostRules()) {
          if (hostRule.getPathMatcher() != null
              && hostRule.getPathMatcher().equals(pathMatcher.getName())) {
            GoogleBackendService googleBackendService = new GoogleBackendService();
            googleBackendService.setName(pathMatchDefaultService);

            GooglePathMatcher gPathMatcher = new GooglePathMatcher();
            gPathMatcher.setPathRules(new ArrayList<>());
            gPathMatcher.setDefaultService(googleBackendService);

            GoogleHostRule gHostRule = new GoogleHostRule();
            gHostRule.setHostPatterns(hostRule.getHosts());
            gHostRule.setPathMatcher(gPathMatcher);
            gPathMatcher.setPathRules(
                pathRules.stream()
                    .map(
                        pathRule -> {
                          GoogleBackendService service = new GoogleBackendService();
                          service.setName(Utils.getLocalName(pathRule.getService()));

                          GooglePathRule googlePathRule = new GooglePathRule();
                          googlePathRule.setPaths(pathRule.getPaths());
                          googlePathRule.setBackendService(service);
                          return googlePathRule;
                        })
                    .collect(toList()));
            loadBalancer.getHostRules().add(gHostRule);
          }
        }

        queuedServices.add(pathMatchDefaultService);
        for (PathRule pathRule : pathRules) {
          if (pathRule.getService() != null) {
            queuedServices.add(Utils.getLocalName(pathRule.getService()));
          }
        }
      }
    }

    for (String queuedService : queuedServices) {
      projectBackendServices.stream()
          .filter(bs -> Utils.getLocalName(bs.getName()).equals(queuedService))
          .findFirst()
          .ifPresent(
              service ->
                  applyBackendService(
                      service, loadBalancer, projectHealthChecks, groupHealthRequest));
    }
  }

  private void applyBackendService(
      BackendService backendService,
      GoogleExternalHttpLoadBalancer loadBalancer,
      List<HealthCheck> healthChecks,
      GoogleBatchRequest groupHealthRequest) {
    List<GoogleBackendService> backendServicesToUpdate =
        Utils.getBackendServicesFromExternalHttpLoadBalancerView(loadBalancer.getView()).stream()
            .filter(b -> b.getName().equals(backendService.getName()))
            .collect(toList());
    for (GoogleBackendService service : backendServicesToUpdate) {
      service.setRegion(loadBalancer.getRegion());
      service.setSessionAffinity(
          GoogleSessionAffinity.valueOf(backendService.getSessionAffinity()));
      service.setAffinityCookieTtlSec(backendService.getAffinityCookieTtlSec());
      service.setEnableCDN(backendService.getEnableCDN());
      String name = backendService.getPortName();
      service.setPortName(
          name != null ? name : GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME);
      ConnectionDraining draining = backendService.getConnectionDraining();
      service.setConnectionDrainingTimeoutSec(
          draining == null ? 0 : draining.getDrainingTimeoutSec());
      if (backendService.getBackends() != null) {
        service.setBackends(
            backendService.getBackends().stream()
                .filter(backend -> backend.getGroup() != null)
                .map(
                    backend -> {
                      GoogleLoadBalancedBackend googleBackend = new GoogleLoadBalancedBackend();
                      googleBackend.setPolicy(GCEUtil.loadBalancingPolicyFromBackend(backend));
                      googleBackend.setServerGroupUrl(backend.getGroup());
                      queueBackendHealth(loadBalancer, backendService, backend, groupHealthRequest);
                      return googleBackend;
                    })
                .collect(toList()));
      }
    }

    if (backendService.getHealthChecks() != null) {
      for (String healthCheckURL : backendService.getHealthChecks()) {
        String healthCheckName = Utils.getLocalName(healthCheckURL);
        healthChecks.stream()
            .filter(hc -> Utils.getLocalName(hc.getName()).equals(healthCheckName))
            .findFirst()
            .ifPresent(
                healthCheck ->
                    GoogleInternalHttpLoadBalancerCachingAgent.handleHealthCheck(
                        healthCheck, backendServicesToUpdate));
      }
    }
  }

  void queueBackendHealth(
      GoogleExternalHttpLoadBalancer loadBalancer,
      BackendService backendService,
      Backend backend,
      GoogleBatchRequest groupHealthRequest) {
    ResourceGroupReference resourceGroup = new ResourceGroupReference();
    resourceGroup.setGroup(backend.getGroup());
    try {
      GroupHealthRequest ghr =
          new GroupHealthRequest(getProject(), backendService.getName(), resourceGroup.getGroup());
      if (!queuedBsGroupHealthRequests.contains(ghr)) {
        queuedBsGroupHealthRequests.add(ghr);
        groupHealthRequest.queue(
            getCompute()
                .regionBackendServices()
                .getHealth(getProject(), getRegion(), backendService.getName(), resourceGroup),
            new GroupHealthCallback(backendService.getName()));
      }
      resolutions.add(new LoadBalancerHealthResolution(loadBalancer, backendService.getName()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public class GroupHealthCallback extends JsonBatchCallback<BackendServiceGroupHealth> {
    private final String backendServiceName;

    GroupHealthCallback(String backendServiceName) {
      this.backendServiceName = backendServiceName;
    }

    @Override
    public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
      // Backend health is supplemental cache data. A transient getHealth failure should produce
      // unknown health for this backend, not drop the load balancer from cache.
      log.debug(
          "Failed backend service group health call for backend service {} for regional external HTTP load balancer. "
              + "The platform error message was:\n{}.",
          backendServiceName,
          e.getMessage());
    }

    @Override
    public void onSuccess(
        BackendServiceGroupHealth backendServiceGroupHealth, HttpHeaders responseHeaders) {
      bsNameToGroupHealthsMap
          .computeIfAbsent(backendServiceName, ignored -> new ArrayList<>())
          .add(backendServiceGroupHealth);
    }
  }
}
