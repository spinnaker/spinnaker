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
import com.google.api.services.compute.model.Backend;
import com.google.api.services.compute.model.BackendService;
import com.google.api.services.compute.model.BackendServiceGroupHealth;
import com.google.api.services.compute.model.ConnectionDraining;
import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.ForwardingRuleList;
import com.google.api.services.compute.model.HealthCheck;
import com.google.api.services.compute.model.HostRule;
import com.google.api.services.compute.model.PathMatcher;
import com.google.api.services.compute.model.PathRule;
import com.google.api.services.compute.model.ResourceGroupReference;
import com.google.api.services.compute.model.TargetHttpProxy;
import com.google.api.services.compute.model.TargetHttpsProxy;
import com.google.api.services.compute.model.UrlMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest;
import com.netflix.spinnaker.clouddriver.google.cache.Keys;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHostRule;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancedBackend;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GooglePathMatcher;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GooglePathRule;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSessionAffinity;
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.GroupHealthRequest;
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.LoadBalancerHealthResolution;
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.PaginatedRequest;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared regional HTTP(S) cache graph walk for internal and external managed load balancers.
 *
 * <p>Both families start at a regional forwarding rule and walk through a regional target proxy,
 * regional URL map, regional backend services, health checks, and backend group health. Subclasses
 * own the GCP scheme/model differences and the policy for missing optional graph components.
 */
