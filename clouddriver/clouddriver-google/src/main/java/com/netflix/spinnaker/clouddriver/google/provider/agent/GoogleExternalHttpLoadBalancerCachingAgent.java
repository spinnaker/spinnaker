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

import static com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleTargetProxyType.HTTP;
import static com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleTargetProxyType.HTTPS;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.*;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*;
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
    try {
      // Backend services and health checks are shared by every URL map in the region. Reading them
      // once keeps the per-listener graph walk cheap while preserving the full backend model.
      List<BackendService> projectBackendServices =
          GCEUtil.fetchRegionBackendServices(this, getCompute(), getProject(), getRegion());
      List<HealthCheck> projectHealthChecks =
          GCEUtil.fetchRegionalHealthChecks(this, getCompute(), getProject(), getRegion());

      List<ForwardingRule> forwardingRules;
      if (onDemandLoadBalancerName != null) {
        // On-demand requests are routed by forwarding-rule name. Returning an empty list for a 404
        // tells the cache layer to evict stale data; throwing for the wrong scheme lets the owning
        // regional HTTP-family agent handle its own forwarding rule.
        ForwardingRule forwardingRule;
        try {
          forwardingRule =
              getCompute()
                  .forwardingRules()
                  .get(getProject(), getRegion(), onDemandLoadBalancerName)
                  .execute();
        } catch (GoogleJsonResponseException e) {
          if (e.getStatusCode() == 404) {
            return Collections.emptyList();
          }
          throw e;
        }
        if (!isExternalManagedHttpForwardingRule(forwardingRule)) {
          throw new IllegalArgumentException(
              "Not responsible for on demand caching of load balancers without "
                  + "EXTERNAL_MANAGED HTTP(S) target proxy.");
        }
        forwardingRules = Collections.singletonList(forwardingRule);
      } else {
        forwardingRules =
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
            }.timeExecute(
                ForwardingRuleList::getItems,
                "compute.forwardingRules.list",
                TAG_SCOPE,
                SCOPE_REGIONAL,
                TAG_REGION,
                getRegion());
      }

      return forwardingRules.stream()
          .filter(GoogleExternalHttpLoadBalancerCachingAgent::isExternalManagedHttpForwardingRule)
          .map(rule -> buildLoadBalancer(rule, projectBackendServices, projectHealthChecks))
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
    GoogleTargetProxyType type =
        forwardingRule.getTarget() != null
            ? Utils.getTargetProxyType(forwardingRule.getTarget())
            : null;
    return "EXTERNAL_MANAGED".equals(forwardingRule.getLoadBalancingScheme())
        && (type == HTTP || type == HTTPS);
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

  private GoogleExternalHttpLoadBalancer buildLoadBalancer(
      ForwardingRule forwardingRule,
      List<BackendService> projectBackendServices,
      List<HealthCheck> projectHealthChecks) {
    GoogleExternalHttpLoadBalancer loadBalancer = new GoogleExternalHttpLoadBalancer();
    loadBalancer.setName(forwardingRule.getName());
    loadBalancer.setAccount(getAccountName());
    loadBalancer.setRegion(Utils.getLocalName(forwardingRule.getRegion()));
    loadBalancer.setCreatedTime(Utils.getTimeFromTimestamp(forwardingRule.getCreationTimestamp()));
    loadBalancer.setIpAddress(forwardingRule.getIPAddress());
    loadBalancer.setIpProtocol(forwardingRule.getIPProtocol());
    loadBalancer.setPortRange(forwardingRule.getPortRange());
    loadBalancer.setNetwork(forwardingRule.getNetwork());
    loadBalancer.setHealths(new ArrayList<>());
    loadBalancer.setHostRules(new ArrayList<>());

    String targetProxyName = Utils.getLocalName(forwardingRule.getTarget());
    try {
      String urlMapUrl;
      switch (Utils.getTargetProxyType(forwardingRule.getTarget())) {
        case HTTP:
          TargetHttpProxy httpProxy =
              getCompute()
                  .regionTargetHttpProxies()
                  .get(getProject(), getRegion(), targetProxyName)
                  .execute();
          urlMapUrl = httpProxy.getUrlMap();
          break;
        case HTTPS:
          TargetHttpsProxy httpsProxy =
              getCompute()
                  .regionTargetHttpsProxies()
                  .get(getProject(), getRegion(), targetProxyName)
                  .execute();
          loadBalancer.setCertificate(getFirstSslCertificateForExternalManaged(httpsProxy));
          urlMapUrl = httpsProxy.getUrlMap();
          break;
        default:
          throw new IllegalArgumentException(
              "Unsupported target proxy for " + forwardingRule.getName());
      }

      if (urlMapUrl != null) {
        UrlMap urlMap =
            getCompute()
                .regionUrlMaps()
                .get(getProject(), getRegion(), Utils.getLocalName(urlMapUrl))
                .execute();
        applyUrlMap(loadBalancer, urlMap, projectBackendServices, projectHealthChecks);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return loadBalancer;
  }

  private void applyUrlMap(
      GoogleExternalHttpLoadBalancer loadBalancer,
      UrlMap urlMap,
      List<BackendService> projectBackendServices,
      List<HealthCheck> projectHealthChecks) {
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
          .ifPresent(service -> applyBackendService(service, loadBalancer, projectHealthChecks));
    }
  }

  private void applyBackendService(
      BackendService backendService,
      GoogleExternalHttpLoadBalancer loadBalancer,
      List<HealthCheck> healthChecks) {
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
                      fetchAndApplyBackendHealth(loadBalancer, backendService, backend);
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

  void fetchAndApplyBackendHealth(
      GoogleExternalHttpLoadBalancer loadBalancer, BackendService backendService, Backend backend) {
    ResourceGroupReference resourceGroup = new ResourceGroupReference();
    resourceGroup.setGroup(backend.getGroup());
    try {
      BackendServiceGroupHealth groupHealth =
          getCompute()
              .regionBackendServices()
              .getHealth(getProject(), getRegion(), backendService.getName(), resourceGroup)
              .execute();
      GCEUtil.handleHealthObject(loadBalancer, groupHealth);
    } catch (IOException e) {
      // Backend health is supplemental cache data. A transient getHealth failure should produce
      // unknown health for this backend, not drop the load balancer from cache.
      log.warn(
          "Failed to read backend health for backend service {} and group {}. "
              + "Caching load balancer {} with unknown health.",
          backendService.getName(),
          backend.getGroup(),
          loadBalancer.getName(),
          e);
    }
  }
}
