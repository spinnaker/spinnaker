/*
 * Copyright 2024 Harness, Inc.
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
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.*;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest;
import com.netflix.spinnaker.clouddriver.google.cache.Keys;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck;
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
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for regional HTTP load balancer caching agents (internal managed and regional
 * external). Contains common logic for loading forwarding rules, target proxies, URL maps, backend
 * services, and health checks.
 *
 * @param <T> The specific GoogleLoadBalancer subclass this agent handles
 */
public abstract class AbstractGoogleRegionalHttpLoadBalancerCachingAgent<
        T extends GoogleLoadBalancer>
    extends AbstractGoogleLoadBalancerCachingAgent {

  private static final Logger log =
      LoggerFactory.getLogger(AbstractGoogleRegionalHttpLoadBalancerCachingAgent.class);

  /**
   * Local cache of BackendServiceGroupHealth keyed by BackendService name.
   *
   * <p>The types in the GCE Batch callbacks aren't the actual Compute types, hence String ->
   * Object.
   */
  protected Map<String, List<BackendServiceGroupHealth>> bsNameToGroupHealthsMap = new HashMap<>();

  protected Set<GroupHealthRequest> queuedBsGroupHealthRequests = new HashSet<>();
  protected Set<LoadBalancerHealthResolution> resolutions = new HashSet<>();

  public AbstractGoogleRegionalHttpLoadBalancerCachingAgent(
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

    // Reset the local getHealth caches/queues each caching agent cycle.
    bsNameToGroupHealthsMap = new HashMap<>();
    queuedBsGroupHealthRequests = new HashSet<>();
    resolutions = new HashSet<>();

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

    try {
      if (onDemandLoadBalancerName != null) {
        ForwardingRuleCallbacks.ForwardingRuleSingletonCallback frCallback =
            forwardingRuleCallbacks.new ForwardingRuleSingletonCallback();
        forwardingRulesRequest.queue(
            getCompute().forwardingRules().get(getProject(), getRegion(), onDemandLoadBalancerName),
            frCallback);
      } else {
        ForwardingRuleCallbacks.ForwardingRuleListCallback frlCallback =
            forwardingRuleCallbacks.new ForwardingRuleListCallback();
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
            frlCallback,
            getLoadBalancerCachingAgentName() + ".forwardingRules");
      }

      executeIfRequestsAreQueued(
          forwardingRulesRequest, getLoadBalancerCachingAgentName() + ".forwardingRules");
      executeIfRequestsAreQueued(
          targetProxyRequest, getLoadBalancerCachingAgentName() + ".targetProxy");
      executeIfRequestsAreQueued(
          urlMapRequest, getLoadBalancerCachingAgentName() + ".urlMapRequest");
      executeIfRequestsAreQueued(
          groupHealthRequest, getLoadBalancerCachingAgentName() + ".groupHealth");

      for (LoadBalancerHealthResolution resolution : resolutions) {
        for (Object groupHealth : bsNameToGroupHealthsMap.get(resolution.getTarget())) {
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
    // Http load balancers' region is "global", so we have to determine the instance region from its
    // zone.
    String instanceZone = health.getInstanceZone();
    String instanceRegion = getCredentials().regionFromZone(instanceZone);
    return Keys.getInstanceKey(getAccountName(), instanceRegion, health.getInstanceName());
  }

  protected static String getFirstSslCertificateName(TargetHttpsProxy targetHttpsProxy) {
    List<String> sslCertificates = targetHttpsProxy.getSslCertificates();
    return sslCertificates != null && !sslCertificates.isEmpty()
        ? Utils.getLocalName(sslCertificates.get(0))
        : null;
  }

  /** Returns the name used for logging and metrics (e.g., "InternalHttpLoadBalancerCaching"). */
  protected abstract String getLoadBalancerCachingAgentName();

  /** Creates a new load balancer instance of the appropriate type. */
  protected abstract T createNewLoadBalancer();

  /** Returns the load balancing scheme filter value (e.g., "INTERNAL_MANAGED" or "EXTERNAL"). */
  protected abstract String getLoadBalancingSchemeFilter();

  /** Returns a function that extracts backend services from the load balancer's view. */
  protected abstract Function<T, List<GoogleBackendService>> getBackendServicesExtractor();

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

    public void cacheRemainderOfLoadBalancerResourceGraph(final ForwardingRule forwardingRule) {
      T newLoadBalancer = createNewLoadBalancer();

      newLoadBalancer.setName(forwardingRule.getName());
      newLoadBalancer.setAccount(getAccountName());
      newLoadBalancer.setRegion(Utils.getLocalName(forwardingRule.getRegion()));
      newLoadBalancer.setCreatedTime(
          Utils.getTimeFromTimestamp(forwardingRule.getCreationTimestamp()));
      newLoadBalancer.setIpAddress(forwardingRule.getIPAddress());
      newLoadBalancer.setIpProtocol(forwardingRule.getIPProtocol());
      newLoadBalancer.setPortRange(forwardingRule.getPortRange());

      if (newLoadBalancer instanceof GoogleRegionalHttpLoadBalancerBase) {
        GoogleRegionalHttpLoadBalancerBase base =
            (GoogleRegionalHttpLoadBalancerBase) newLoadBalancer;
        base.setNetwork(forwardingRule.getNetwork());
        base.setSubnet(forwardingRule.getSubnetwork());
        base.setHealths(new ArrayList<>());
        base.setHostRules(new ArrayList<>());
      }

      loadBalancers.add(newLoadBalancer);

      String targetProxyName = Utils.getLocalName(forwardingRule.getTarget());
      TargetProxyCallback targetProxyCallback =
          new TargetProxyCallback(
              newLoadBalancer,
              urlMapRequest,
              groupHealthRequest,
              projectBackendServices,
              projectHealthChecks,
              newLoadBalancer.getName(),
              failedLoadBalancers);

      TargetHttpsProxyCallback targetHttpsProxyCallback =
          new TargetHttpsProxyCallback(
              newLoadBalancer,
              urlMapRequest,
              groupHealthRequest,
              projectBackendServices,
              projectHealthChecks,
              newLoadBalancer.getName(),
              failedLoadBalancers);

      try {
        switch (Utils.getTargetProxyType(forwardingRule.getTarget())) {
          case HTTP:
            targetProxyRequest.queue(
                getCompute()
                    .regionTargetHttpProxies()
                    .get(getProject(), getRegion(), targetProxyName),
                targetProxyCallback);
            break;
          case HTTPS:
            targetProxyRequest.queue(
                getCompute()
                    .regionTargetHttpsProxies()
                    .get(getProject(), getRegion(), targetProxyName),
                targetHttpsProxyCallback);
            break;
          default:
            log.debug(
                "Non-Http target type found for regional forwarding rule "
                    + forwardingRule.getName());
            break;
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public class ForwardingRuleSingletonCallback extends JsonBatchCallback<ForwardingRule> {
      @Override
      public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        // 404 is thrown if the forwarding rule does not exist in the given region.
        if (e.getCode() != 404) {
          String errorJson =
              new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e);
          log.error(errorJson);
        }
      }

      @Override
      public void onSuccess(ForwardingRule forwardingRule, HttpHeaders responseHeaders)
          throws IOException {
        GoogleTargetProxyType type =
            forwardingRule.getTarget() != null
                ? Utils.getTargetProxyType(forwardingRule.getTarget())
                : null;
        if (type == HTTP || type == HTTPS) {
          cacheRemainderOfLoadBalancerResourceGraph(forwardingRule);
        } else {
          throw new IllegalArgumentException(
              "Not responsible for on demand caching of load balancers without target proxy or with SSL proxy type.");
        }
      }
    }

    public class ForwardingRuleListCallback extends JsonBatchCallback<ForwardingRuleList>
        implements FailureLogger {
      @Override
      public void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) {
        if (forwardingRuleList.getItems() == null) return;
        forwardingRuleList.getItems().stream()
            .filter(
                f ->
                    f.getLoadBalancingScheme() != null
                        && f.getLoadBalancingScheme().equals(getLoadBalancingSchemeFilter()))
            .forEach(
                forwardingRule -> {
                  GoogleTargetProxyType type =
                      forwardingRule.getTarget() != null
                          ? Utils.getTargetProxyType(forwardingRule.getTarget())
                          : null;
                  if (type == HTTP || type == HTTPS) {
                    cacheRemainderOfLoadBalancerResourceGraph(forwardingRule);
                  }
                });
      }

      @Override
      public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
        log.error(e.getMessage());
      }
    }
  }

  protected abstract class BaseCallback<S> extends JsonBatchCallback<S> {
    protected final List<String> failedSubjects;
    protected final String subject;

    public BaseCallback(List<String> failedSubjects, String subject) {
      this.failedSubjects = failedSubjects;
      this.subject = subject;
    }

    @Override
    public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.warn(
          "Failed to read a component of subject "
              + subject
              + ". The platform error message was:\n"
              + e.getMessage()
              + ". \nReporting it as 'Failed' to the caching agent. ");
      failedSubjects.add(subject);
    }
  }

  public class TargetHttpsProxyCallback extends BaseCallback<TargetHttpsProxy> {
    private final T googleLoadBalancer;
    private final GoogleBatchRequest urlMapRequest;
    private final GoogleBatchRequest groupHealthRequest;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;

    public TargetHttpsProxyCallback(
        T googleLoadBalancer,
        GoogleBatchRequest urlMapRequest,
        GoogleBatchRequest groupHealthRequest,
        List<BackendService> projectBackendServices,
        List<HealthCheck> projectHealthChecks,
        String subject,
        List<String> failedSubjects) {
      super(failedSubjects, subject);
      this.googleLoadBalancer = googleLoadBalancer;
      this.urlMapRequest = urlMapRequest;
      this.groupHealthRequest = groupHealthRequest;
      this.projectBackendServices = projectBackendServices;
      this.projectHealthChecks = projectHealthChecks;
    }

    @Override
    public void onSuccess(TargetHttpsProxy targetHttpsProxy, HttpHeaders responseHeaders)
        throws IOException {
      if (googleLoadBalancer instanceof GoogleRegionalHttpLoadBalancerBase) {
        GoogleRegionalHttpLoadBalancerBase base =
            (GoogleRegionalHttpLoadBalancerBase) googleLoadBalancer;
        base.setCertificate(getFirstSslCertificateName(targetHttpsProxy));
        base.setCertificateMap(Utils.getLocalName(targetHttpsProxy.getCertificateMap()));
      }

      String urlMapURL = targetHttpsProxy.getUrlMap();
      if (urlMapURL != null) {
        UrlMapCallback urlMapCallback =
            new UrlMapCallback(
                googleLoadBalancer,
                projectBackendServices,
                projectHealthChecks,
                groupHealthRequest,
                subject,
                failedSubjects);
        urlMapRequest.queue(
            getCompute()
                .regionUrlMaps()
                .get(getProject(), getRegion(), Utils.getLocalName(urlMapURL)),
            urlMapCallback);
      }
    }
  }

  public class TargetProxyCallback extends BaseCallback<TargetHttpProxy> {
    private final T googleLoadBalancer;
    private final GoogleBatchRequest urlMapRequest;
    private final GoogleBatchRequest groupHealthRequest;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;

    public TargetProxyCallback(
        T googleLoadBalancer,
        GoogleBatchRequest urlMapRequest,
        GoogleBatchRequest groupHealthRequest,
        List<BackendService> projectBackendServices,
        List<HealthCheck> projectHealthChecks,
        String subject,
        List<String> failedSubjects) {
      super(failedSubjects, subject);
      this.googleLoadBalancer = googleLoadBalancer;
      this.urlMapRequest = urlMapRequest;
      this.groupHealthRequest = groupHealthRequest;
      this.projectBackendServices = projectBackendServices;
      this.projectHealthChecks = projectHealthChecks;
    }

    @Override
    public void onSuccess(TargetHttpProxy targetHttpProxy, HttpHeaders responseHeaders)
        throws IOException {
      String urlMapURL = targetHttpProxy.getUrlMap();
      if (urlMapURL != null) {
        UrlMapCallback urlMapCallback =
            new UrlMapCallback(
                googleLoadBalancer,
                projectBackendServices,
                projectHealthChecks,
                groupHealthRequest,
                subject,
                failedSubjects);
        urlMapRequest.queue(
            getCompute()
                .regionUrlMaps()
                .get(getProject(), getRegion(), Utils.getLocalName(urlMapURL)),
            urlMapCallback);
      }
    }
  }

  public class UrlMapCallback extends BaseCallback<UrlMap> {
    private final T googleLoadBalancer;
    private final List<BackendService> projectBackendServices;
    private final List<HealthCheck> projectHealthChecks;
    private final GoogleBatchRequest groupHealthRequest;

    public UrlMapCallback(
        T googleLoadBalancer,
        List<BackendService> projectBackendServices,
        List<HealthCheck> projectHealthChecks,
        GoogleBatchRequest groupHealthRequest,
        String subject,
        List<String> failedSubjects) {
      super(failedSubjects, subject);
      this.googleLoadBalancer = googleLoadBalancer;
      this.projectBackendServices = projectBackendServices;
      this.projectHealthChecks = projectHealthChecks;
      this.groupHealthRequest = groupHealthRequest;
    }

    @Override
    public void onSuccess(UrlMap urlMap, HttpHeaders responseHeaders) {
      if (!(googleLoadBalancer instanceof GoogleRegionalHttpLoadBalancerBase)) {
        return;
      }

      GoogleRegionalHttpLoadBalancerBase base =
          (GoogleRegionalHttpLoadBalancerBase) googleLoadBalancer;

      // Check that we aren't stomping on our URL map.
      if (base.getDefaultService() != null
          || (base.getHostRules() != null && base.getHostRules().size() > 0)) {
        log.error(
            "Overwriting UrlMap "
                + urlMap.getName()
                + ". You may have a TargetHttp(s)Proxy naming collision.");
      }

      base.setUrlMapName(urlMap.getName());
      Set<String> queuedServices = new HashSet<>();

      // Default service is mandatory.
      String urlMapDefaultService = Utils.getLocalName(urlMap.getDefaultService());
      queuedServices.add(urlMapDefaultService);

      GoogleBackendService service1 = new GoogleBackendService();
      service1.setName(urlMapDefaultService);
      base.setDefaultService(service1);

      if (urlMap.getPathMatchers() != null) {
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
              List<GooglePathRule> collect =
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
                      .collect(toList());
              gPathMatcher.setPathRules(collect);
              base.getHostRules().add(gHostRule);
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

      // Process queued backend services.
      for (String queuedService : queuedServices) {
        BackendService service =
            projectBackendServices.stream()
                .filter(bs -> Utils.getLocalName(bs.getName()).equals(queuedService))
                .findFirst()
                .get();
        handleBackendService(service, googleLoadBalancer, projectHealthChecks, groupHealthRequest);
      }
    }
  }

  public class GroupHealthCallback extends JsonBatchCallback<BackendServiceGroupHealth> {
    private final String backendServiceName;

    public GroupHealthCallback(String backendServiceName) {
      this.backendServiceName = backendServiceName;
    }

    @Override
    public void onFailure(final GoogleJsonError e, HttpHeaders responseHeaders) {
      log.debug(
          "Failed backend service group health call for backend service "
              + backendServiceName
              + " for Http load balancer. The platform error message was:\n "
              + e.getMessage()
              + ".");
    }

    @Override
    public void onSuccess(
        BackendServiceGroupHealth backendServiceGroupHealth, HttpHeaders responseHeaders) {
      if (!bsNameToGroupHealthsMap.containsKey(backendServiceName)) {
        bsNameToGroupHealthsMap.put(
            backendServiceName, new ArrayList<>(Arrays.asList(backendServiceGroupHealth)));
      } else {
        bsNameToGroupHealthsMap.get(backendServiceName).add(backendServiceGroupHealth);
      }
    }
  }

  protected void handleBackendService(
      BackendService backendService,
      T googleHttpLoadBalancer,
      List<HealthCheck> healthChecks,
      GoogleBatchRequest groupHealthRequest) {
    if (backendService == null) {
      return;
    }

    final GroupHealthCallback groupHealthCallback =
        new GroupHealthCallback(backendService.getName());

    // Extract backend services from the load balancer view using the concrete extractor
    List<GoogleBackendService> backendServicesInMap =
        getBackendServicesExtractor().apply(googleHttpLoadBalancer);
    List<GoogleBackendService> backendServicesToUpdate =
        backendServicesInMap.stream()
            .filter(b -> b.getName().equals(backendService.getName()))
            .collect(toList());

    for (GoogleBackendService service : backendServicesToUpdate) {
      service.setRegion(googleHttpLoadBalancer.getRegion());
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
        List<GoogleLoadBalancedBackend> backends =
            backendService.getBackends().stream()
                .filter(backend -> backend.getGroup() != null)
                .map(
                    backend -> {
                      GoogleLoadBalancedBackend googleBackend = new GoogleLoadBalancedBackend();
                      googleBackend.setPolicy(GCEUtil.loadBalancingPolicyFromBackend(backend));
                      googleBackend.setServerGroupUrl(backend.getGroup());
                      return googleBackend;
                    })
                .collect(toList());
        service.setBackends(backends);
      }
    }

    if (backendService.getBackends() != null) {
      backendService.getBackends().stream()
          .filter(backend -> backend.getGroup() != null)
          .forEach(
              backend -> {
                ResourceGroupReference resourceGroup = new ResourceGroupReference();
                resourceGroup.setGroup(backend.getGroup());

                GroupHealthRequest ghr =
                    new GroupHealthRequest(
                        getProject(), backendService.getName(), resourceGroup.getGroup());
                if (!queuedBsGroupHealthRequests.contains(ghr)) {
                  log.debug("Queueing a batch call for getHealth(): {}", ghr);
                  queuedBsGroupHealthRequests.add(ghr);
                  try {
                    groupHealthRequest.queue(
                        getCompute()
                            .regionBackendServices()
                            .getHealth(
                                getProject(), getRegion(), backendService.getName(), resourceGroup),
                        groupHealthCallback);
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                } else {
                  log.debug("Passing, batch call result cached for getHealth(): {}", ghr);
                }
                resolutions.add(
                    new LoadBalancerHealthResolution(
                        googleHttpLoadBalancer, backendService.getName()));
              });
    }

    for (String healthCheckURL : backendService.getHealthChecks()) {
      String healthCheckName = Utils.getLocalName(healthCheckURL);
      HealthCheck healthCheck =
          healthChecks.stream()
              .filter(hc -> Utils.getLocalName(hc.getName()).equals(healthCheckName))
              .findFirst()
              .get();
      handleHealthCheck(healthCheck, backendServicesToUpdate);
    }
  }

  protected static void handleHealthCheck(
      final HealthCheck healthCheck, List<GoogleBackendService> googleBackendServices) {
    if (healthCheck == null) return;

    Integer port = null;
    GoogleHealthCheck.HealthCheckType hcType = null;
    String requestPath = null;

    if (healthCheck.getTcpHealthCheck() != null) {
      port = healthCheck.getTcpHealthCheck().getPort();
      hcType = GoogleHealthCheck.HealthCheckType.TCP;
    } else if (healthCheck.getSslHealthCheck() != null) {
      port = healthCheck.getSslHealthCheck().getPort();
      hcType = GoogleHealthCheck.HealthCheckType.SSL;
    } else if (healthCheck.getHttpHealthCheck() != null) {
      port = healthCheck.getHttpHealthCheck().getPort();
      requestPath = healthCheck.getHttpHealthCheck().getRequestPath();
      hcType = GoogleHealthCheck.HealthCheckType.HTTP;
    } else if (healthCheck.getHttpsHealthCheck() != null) {
      port = healthCheck.getHttpsHealthCheck().getPort();
      requestPath = healthCheck.getHttpsHealthCheck().getRequestPath();
      hcType = GoogleHealthCheck.HealthCheckType.HTTPS;
    } else if (healthCheck.getHttp2HealthCheck() != null) {
      port = healthCheck.getHttp2HealthCheck().getPort();
      requestPath = healthCheck.getHttp2HealthCheck().getRequestPath();
      hcType = GoogleHealthCheck.HealthCheckType.HTTP2;
    } else if (healthCheck.getGrpcHealthCheck() != null) {
      port = healthCheck.getGrpcHealthCheck().getPort();
      requestPath = healthCheck.getGrpcHealthCheck().getGrpcServiceName();
      hcType = GoogleHealthCheck.HealthCheckType.GRPC;
    }

    if (port != null && hcType != null) {
      for (GoogleBackendService googleBackendService : googleBackendServices) {
        GoogleHealthCheck googleHealthCheck = new GoogleHealthCheck();
        googleHealthCheck.setName(healthCheck.getName());
        googleHealthCheck.setRequestPath(requestPath);
        googleHealthCheck.setSelfLink(healthCheck.getSelfLink());
        googleHealthCheck.setPort(port);
        googleHealthCheck.setHealthCheckType(hcType);
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