abstract class AbstractGoogleRegionalHttpLoadBalancerCachingAgent<T extends GoogleLoadBalancer>
    extends AbstractGoogleLoadBalancerCachingAgent {
  private static final Logger log =
      LoggerFactory.getLogger(AbstractGoogleRegionalHttpLoadBalancerCachingAgent.class);

  private Map<String, List<BackendServiceGroupHealth>> bsNameToGroupHealthsMap = new HashMap<>();
  private Set<GroupHealthRequest> queuedBsGroupHealthRequests = new HashSet<>();
  private Set<LoadBalancerHealthResolution> resolutions = new HashSet<>();

  AbstractGoogleRegionalHttpLoadBalancerCachingAgent(
      String clouddriverUserAgentApplicationName,
      GoogleNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region);
  }

  @Override
  public List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName) {
    List<T> loadBalancers = new ArrayList<>();
    List<String> failedLoadBalancers = new ArrayList<>();

    GoogleBatchRequest forwardingRulesRequest = buildGoogleBatchRequest();
    GoogleBatchRequest targetProxyRequest = buildGoogleBatchRequest();
    GoogleBatchRequest urlMapRequest = buildGoogleBatchRequest();
    GoogleBatchRequest groupHealthRequest = buildGoogleBatchRequest();

    // These callbacks run across staged batches; reset all callback-owned state for each refresh.
    bsNameToGroupHealthsMap = new HashMap<>();
    queuedBsGroupHealthRequests = new HashSet<>();
    resolutions = new HashSet<>();

    try {
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
            getInstrumentationPrefix() + ".forwardingRules");
      }

      executeIfRequestsAreQueued(
          forwardingRulesRequest, getInstrumentationPrefix() + ".forwardingRules");
      executeIfRequestsAreQueued(targetProxyRequest, getInstrumentationPrefix() + ".targetProxy");
      executeIfRequestsAreQueued(urlMapRequest, getInstrumentationPrefix() + ".urlMapRequest");
      executeIfRequestsAreQueued(groupHealthRequest, getInstrumentationPrefix() + ".groupHealth");

      // Group health is returned by backend service, then applied after every batch has completed.
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
    return Keys.getInstanceKey(getAccountName(), instanceRegion, health.getInstanceName());
  }

  protected void populateCommonFields(T loadBalancer, ForwardingRule forwardingRule) {
    loadBalancer.setName(forwardingRule.getName());
    loadBalancer.setAccount(getAccountName());
    loadBalancer.setRegion(Utils.getLocalName(forwardingRule.getRegion()));
    loadBalancer.setCreatedTime(Utils.getTimeFromTimestamp(forwardingRule.getCreationTimestamp()));
    loadBalancer.setIpAddress(forwardingRule.getIPAddress());
    loadBalancer.setIpProtocol(forwardingRule.getIPProtocol());
    loadBalancer.setPortRange(forwardingRule.getPortRange());
    loadBalancer.setHealths(new ArrayList<>());
  }

  protected abstract String getInstrumentationPrefix();

  protected abstract boolean isOwnedForwardingRule(ForwardingRule forwardingRule);

  protected abstract T newLoadBalancer(ForwardingRule forwardingRule);

  protected abstract void applyHttpsProxyFields(T loadBalancer, TargetHttpsProxy targetHttpsProxy);

  protected abstract void setUrlMapName(T loadBalancer, String urlMapName);

  protected abstract void setDefaultService(T loadBalancer, GoogleBackendService defaultService);

  protected abstract List<GoogleHostRule> getHostRules(T loadBalancer);

  protected abstract List<GoogleBackendService> getBackendServicesFromView(T loadBalancer);

  /** Applies the subclass policy when a URL map references a backend service not in the region. */
  protected abstract void handleMissingBackendService(String backendServiceName, T loadBalancer);

  /** Applies the subclass policy when a backend service references an unresolved health check. */
  protected abstract void handleMissingHealthCheck(String healthCheckName, T loadBalancer);

  /**
   * Applies the subclass policy for forwarding rules whose scheme matches but target proxy does
   * not.
   */
  protected abstract void handleUnsupportedTargetProxy(
      ForwardingRule forwardingRule, T loadBalancer, List<String> failedLoadBalancers);

  protected abstract String getWrongSchemeMessage();

  public class ForwardingRuleCallbacks {
    private final List<T> loadBalancers;
    private final List<String> failedLoadBalancers;
    private final GoogleBatchRequest targetProxyRequest;
    private final GoogleBatchRequest urlMapRequest;
    private final GoogleBatchRequest groupHealthRequest;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;

    public ForwardingRuleCallbacks(
        List<T> loadBalancers,
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
      T loadBalancer = newLoadBalancer(forwardingRule);
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
            handleUnsupportedTargetProxy(forwardingRule, loadBalancer, failedLoadBalancers);
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public class ForwardingRuleSingletonCallback extends JsonBatchCallback<ForwardingRule> {
      @Override
      public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        if (e.getCode() != 404) {
          log.error(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e));
        }
      }

      @Override
      public void onSuccess(ForwardingRule forwardingRule, HttpHeaders responseHeaders)
          throws IOException {
        if (isOwnedForwardingRule(forwardingRule)) {
          cacheRemainderOfLoadBalancerResourceGraph(forwardingRule);
        } else {
          throw new IllegalArgumentException(getWrongSchemeMessage());
        }
      }
    }

    public class ForwardingRuleListCallback extends JsonBatchCallback<ForwardingRuleList>
        implements FailureLogger {
      @Override
      public void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) {
        if (forwardingRuleList.getItems() == null) {
          return;
        }
        forwardingRuleList.getItems().stream()
            .filter(AbstractGoogleRegionalHttpLoadBalancerCachingAgent.this::isOwnedForwardingRule)
            .forEach(ForwardingRuleCallbacks.this::cacheRemainderOfLoadBalancerResourceGraph);
      }

      @Override
      public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
        log.error(e.getMessage());
      }
    }
  }

  abstract class BaseCallback<R> extends JsonBatchCallback<R> {
    final String subject;
    final List<String> failedSubjects;

    BaseCallback(String subject, List<String> failedSubjects) {
      this.subject = subject;
      this.failedSubjects = failedSubjects;
    }

    @Override
    public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
      log.warn(
          "Failed to read a component of regional HTTP load balancer {}. "
              + "The platform error message was:\n{}. Reporting it as failed to the caching agent.",
          subject,
          e.getMessage());
      failedSubjects.add(subject);
    }
  }

  public class TargetProxyCallback extends BaseCallback<TargetHttpProxy> {
    private final T loadBalancer;
    private final GoogleBatchRequest urlMapRequest;
    private final GoogleBatchRequest groupHealthRequest;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;

    TargetProxyCallback(
        T loadBalancer,
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
    private final T loadBalancer;
    private final GoogleBatchRequest urlMapRequest;
    private final GoogleBatchRequest groupHealthRequest;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;

    TargetHttpsProxyCallback(
        T loadBalancer,
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
      applyHttpsProxyFields(loadBalancer, targetHttpsProxy);
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
      T loadBalancer,
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
    private final T loadBalancer;
    private final GoogleBatchRequest groupHealthRequest;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;

    UrlMapCallback(
        T loadBalancer,
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
      T loadBalancer,
      UrlMap urlMap,
      List<BackendService> projectBackendServices,
      List<HealthCheck> projectHealthChecks,
      GoogleBatchRequest groupHealthRequest) {
    setUrlMapName(loadBalancer, urlMap.getName());
    Set<String> queuedServices = new HashSet<>();

    String urlMapDefaultService = Utils.getLocalName(urlMap.getDefaultService());
    queuedServices.add(urlMapDefaultService);
    GoogleBackendService defaultService = new GoogleBackendService();
    defaultService.setName(urlMapDefaultService);
    setDefaultService(loadBalancer, defaultService);

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

            GooglePathMatcher googlePathMatcher = new GooglePathMatcher();
            googlePathMatcher.setPathRules(new ArrayList<>());
            googlePathMatcher.setDefaultService(googleBackendService);

            GoogleHostRule googleHostRule = new GoogleHostRule();
            googleHostRule.setHostPatterns(hostRule.getHosts());
            googleHostRule.setPathMatcher(googlePathMatcher);
            googlePathMatcher.setPathRules(
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
            getHostRules(loadBalancer).add(googleHostRule);
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
          .ifPresentOrElse(
              service ->
                  applyBackendService(
                      service, loadBalancer, projectHealthChecks, groupHealthRequest),
              () -> handleMissingBackendService(queuedService, loadBalancer));
    }
  }

  private void applyBackendService(
      BackendService backendService,
      T loadBalancer,
      List<HealthCheck> healthChecks,
      GoogleBatchRequest groupHealthRequest) {
    List<GoogleBackendService> backendServicesToUpdate =
        getBackendServicesFromView(loadBalancer).stream()
            .filter(b -> b != null && b.getName().equals(backendService.getName()))
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
            .ifPresentOrElse(
                healthCheck -> handleHealthCheck(healthCheck, backendServicesToUpdate),
                () -> handleMissingHealthCheck(healthCheckName, loadBalancer));
      }
    }
  }

  void queueBackendHealth(
      T loadBalancer,
      BackendService backendService,
      Backend backend,
      GoogleBatchRequest groupHealthRequest) {
    ResourceGroupReference resourceGroup = new ResourceGroupReference();
    resourceGroup.setGroup(backend.getGroup());
    try {
      GroupHealthRequest groupHealthRequestKey =
          new GroupHealthRequest(getProject(), backendService.getName(), resourceGroup.getGroup());
      // A URL map can reference the same backend service/group through several rules; queue once.
      if (!queuedBsGroupHealthRequests.contains(groupHealthRequestKey)) {
        queuedBsGroupHealthRequests.add(groupHealthRequestKey);
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
      log.debug(
          "Failed backend service group health call for backend service {} for regional HTTP load balancer. "
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

  static void handleHealthCheck(
      final HealthCheck healthCheck, List<GoogleBackendService> googleBackendServices) {
    if (healthCheck == null) return;

    Integer port = null;
    GoogleHealthCheck.HealthCheckType healthCheckType = null;
    String requestPath = null;
    if (healthCheck.getTcpHealthCheck() != null) {
      port = healthCheck.getTcpHealthCheck().getPort();
      healthCheckType = GoogleHealthCheck.HealthCheckType.TCP;
    } else if (healthCheck.getSslHealthCheck() != null) {
      port = healthCheck.getSslHealthCheck().getPort();
      healthCheckType = GoogleHealthCheck.HealthCheckType.SSL;
    } else if (healthCheck.getHttpHealthCheck() != null) {
      port = healthCheck.getHttpHealthCheck().getPort();
      requestPath = healthCheck.getHttpHealthCheck().getRequestPath();
      healthCheckType = GoogleHealthCheck.HealthCheckType.HTTP;
    } else if (healthCheck.getHttpsHealthCheck() != null) {
      port = healthCheck.getHttpsHealthCheck().getPort();
      requestPath = healthCheck.getHttpsHealthCheck().getRequestPath();
      healthCheckType = GoogleHealthCheck.HealthCheckType.HTTPS;
    } else if (healthCheck.getHttp2HealthCheck() != null) {
      port = healthCheck.getHttp2HealthCheck().getPort();
      requestPath = healthCheck.getHttp2HealthCheck().getRequestPath();
      healthCheckType = GoogleHealthCheck.HealthCheckType.HTTP2;
    } else if (healthCheck.getGrpcHealthCheck() != null) {
      port = healthCheck.getGrpcHealthCheck().getPort();
      requestPath = healthCheck.getGrpcHealthCheck().getGrpcServiceName();
      healthCheckType = GoogleHealthCheck.HealthCheckType.GRPC;
    }

    if (port != null && healthCheckType != null) {
      for (GoogleBackendService googleBackendService : googleBackendServices) {
        GoogleHealthCheck googleHealthCheck = new GoogleHealthCheck();
        googleHealthCheck.setName(healthCheck.getName());
        googleHealthCheck.setRequestPath(requestPath);
        googleHealthCheck.setSelfLink(healthCheck.getSelfLink());
        googleHealthCheck.setPort(port);
        googleHealthCheck.setHealthCheckType(healthCheckType);
        googleHealthCheck.setCheckIntervalSec(healthCheck.getCheckIntervalSec());
        googleHealthCheck.setTimeoutSec(healthCheck.getTimeoutSec());
        googleHealthCheck.setUnhealthyThreshold(healthCheck.getUnhealthyThreshold());
        googleHealthCheck.setHealthyThreshold(healthCheck.getHealthyThreshold());
        googleHealthCheck.setRegion(healthCheck.getRegion());
        googleBackendService.setHealthCheck(googleHealthCheck);
      }
    }
  }
}
