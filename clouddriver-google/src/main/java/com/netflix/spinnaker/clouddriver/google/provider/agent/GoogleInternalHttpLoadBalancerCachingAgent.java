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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleInternalHttpLoadBalancerCachingAgent
    extends AbstractGoogleLoadBalancerCachingAgent {
  private static final Logger log = LoggerFactory.getLogger(GoogleInternalHttpLoadBalancer.class);

  public GoogleInternalHttpLoadBalancerCachingAgent(
      String clouddriverUserAgentApplicationName,
      GoogleNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region);
  }

  @Override
  public List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName) {
    List<GoogleInternalHttpLoadBalancer> loadBalancers = new ArrayList<>();
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
            forwardingRuleCallbacks.newForwardingRuleSingletonCallback();
        forwardingRulesRequest.queue(
            getCompute().forwardingRules().get(getProject(), getRegion(), onDemandLoadBalancerName),
            frCallback);
      } else {
        ForwardingRuleCallbacks.ForwardingRuleListCallback frlCallback =
            forwardingRuleCallbacks.newForwardingRuleListCallback();
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
            forwardingRulesRequest, frlCallback, "InternalHttpLoadBalancerCaching.forwardingRules");
      }

      executeIfRequestsAreQueued(
          forwardingRulesRequest, "InternalHttpLoadBalancerCaching.forwardingRules");
      executeIfRequestsAreQueued(targetProxyRequest, "InternalHttpLoadBalancerCaching.targetProxy");
      executeIfRequestsAreQueued(urlMapRequest, "InternalHttpLoadBalancerCaching.urlMapRequest");
      executeIfRequestsAreQueued(groupHealthRequest, "InternalHttpLoadBalancerCaching.groupHealth");

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

  /**
   * Local cache of BackendServiceGroupHealth keyed by BackendService name.
   *
   * <p>It turns out that the types in the GCE Batch callbacks aren't the actual Compute types for
   * some reason, which is why this map is String -> Object.
   */
  private Map<String, List<BackendServiceGroupHealth>> bsNameToGroupHealthsMap = new HashMap<>();

  private Set<GroupHealthRequest> queuedBsGroupHealthRequests = new HashSet<GroupHealthRequest>();
  private Set<LoadBalancerHealthResolution> resolutions =
      new HashSet<LoadBalancerHealthResolution>();

  public class ForwardingRuleCallbacks {
    public ForwardingRuleSingletonCallback newForwardingRuleSingletonCallback() {
      return new ForwardingRuleSingletonCallback();
    }

    public ForwardingRuleListCallback newForwardingRuleListCallback() {
      return new ForwardingRuleListCallback();
    }

    public void cacheRemainderOfLoadBalancerResourceGraph(final ForwardingRule forwardingRule) {
      GoogleInternalHttpLoadBalancer newLoadBalancer = new GoogleInternalHttpLoadBalancer();

      newLoadBalancer.setName(forwardingRule.getName());
      newLoadBalancer.setAccount(getAccountName());
      newLoadBalancer.setRegion(Utils.getLocalName(forwardingRule.getRegion()));
      newLoadBalancer.setCreatedTime(
          Utils.getTimeFromTimestamp(forwardingRule.getCreationTimestamp()));
      newLoadBalancer.setIpAddress(forwardingRule.getIPAddress());
      newLoadBalancer.setIpProtocol(forwardingRule.getIPProtocol());
      newLoadBalancer.setPortRange(forwardingRule.getPortRange());
      newLoadBalancer.setNetwork(forwardingRule.getNetwork());
      newLoadBalancer.setSubnet(forwardingRule.getSubnetwork());
      newLoadBalancer.setHealths(new ArrayList<>());
      newLoadBalancer.setHostRules(new ArrayList<>());
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
                "Non-Http target type found for global forwarding rule "
                    + forwardingRule.getName());
            break;
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public List<GoogleInternalHttpLoadBalancer> getLoadBalancers() {
      return loadBalancers;
    }

    public void setLoadBalancers(List<GoogleInternalHttpLoadBalancer> loadBalancers) {
      this.loadBalancers = loadBalancers;
    }

    private List<GoogleInternalHttpLoadBalancer> loadBalancers;
    private List<String> failedLoadBalancers;
    private GoogleBatchRequest targetProxyRequest;
    private GoogleBatchRequest urlMapRequest;
    private GoogleBatchRequest groupHealthRequest;
    private List<BackendService> projectBackendServices;
    private List<HealthCheck> projectHealthChecks;

    public ForwardingRuleCallbacks(
        List<GoogleInternalHttpLoadBalancer> loadBalancers,
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

    public class ForwardingRuleSingletonCallback extends JsonBatchCallback<ForwardingRule> {
      @Override
      public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        // 404 is thrown if the forwarding rule does not exist in the given region. Any other
        // exception needs to be propagated.
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
              "Not responsible for on demand caching of load balancers without target "
                  + "proxy or with SSL proxy type.");
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
                        && f.getLoadBalancingScheme().equals("INTERNAL_MANAGED"))
            .forEach(
                forwardingRule -> {
                  GoogleTargetProxyType type =
                      forwardingRule.getTarget() != null
                          ? Utils.getTargetProxyType(forwardingRule.getTarget())
                          : null;
                  if (type == HTTP || type == HTTPS) {
                    cacheRemainderOfLoadBalancerResourceGraph(forwardingRule);
                  } else {
                    throw new IllegalArgumentException(
                        "Not responsible for on demand caching of load balancers without target "
                            + "proxy or with SSL proxy type.");
                  }
                });
      }

      @Override
      public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
        log.error(e.getMessage());
      }
    }
  }

  abstract static class BaseCallback<T> extends JsonBatchCallback<T> {
    List<String> failedSubjects;
    String subject;

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
    @Override
    public void onSuccess(TargetHttpsProxy targetHttpsProxy, HttpHeaders responseHeaders)
        throws IOException {
      // SslCertificates is a required field for TargetHttpsProxy, and contains exactly one cert.
      googleLoadBalancer.setCertificate(
          Utils.getLocalName((targetHttpsProxy.getSslCertificates().get(0))));

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

    private GoogleInternalHttpLoadBalancer googleLoadBalancer;
    private GoogleBatchRequest urlMapRequest;
    private GoogleBatchRequest groupHealthRequest;
    private List<BackendService> projectBackendServices;
    private List<HealthCheck> projectHealthChecks;

    public TargetHttpsProxyCallback(
        GoogleInternalHttpLoadBalancer googleLoadBalancer,
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
  }

  public class TargetProxyCallback extends BaseCallback<TargetHttpProxy> {
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

    private GoogleInternalHttpLoadBalancer googleLoadBalancer;
    private GoogleBatchRequest urlMapRequest;
    private GoogleBatchRequest groupHealthRequest;
    private List<BackendService> projectBackendServices;
    private List<HealthCheck> projectHealthChecks;

    public TargetProxyCallback(
        GoogleInternalHttpLoadBalancer googleLoadBalancer,
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
  }

  public class UrlMapCallback extends BaseCallback<UrlMap> {
    @Override
    public void onSuccess(UrlMap urlMap, HttpHeaders responseHeaders) {
      // Check that we aren't stomping on our URL map. If we are, log an error.
      if (googleLoadBalancer.getDefaultService() != null
          || (googleLoadBalancer.getHostRules() != null
              && googleLoadBalancer.getHostRules().size() > 0)) {
        log.error(
            "Overwriting UrlMap "
                + urlMap.getName()
                + ". You may have a TargetHttp(s)Proxy naming collision.");
      }

      googleLoadBalancer.setUrlMapName(urlMap.getName());
      // Queue up the backend services to process.
      Set<String> queuedServices = new HashSet<>();

      // Default service is mandatory.
      String urlMapDefaultService = Utils.getLocalName(urlMap.getDefaultService());
      queuedServices.add(urlMapDefaultService);

      GoogleBackendService service1 = new GoogleBackendService();
      service1.setName(urlMapDefaultService);
      googleLoadBalancer.setDefaultService(service1);
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
              googleLoadBalancer.getHostRules().add(gHostRule);
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

    private GoogleInternalHttpLoadBalancer googleLoadBalancer;
    private List<BackendService> projectBackendServices;
    private List<HealthCheck> projectHealthChecks;
    private GoogleBatchRequest groupHealthRequest;

    public UrlMapCallback(
        GoogleInternalHttpLoadBalancer googleLoadBalancer,
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
  }

  public class GroupHealthCallback extends JsonBatchCallback<BackendServiceGroupHealth> {
    /**
     * Tolerate of the group health calls failing. Spinnaker reports empty load balancer healths as
     * 'unknown'. If healthStatus is null in the onSuccess() function, the same state is reported,
     * so this shouldn't cause issues.
     */
    public void onFailure(final GoogleJsonError e, HttpHeaders responseHeaders) {
      log.debug(
          "Failed backend service group health call for backend service "
              + getBackendServiceName()
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

    public String getBackendServiceName() {
      return backendServiceName;
    }

    public void setBackendServiceName(String backendServiceName) {
      this.backendServiceName = backendServiceName;
    }

    private String backendServiceName;

    public GroupHealthCallback(String backendServiceName) {
      this.backendServiceName = backendServiceName;
    }
  }

  private void handleBackendService(
      BackendService backendService,
      GoogleInternalHttpLoadBalancer googleHttpLoadBalancer,
      List<HealthCheck> healthChecks,
      GoogleBatchRequest groupHealthRequest) {
    if (backendService == null) {
      return;
    }

    final GroupHealthCallback groupHealthCallback =
        new GroupHealthCallback(backendService.getName());

    // We have to update the backend service objects we created from the UrlMapCallback.
    // The UrlMapCallback knows which backend service is the defaultService, etc and the
    // BackendServiceCallback has the actual serving capacity and server group data.
    List<GoogleBackendService> backendServicesInMap =
        Utils.getBackendServicesFromInternalHttpLoadBalancerView(googleHttpLoadBalancer.getView());
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
      // Note: It's possible for a backend service to have backends that point to a null group.
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

    // Note: It's possible for a backend service to have backends that point to a null group.
    if (backendService.getBackends() != null) {
      backendService.getBackends().stream()
          .filter(backend -> backend.getGroup() != null)
          .forEach(
              backend -> {
                ResourceGroupReference resourceGroup = new ResourceGroupReference();
                resourceGroup.setGroup(backend.getGroup());

                // Make only the group health request calls we need to.
                GroupHealthRequest ghr =
                    new GroupHealthRequest(
                        getProject(), backendService.getName(), resourceGroup.getGroup());
                if (!queuedBsGroupHealthRequests.contains(ghr)) {
                  // The groupHealthCallback updates the local cache.
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

  private static void handleHealthCheck(
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
    }
    //    else if (healthCheck.getUdpHealthCheck() != null) {
    //      port = healthCheck.getUdpHealthCheck().getPort();
    //      hcType = GoogleHealthCheck.HealthCheckType.UDP;
    //    }

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
