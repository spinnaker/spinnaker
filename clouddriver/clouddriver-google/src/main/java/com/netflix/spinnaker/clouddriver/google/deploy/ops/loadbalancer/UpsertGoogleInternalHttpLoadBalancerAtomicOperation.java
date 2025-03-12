package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer;

import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.REGIONAL_LOAD_BALANCER_NAMES;
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.REGION_BACKEND_SERVICE_NAMES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.GenericJson;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry;
import com.netflix.spinnaker.clouddriver.google.deploy.description.BaseGoogleInstanceDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException;
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck;
import com.netflix.spinnaker.clouddriver.google.model.GoogleNetwork;
import com.netflix.spinnaker.clouddriver.google.model.GoogleSubnet;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationsRegistry;
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor;
import groovy.lang.Closure;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class UpsertGoogleInternalHttpLoadBalancerAtomicOperation
    extends UpsertGoogleLoadBalancerAtomicOperation {
  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private static final Logger log = LoggerFactory.getLogger(GoogleInternalHttpLoadBalancer.class);
  private static final String BASE_PHASE = "UPSERT_INTERNAL_HTTP_LOAD_BALANCER";
  private static final String PATH_MATCHER_PREFIX = "pm";
  public static final String TARGET_HTTP_PROXY_NAME_PREFIX = "target-http-proxy";
  public static final String TARGET_HTTPS_PROXY_NAME_PREFIX = "target-https-proxy";
  @Autowired private GoogleOperationPoller googleOperationPoller;
  @Autowired private AtomicOperationsRegistry atomicOperationsRegistry;
  @Autowired private GoogleNetworkProvider googleNetworkProvider;
  @Autowired private GoogleSubnetProvider googleSubnetProvider;
  @Autowired private OrchestrationProcessor orchestrationProcessor;
  @Autowired private SafeRetry safeRetry;
  private final UpsertGoogleLoadBalancerDescription description;

  public UpsertGoogleInternalHttpLoadBalancerAtomicOperation(
      UpsertGoogleLoadBalancerDescription description) {
    this.description = description;
  }

  /**
   * minimal command: curl -v -X POST -H "Content-Type: application/json" -d '[{
   * "upsertLoadBalancer": {"credentials": "my-google-account", "loadBalancerType":
   * "INTERNAL_MANAGED", "loadBalancerName": "internal-http-create", "portRange": "80",
   * "backendServiceDiff": [], "defaultService": {"name": "default-backend-service", "backends": [],
   * "healthCheck": {"name": "basic-check", "requestPath": "/", "port": 80, "checkIntervalSec": 1,
   * "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}, "certificate": "",
   * "hostRules": [] }}]' localhost:7002/gce/ops
   *
   * <p>full command: curl -v -X POST -H "Content-Type: application/json" -d '[{
   * "upsertLoadBalancer": {"credentials": "my-google-account", "loadBalancerType":
   * "INTERNAL_MANAGED", "loadBalancerName": "internal-http-create", "portRange": "80",
   * "backendServiceDiff": [], "defaultService": {"name": "default-backend-service", "backends": [],
   * "healthCheck": {"name": "basic-check", "requestPath": "/", "port": 80, "checkIntervalSec": 1,
   * "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}, "certificate": "",
   * "hostRules": [{"hostPatterns": ["host1.com", "host2.com"], "pathMatcher": {"pathRules":
   * [{"paths": ["/path", "/path2/more"], "backendService": {"name": "backend-service", "backends":
   * [], "healthCheck": {"name": "health-check", "requestPath": "/", "port": 80, "checkIntervalSec":
   * 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}}], "defaultService":
   * {"name": "pm-backend-service", "backends": [], "healthCheck": {"name": "derp-check",
   * "requestPath": "/", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1,
   * "unhealthyThreshold": 1}}}}]}}]' localhost:7002/gce/ops
   *
   * @param description
   * @param priorOutputs
   * @return
   */
  @Override
  public Map operate(List priorOutputs) {
    GoogleNetwork network =
        GCEUtil.queryNetwork(
            description.getAccountName(),
            description.getNetwork(),
            getTask(),
            BASE_PHASE,
            googleNetworkProvider);
    GoogleSubnet subnet =
        GCEUtil.querySubnet(
            description.getAccountName(),
            description.getRegion(),
            description.getSubnet(),
            getTask(),
            BASE_PHASE,
            googleSubnetProvider);
    GoogleInternalHttpLoadBalancer internalHttpLoadBalancer = new GoogleInternalHttpLoadBalancer();

    internalHttpLoadBalancer.setName(description.getLoadBalancerName());
    internalHttpLoadBalancer.setUrlMapName(description.getUrlMapName());
    internalHttpLoadBalancer.setDefaultService(description.getDefaultService());
    internalHttpLoadBalancer.setHostRules(
        description.getHostRules() != null ? description.getHostRules() : new ArrayList<>());
    internalHttpLoadBalancer.setCertificate(description.getCertificate());
    internalHttpLoadBalancer.setIpAddress(description.getIpAddress());
    internalHttpLoadBalancer.setIpProtocol(description.getIpProtocol());
    internalHttpLoadBalancer.setNetwork(network.getSelfLink());
    internalHttpLoadBalancer.setSubnet(subnet.getSelfLink());
    internalHttpLoadBalancer.setPortRange(description.getPortRange());

    String internalHttpLoadBalancerName = internalHttpLoadBalancer.getName();

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing upsert of Internal HTTP load balancer "
                + internalHttpLoadBalancerName
                + "...");

    if (description.getCredentials() == null) {
      throw new IllegalArgumentException(
          "Unable to resolve credentials for Google account '"
              + description.getAccountName()
              + "'.");
    }

    Compute compute = description.getCredentials().getCompute();
    String project = description.getCredentials().getProject();
    String region = description.getRegion();

    // Step 0: Set up state to formulate a plan for creating or updating the L7 LB.

    Set<String> healthCheckExistsSet = new HashSet<>();
    Set<String> healthCheckNeedsUpdatedSet = new HashSet<>();
    Set<String> serviceExistsSet = new HashSet<>();
    Set<String> serviceNeedsUpdatedSet = new HashSet<>();
    boolean urlMapExists;
    boolean targetProxyExists = false;
    boolean targetProxyNeedsUpdated = false;
    boolean forwardingRuleExists;

    // The following are unique on object equality, not just name. This lets us check if a
    // service/hc exists or
    // needs updated by _name_ later.
    List<GoogleBackendService> backendServicesFromDescription =
        ImmutableSet.copyOf(
                Utils.getBackendServicesFromInternalHttpLoadBalancerView(
                    internalHttpLoadBalancer.getView()))
            .asList();
    List<GoogleHealthCheck> healthChecksFromDescription =
        backendServicesFromDescription.stream()
            .map(GoogleBackendService::getHealthCheck)
            .distinct()
            .collect(toList());

    final String name = internalHttpLoadBalancer.getUrlMapName();
    String urlMapName =
        name != null
            ? name
            : internalHttpLoadBalancerName; // An L7 load balancer is identified by its UrlMap name
    // in Google Cloud Console.

    // Get all the existing infrastructure.

    // Look up the legacy health checks so we can do the work to transition smoothly to the UHCs.
    try {
      List<HealthCheck> existingHealthChecks =
          timeExecute(
                  compute.regionHealthChecks().list(project, region),
                  "compute.regionHealthChecks.list",
                  TAG_SCOPE,
                  SCOPE_REGIONAL,
                  TAG_REGION,
                  region)
              .getItems();
      List<BackendService> existingServices =
          timeExecute(
                  compute.regionBackendServices().list(project, region),
                  "compute.regionBackendServices.list",
                  TAG_SCOPE,
                  SCOPE_REGIONAL,
                  TAG_REGION,
                  region)
              .getItems();
      UrlMap existingUrlMap = null;
      try {
        existingUrlMap =
            timeExecute(
                compute.regionUrlMaps().get(project, region, urlMapName),
                "compute.regionUrlMaps.get",
                TAG_SCOPE,
                SCOPE_REGIONAL,
                TAG_REGION,
                region);
      } catch (GoogleJsonResponseException e) {
        // 404 is thrown if the url map doesn't exist. Any other exception needs to be propagated.
        if (e.getStatusCode() != 404) {
          throw e;
        }
      }

      // Determine if the infrastructure in the description exists already.
      // If it does, check and see if we need to update it from the description.

      // UrlMap
      urlMapExists = existingUrlMap != null;

      // ForwardingRule
      ForwardingRule existingRule = null;
      try {
        existingRule =
            timeExecute(
                compute.forwardingRules().get(project, region, internalHttpLoadBalancerName),
                "compute.forwardingRules.get",
                TAG_SCOPE,
                SCOPE_REGIONAL,
                TAG_REGION,
                region);
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() != 404) {
          throw e;
        }
      }

      forwardingRuleExists = existingRule != null;

      // TargetProxy
      GenericJson existingProxy = null;
      if (forwardingRuleExists) {
        String targetProxyName = GCEUtil.getLocalName(existingRule.getTarget());
        switch (Utils.getTargetProxyType(existingRule.getTarget())) {
          case HTTP:
            existingProxy =
                timeExecute(
                    compute.regionTargetHttpProxies().get(project, region, targetProxyName),
                    "compute.regionTargetHttpProxies.get",
                    TAG_SCOPE,
                    SCOPE_REGIONAL,
                    TAG_REGION,
                    region);
            break;
          case HTTPS:
            existingProxy =
                timeExecute(
                    compute.regionTargetHttpsProxies().get(project, region, targetProxyName),
                    "compute.regionTargetHttpsProxies.get",
                    TAG_SCOPE,
                    SCOPE_REGIONAL,
                    TAG_REGION,
                    region);
            if (!StringGroovyMethods.asBoolean(internalHttpLoadBalancer.getCertificate())) {
              throw new IllegalArgumentException(
                  internalHttpLoadBalancerName
                      + " is an Https load balancer, but the upsert description does not contain a certificate.");
            }

            targetProxyNeedsUpdated =
                !GCEUtil.getLocalName(
                        ((TargetHttpsProxy) existingProxy).getSslCertificates().get(0))
                    .equals(
                        GCEUtil.getLocalName(
                            GCEUtil.buildCertificateUrl(
                                project, internalHttpLoadBalancer.getCertificate())));
            break;
          default:
            log.warn("Unexpected target proxy type for " + targetProxyName + ".");
            break;
        }
        targetProxyExists = existingProxy != null;
        if (targetProxyExists
            && !GCEUtil.getLocalName((String) existingProxy.get("urlMap"))
                .equals(description.getUrlMapName())) {
          throw new IllegalStateException(
              "Listener with name "
                  + existingRule.getName()
                  + " already exists and points to url map: "
                  + GCEUtil.getLocalName((String) existingProxy.get("urlMap"))
                  + ","
                  + " which is different from the description url map: "
                  + description.getUrlMapName()
                  + ".");
        }
      }

      // HealthChecks
      if (healthChecksFromDescription.size()
          != healthChecksFromDescription.stream()
              .map(GoogleHealthCheck::getName)
              .distinct()
              .count()) {
        throw new GoogleOperationException(
            "Duplicate health checks with different attributes in the description. Please specify one object per named health check.");
      }

      for (GoogleHealthCheck healthCheck : healthChecksFromDescription) {
        String healthCheckName = healthCheck.getName();

        existingHealthChecks.stream()
            .filter(e -> e.getName().equals(healthCheckName))
            .findFirst()
            .ifPresent(
                existingHealthCheck -> {
                  healthCheckExistsSet.add(healthCheck.getName());
                  if (GCEUtil.healthCheckShouldBeUpdated(existingHealthCheck, healthCheck)) {
                    healthCheckNeedsUpdatedSet.add(healthCheck.getName());
                  }
                });
      }

      // BackendServices
      if (backendServicesFromDescription.size()
          != backendServicesFromDescription.stream()
              .map(GoogleBackendService::getName)
              .distinct()
              .count()) {
        throw new GoogleOperationException(
            "Duplicate backend services with different attributes in the description. Please specify one object per named backend service.");
      }

      for (GoogleBackendService backendService : backendServicesFromDescription) {
        final String backendServiceName = backendService.getName();

        existingServices.stream()
            .filter(e -> e.getName().equals(backendServiceName))
            .findFirst()
            .ifPresent(
                existingService -> {
                  serviceExistsSet.add(backendService.getName());

                  Set<String> existingHcs =
                      existingService.getHealthChecks() == null
                          ? new HashSet<>()
                          : existingService.getHealthChecks().stream()
                              .map(GCEUtil::getLocalName)
                              .collect(toSet());
                  Boolean differentHealthChecks =
                      Sets.difference(
                                  existingHcs,
                                  ImmutableSet.of(backendService.getHealthCheck().getName()))
                              .size()
                          > 0;
                  Boolean differentSessionAffinity =
                      !GoogleSessionAffinity.valueOf(existingService.getSessionAffinity())
                          .equals(backendService.getSessionAffinity());
                  Boolean differentSessionCookieTtl =
                      !Objects.equals(
                          existingService.getAffinityCookieTtlSec(),
                          backendService.getAffinityCookieTtlSec());
                  Boolean differentPortName =
                      !Objects.equals(existingService.getPortName(), backendService.getPortName());
                  Integer drainingSec =
                      existingService.getConnectionDraining() == null
                          ? 0
                          : existingService.getConnectionDraining().getDrainingTimeoutSec();
                  Boolean differentConnectionDraining =
                      !Objects.equals(
                          drainingSec, backendService.getConnectionDrainingTimeoutSec());
                  if (differentHealthChecks
                      || differentSessionAffinity
                      || differentSessionCookieTtl
                      || differentPortName
                      || differentConnectionDraining) {
                    serviceNeedsUpdatedSet.add(backendService.getName());
                  }
                });
      }

      // Step 1: If there are no existing components in GCE, insert the new L7 components.
      // If something exists and needs updated, update it. Else do nothing.

      // HealthChecks
      for (GoogleHealthCheck healthCheck : healthChecksFromDescription) {
        String healthCheckName = healthCheck.getName();

        if (!healthCheckExistsSet.contains(healthCheck.getName())) {
          getTask()
              .updateStatus(
                  BASE_PHASE, "Creating health check " + healthCheckName + " in " + region + "...");
          HealthCheck newHealthCheck = GCEUtil.createNewHealthCheck(healthCheck);
          Operation insertHealthCheckOperation =
              timeExecute(
                  compute.regionHealthChecks().insert(project, region, newHealthCheck),
                  "compute.regionHealthChecks.insert",
                  TAG_SCOPE,
                  SCOPE_REGIONAL,
                  TAG_REGION,
                  region);
          googleOperationPoller.waitForRegionalOperation(
              compute,
              project,
              region,
              insertHealthCheckOperation.getName(),
              null,
              getTask(),
              "region health check " + healthCheckName,
              BASE_PHASE);
        } else if (healthCheckExistsSet.contains(healthCheck.getName())
            && healthCheckNeedsUpdatedSet.contains(healthCheck.getName())) {
          getTask().updateStatus(BASE_PHASE, "Updating health check " + healthCheckName + "...");
          HealthCheck hcToUpdate =
              existingHealthChecks.stream()
                  .filter(hc -> hc.getName().equals(healthCheckName))
                  .findFirst()
                  .get();
          GCEUtil.updateExistingHealthCheck(hcToUpdate, healthCheck);
          Operation updateHealthCheckOperation =
              timeExecute(
                  compute.regionHealthChecks().update(project, region, healthCheckName, hcToUpdate),
                  "compute.regionHealthChecks.update",
                  TAG_SCOPE,
                  SCOPE_REGIONAL,
                  TAG_REGION,
                  region);
          googleOperationPoller.waitForRegionalOperation(
              compute,
              project,
              region,
              updateHealthCheckOperation.getName(),
              null,
              getTask(),
              "region health check " + healthCheckName,
              BASE_PHASE);
        }
      }

      // BackendServices
      for (GoogleBackendService backendService : backendServicesFromDescription) {
        String backendServiceName = backendService.getName();
        String sessionAffinity =
            backendService.getSessionAffinity() != null
                ? backendService.getSessionAffinity().toString()
                : "NONE";

        if (!serviceExistsSet.contains(backendService.getName())) {
          getTask()
              .updateStatus(
                  BASE_PHASE,
                  "Creating backend service " + backendServiceName + " in " + region + "...");
          BackendService service = new BackendService();

          BackendService bs = service.setName(backendServiceName);
          service.setLoadBalancingScheme("INTERNAL_MANAGED");
          service.setPortName(
              backendService.getPortName() != null
                  ? backendService.getPortName()
                  : GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME);
          service.setConnectionDraining(
              new ConnectionDraining()
                  .setDrainingTimeoutSec(backendService.getConnectionDrainingTimeoutSec()));
          service.setHealthChecks(
              Arrays.asList(
                  GCEUtil.buildRegionalHealthCheckUrl(
                      project, region, backendService.getHealthCheck().getName())));
          service.setSessionAffinity(sessionAffinity);
          service.setAffinityCookieTtlSec(backendService.getAffinityCookieTtlSec());
          Operation insertBackendServiceOperation =
              timeExecute(
                  compute.regionBackendServices().insert(project, region, bs),
                  "compute.regionBackendServices.insert",
                  TAG_SCOPE,
                  SCOPE_REGIONAL,
                  TAG_REGION,
                  region);
          googleOperationPoller.waitForRegionalOperation(
              compute,
              project,
              region,
              insertBackendServiceOperation.getName(),
              null,
              getTask(),
              "region backend service " + backendServiceName,
              BASE_PHASE);
        } else if (serviceExistsSet.contains(backendService.getName())) {
          // Update the actual backend service if necessary.
          if (serviceNeedsUpdatedSet.contains(backendService.getName())) {
            getTask()
                .updateStatus(
                    BASE_PHASE,
                    "Updating backend service " + backendServiceName + " in " + region + "...");
            BackendService bsToUpdate =
                existingServices.stream()
                    .filter(s -> s.getName().equals(backendServiceName))
                    .findFirst()
                    .get();
            String hcName = backendService.getHealthCheck().getName();
            bsToUpdate.setPortName(
                backendService.getPortName() != null
                    ? backendService.getPortName()
                    : GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME);
            bsToUpdate.setConnectionDraining(
                new ConnectionDraining()
                    .setDrainingTimeoutSec(backendService.getConnectionDrainingTimeoutSec()));
            bsToUpdate.setHealthChecks(
                Arrays.asList(GCEUtil.buildRegionalHealthCheckUrl(project, region, hcName)));
            bsToUpdate.setSessionAffinity(sessionAffinity);
            bsToUpdate.setAffinityCookieTtlSec(backendService.getAffinityCookieTtlSec());

            Operation updateServiceOperation =
                timeExecute(
                    compute
                        .regionBackendServices()
                        .update(project, region, backendServiceName, bsToUpdate),
                    "compute.regionBackendServices.update",
                    TAG_SCOPE,
                    SCOPE_REGIONAL,
                    TAG_REGION,
                    region);
            googleOperationPoller.waitForRegionalOperation(
                compute,
                project,
                region,
                updateServiceOperation.getName(),
                null,
                getTask(),
                "region backend service  " + backendServiceName,
                BASE_PHASE);
          }

          fixBackendMetadata(
              compute,
              description.getCredentials(),
              project,
              getAtomicOperationsRegistry(),
              getOrchestrationProcessor(),
              description.getLoadBalancerName(),
              backendService);
        }
      }
      if (description.getBackendServiceDiff() != null) {
        for (GoogleBackendService backendService : description.getBackendServiceDiff()) {
          fixBackendMetadata(
              compute,
              description.getCredentials(),
              project,
              getAtomicOperationsRegistry(),
              getOrchestrationProcessor(),
              description.getLoadBalancerName(),
              backendService);
        }
      }

      // UrlMap
      String urlMapUrl = null;
      if (!urlMapExists) {
        getTask()
            .updateStatus(BASE_PHASE, "Creating URL map " + urlMapName + " in " + region + "...");
        UrlMap newUrlMap = new UrlMap();
        newUrlMap.setName(urlMapName);
        newUrlMap.setHostRules(new ArrayList<>());
        newUrlMap.setPathMatchers(new ArrayList<>());
        newUrlMap.setDefaultService(
            GCEUtil.buildRegionBackendServiceUrl(
                project, region, internalHttpLoadBalancer.getDefaultService().getName()));
        for (GoogleHostRule hostRule : internalHttpLoadBalancer.getHostRules()) {
          String pathMatcherName = PATH_MATCHER_PREFIX + "-" + UUID.randomUUID().toString();
          GooglePathMatcher pathMatcher = hostRule.getPathMatcher();
          PathMatcher matcher = new PathMatcher();
          matcher.setDefaultService(
              GCEUtil.buildRegionBackendServiceUrl(
                  project, region, pathMatcher.getDefaultService().getName()));
          matcher.setPathRules(
              pathMatcher.getPathRules().stream()
                  .map(
                      p -> {
                        PathRule rule = new PathRule();
                        rule.setPaths(p.getPaths());
                        rule.setService(
                            GCEUtil.buildRegionBackendServiceUrl(
                                project, region, p.getBackendService().getName()));
                        return rule;
                      })
                  .collect(toList()));
          newUrlMap.getPathMatchers().add(matcher);

          HostRule rule = new HostRule();
          rule.setHosts(hostRule.getHostPatterns());
          rule.setPathMatcher(pathMatcherName);
          newUrlMap.getHostRules().add(rule);
        }
        Operation insertUrlMapOperation =
            timeExecute(
                compute.regionUrlMaps().insert(project, region, newUrlMap),
                "compute.regionUrlMaps.insert",
                TAG_SCOPE,
                SCOPE_REGIONAL,
                TAG_REGION,
                region);
        googleOperationPoller.waitForRegionalOperation(
            compute,
            project,
            region,
            insertUrlMapOperation.getName(),
            null,
            getTask(),
            "region url map " + urlMapName,
            BASE_PHASE);
        urlMapUrl = insertUrlMapOperation.getTargetLink();
      } else if (urlMapExists) {
        getTask()
            .updateStatus(BASE_PHASE, "Updating URL map " + urlMapName + " in " + region + "...");
        existingUrlMap.setDefaultService(
            GCEUtil.buildRegionBackendServiceUrl(
                project, region, internalHttpLoadBalancer.getDefaultService().getName()));
        existingUrlMap.setPathMatchers(new ArrayList<>());
        existingUrlMap.setHostRules(new ArrayList<>());
        for (GoogleHostRule hostRule : internalHttpLoadBalancer.getHostRules()) {
          String pathMatcherName = PATH_MATCHER_PREFIX + "-" + UUID.randomUUID().toString();
          GooglePathMatcher pathMatcher = hostRule.getPathMatcher();
          PathMatcher matcher = new com.google.api.services.compute.model.PathMatcher();
          matcher.setName(pathMatcherName);
          matcher.setDefaultService(
              GCEUtil.buildRegionBackendServiceUrl(
                  project, region, pathMatcher.getDefaultService().getName()));
          matcher.setPathRules(
              pathMatcher.getPathRules().stream()
                  .map(
                      p -> {
                        PathRule rule = new PathRule();
                        rule.setService(
                            GCEUtil.buildRegionBackendServiceUrl(
                                project, region, p.getBackendService().getName()));
                        rule.setPaths(p.getPaths());
                        return rule;
                      })
                  .collect(toList()));
          existingUrlMap.getPathMatchers().add(matcher);
          HostRule rule = new HostRule();
          rule.setHosts(hostRule.getHostPatterns());
          existingUrlMap.getHostRules().add(rule.setPathMatcher(pathMatcherName));
        }
        Operation updateUrlMapOperation =
            timeExecute(
                compute.regionUrlMaps().update(project, region, urlMapName, existingUrlMap),
                "compute.regionUrlMaps.update",
                TAG_SCOPE,
                SCOPE_REGIONAL,
                TAG_REGION,
                region);
        googleOperationPoller.waitForRegionalOperation(
            compute,
            project,
            region,
            updateUrlMapOperation.getName(),
            null,
            getTask(),
            "region url map " + urlMapName,
            BASE_PHASE);
        urlMapUrl = updateUrlMapOperation.getTargetLink();
      } else {
        urlMapUrl = existingUrlMap.getSelfLink();
      }

      // TargetProxy
      String targetProxyName;
      Object targetProxy;
      Operation insertTargetProxyOperation;
      String targetProxyUrl = null;
      if (!targetProxyExists) {
        if (!StringUtils.isEmpty(internalHttpLoadBalancer.getCertificate())) {
          targetProxyName = internalHttpLoadBalancerName + "-" + TARGET_HTTPS_PROXY_NAME_PREFIX;
          getTask()
              .updateStatus(
                  BASE_PHASE, "Creating target proxy " + targetProxyName + " in " + region + "...");
          TargetHttpsProxy proxy = new TargetHttpsProxy();
          proxy.setSslCertificates(
              Arrays.asList(
                  GCEUtil.buildCertificateUrl(project, internalHttpLoadBalancer.getCertificate())));
          proxy.setUrlMap(urlMapUrl);
          proxy.setName(targetProxyName);
          targetProxy = proxy;
          insertTargetProxyOperation =
              timeExecute(
                  compute
                      .regionTargetHttpsProxies()
                      .insert(project, region, (TargetHttpsProxy) targetProxy),
                  "compute.regionTargetHttpsProxies.insert",
                  TAG_SCOPE,
                  SCOPE_REGIONAL,
                  TAG_REGION,
                  region);
        } else {
          targetProxyName = internalHttpLoadBalancerName + "-" + TARGET_HTTP_PROXY_NAME_PREFIX;
          getTask()
              .updateStatus(
                  BASE_PHASE, "Creating target proxy " + targetProxyName + " in " + region + "...");
          TargetHttpProxy proxy = new TargetHttpProxy();
          proxy.setName(targetProxyName);
          proxy.setUrlMap(urlMapUrl);
          targetProxy = proxy;
          insertTargetProxyOperation =
              timeExecute(
                  compute
                      .regionTargetHttpProxies()
                      .insert(project, region, (TargetHttpProxy) targetProxy),
                  "compute.regionTargetHttpProxies.insert",
                  TAG_SCOPE,
                  SCOPE_REGIONAL,
                  TAG_REGION,
                  region);
        }

        googleOperationPoller.waitForRegionalOperation(
            compute,
            project,
            region,
            insertTargetProxyOperation.getName(),
            null,
            getTask(),
            "region target proxy " + targetProxyName,
            BASE_PHASE);
        targetProxyUrl = insertTargetProxyOperation.getTargetLink();
      } else if (targetProxyExists && targetProxyNeedsUpdated) {
        GoogleTargetProxyType proxyType =
            Utils.getTargetProxyType((String) existingProxy.get("selfLink"));
        switch (proxyType) {
          case HTTP:
            break;
          case HTTPS:
            targetProxyName = internalHttpLoadBalancerName + "-" + TARGET_HTTPS_PROXY_NAME_PREFIX;
            getTask()
                .updateStatus(
                    BASE_PHASE,
                    "Updating target proxy " + targetProxyName + " in " + region + "...");
            RegionTargetHttpsProxiesSetSslCertificatesRequest request =
                new RegionTargetHttpsProxiesSetSslCertificatesRequest();
            RegionTargetHttpsProxiesSetSslCertificatesRequest setSslReq =
                request.setSslCertificates(
                    Arrays.asList(
                        GCEUtil.buildRegionalCertificateUrl(
                            project, region, internalHttpLoadBalancer.getCertificate())));
            Operation sslCertOp =
                timeExecute(
                    compute
                        .regionTargetHttpsProxies()
                        .setSslCertificates(project, region, targetProxyName, setSslReq),
                    "compute.regionTargetHttpsProxies.setSslCertificates",
                    TAG_SCOPE,
                    SCOPE_REGIONAL,
                    TAG_REGION,
                    region);
            googleOperationPoller.waitForRegionalOperation(
                compute,
                project,
                region,
                sslCertOp.getName(),
                null,
                getTask(),
                "set ssl cert " + internalHttpLoadBalancer.getCertificate(),
                BASE_PHASE);
            UrlMapReference reference = new UrlMapReference();
            UrlMapReference urlMapRef = reference.setUrlMap(urlMapUrl);
            Operation setUrlMapOp =
                timeExecute(
                    compute
                        .regionTargetHttpsProxies()
                        .setUrlMap(project, region, targetProxyName, urlMapRef),
                    "compute.regionTargetHttpsProxies.setUrlMap",
                    TAG_SCOPE,
                    SCOPE_REGIONAL,
                    TAG_REGION,
                    region);
            googleOperationPoller.waitForRegionalOperation(
                compute,
                project,
                region,
                setUrlMapOp.getName(),
                null,
                getTask(),
                "set urlMap " + urlMapUrl + " for target proxy " + targetProxyName,
                BASE_PHASE);
            targetProxyUrl = setUrlMapOp.getTargetLink();
            break;
          default:
            throw new IllegalStateException(
                "Updating Internal Http load balancer "
                    + internalHttpLoadBalancerName
                    + " in "
                    + region
                    + " failed. Could not update target proxy; Illegal target proxy type "
                    + proxyType
                    + ".");
        }
      } else {
        targetProxyUrl = (String) existingProxy.get("selfLink");
      }

      // ForwardingRule
      if (!forwardingRuleExists) {
        getTask()
            .updateStatus(
                BASE_PHASE,
                "Creating internal forwarding rule "
                    + internalHttpLoadBalancerName
                    + " in "
                    + region
                    + "...");
        ForwardingRule rule = new ForwardingRule();

        rule.setName(internalHttpLoadBalancerName);
        rule.setLoadBalancingScheme("INTERNAL_MANAGED");
        rule.setIPAddress(internalHttpLoadBalancer.getIpAddress());
        rule.setIPProtocol(internalHttpLoadBalancer.getIpProtocol());
        rule.setNetwork(internalHttpLoadBalancer.getNetwork());
        rule.setSubnetwork(internalHttpLoadBalancer.getSubnet());
        rule.setPortRange(
            StringGroovyMethods.asBoolean(internalHttpLoadBalancer.getCertificate())
                ? "443"
                : internalHttpLoadBalancer.getPortRange());
        rule.setTarget(targetProxyUrl);

        Operation forwardingRuleOp =
            safeRetry.doRetry(
                new Closure<Operation>(this, this) {
                  @Override
                  public Operation call() {
                    try {
                      return timeExecute(
                          compute.forwardingRules().insert(project, region, rule),
                          "compute.forwardingRules.insert",
                          TAG_SCOPE,
                          SCOPE_REGIONAL,
                          TAG_REGION,
                          region);
                    } catch (IOException e) {
                      throw new UncheckedIOException(e);
                    }
                  }
                },
                "forwarding rule " + description.getLoadBalancerName(),
                getTask(),
                Arrays.asList(400, 403, 412),
                new ArrayList<>(),
                ImmutableMap.of(
                    "action",
                    "insert",
                    "phase",
                    BASE_PHASE,
                    "operation",
                    "compute.forwardingRules.insert",
                    TAG_SCOPE,
                    SCOPE_REGIONAL,
                    TAG_REGION,
                    region),
                getRegistry());

        // Orca's orchestration for upserting a Google load balancer does not contain a task
        // to wait for the state of the platform to show that a load balancer was created (for good
        // reason,
        // that would be a complicated operation). Instead, Orca waits for Clouddriver to execute
        // this operation
        // and do a force cache refresh. We should wait for the whole load balancer to be created in
        // the platform
        // before we exit this upsert operation, so we wait for the forwarding rule to be created
        // before continuing
        // so we _know_ the state of the platform when we do a force cache refresh.
        googleOperationPoller.waitForRegionalOperation(
            compute,
            project,
            region,
            forwardingRuleOp.getName(),
            null,
            getTask(),
            "forwarding rule " + internalHttpLoadBalancerName,
            BASE_PHASE);
      }

      // NOTE: there is no update for forwarding rules because we support adding/deleting multiple
      // listeners in the frontend.
      // Rotating or changing certificates updates the targetProxy only, so the forwarding rule
      // doesn't need to change.

      // Delete extraneous listeners.
      if (description.getListenersToDelete() != null) {
        for (String forwardingRuleName : description.getListenersToDelete()) {
          getTask()
              .updateStatus(
                  BASE_PHASE, "Deleting listener " + forwardingRuleName + " in " + region + "...");
          GCEUtil.deleteRegionalListener(
              compute,
              project,
              region,
              forwardingRuleName,
              BASE_PHASE,
              getSafeRetry(),
              UpsertGoogleInternalHttpLoadBalancerAtomicOperation.this);
        }
      }
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Done upserting Internal HTTP load balancer "
                  + internalHttpLoadBalancerName
                  + " in "
                  + region);

      Map<String, String> lb = new HashMap<>(1);
      lb.put("name", internalHttpLoadBalancerName);
      Map<String, Map<String, String>> regionToLb = new HashMap<>(1);
      regionToLb.put("region", lb);

      Map<String, Map<String, Map<String, String>>> lbs = new HashMap<>(1);
      lbs.put("loadBalancers", regionToLb);
      return lbs;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Update each instance template on all the server groups in the backend service to reflect being
   * added to the new load balancer.
   *
   * @param compute
   * @param credentials
   * @param project
   * @param loadBalancerName
   * @param backendService
   */
  private void fixBackendMetadata(
      Compute compute,
      GoogleNamedAccountCredentials credentials,
      String project,
      AtomicOperationsRegistry atomicOperationsRegistry,
      OrchestrationProcessor orchestrationProcessor,
      String loadBalancerName,
      GoogleBackendService backendService) {
    try {
      for (GoogleLoadBalancedBackend backend : backendService.getBackends()) {

        String groupName = Utils.getLocalName(backend.getServerGroupUrl());
        String groupRegion = Utils.getRegionFromGroupUrl(backend.getServerGroupUrl());
        String templateUrl = null;
        switch (Utils.determineServerGroupType(backend.getServerGroupUrl())) {
          case REGIONAL:
            templateUrl =
                timeExecute(
                        compute.regionInstanceGroupManagers().get(project, groupRegion, groupName),
                        "compute.regionInstanceGroupManagers.get",
                        TAG_SCOPE,
                        SCOPE_REGIONAL,
                        TAG_REGION,
                        groupRegion)
                    .getInstanceTemplate();
            break;
          case ZONAL:
            String groupZone = Utils.getZoneFromGroupUrl(backend.getServerGroupUrl());
            templateUrl =
                timeExecute(
                        compute.instanceGroupManagers().get(project, groupZone, groupName),
                        "compute.instanceGroupManagers.get",
                        TAG_SCOPE,
                        SCOPE_ZONAL,
                        TAG_ZONE,
                        groupZone)
                    .getInstanceTemplate();
            break;
          default:
            throw new IllegalStateException(
                "Server group referenced by " + backend.getServerGroupUrl() + " has illegal type.");
        }

        InstanceTemplate template =
            timeExecute(
                compute.instanceTemplates().get(project, Utils.getLocalName(templateUrl)),
                "compute.instancesTemplates.get",
                TAG_SCOPE,
                SCOPE_GLOBAL);
        BaseGoogleInstanceDescription instanceDescription =
            GCEUtil.buildInstanceDescriptionFromTemplate(project, template);

        Map<String, Object> templateOpMap = new HashMap<>(15);
        templateOpMap.put("image", instanceDescription.getImage());
        templateOpMap.put("instanceType", instanceDescription.getInstanceType());
        templateOpMap.put("credentials", credentials.getName());
        templateOpMap.put("disks", instanceDescription.getDisks());
        templateOpMap.put("instanceMetadata", instanceDescription.getInstanceMetadata());
        templateOpMap.put("tags", instanceDescription.getTags());
        templateOpMap.put("network", instanceDescription.getNetwork());
        templateOpMap.put("subnet", instanceDescription.getSubnet());
        templateOpMap.put("serviceAccountEmail", instanceDescription.getServiceAccountEmail());
        templateOpMap.put("authScopes", instanceDescription.getAuthScopes());
        templateOpMap.put("preemptible", instanceDescription.getPreemptible());
        templateOpMap.put("automaticRestart", instanceDescription.getAutomaticRestart());
        templateOpMap.put("onHostMaintenance", instanceDescription.getOnHostMaintenance());
        templateOpMap.put("region", groupRegion);
        templateOpMap.put("serverGroupName", groupName);

        if (StringGroovyMethods.asBoolean(instanceDescription.getMinCpuPlatform())) {
          templateOpMap.put("minCpuPlatform", instanceDescription.getMinCpuPlatform());
        }

        if (templateOpMap.containsKey("instanceMetadata")) {
          Map<String, String> instanceMetadata = (Map) templateOpMap.get("instanceMetadata");
          String regionLbStr = instanceMetadata.get(REGIONAL_LOAD_BALANCER_NAMES);
          List<String> regionalLbs =
              regionLbStr != null
                  ? new ArrayList<>(Arrays.asList(regionLbStr.split(",")))
                  : new ArrayList<>();
          regionalLbs.add(loadBalancerName);
          instanceMetadata.put(
              REGIONAL_LOAD_BALANCER_NAMES,
              regionalLbs.stream().distinct().collect(Collectors.joining(",")));

          String backendsStr = instanceMetadata.get(REGION_BACKEND_SERVICE_NAMES);
          List<String> bsNames =
              backendsStr != null
                  ? new ArrayList<>(Arrays.asList(backendsStr.split(",")))
                  : new ArrayList<>();
          bsNames.add(backendService.getName());
          instanceMetadata.put(
              REGION_BACKEND_SERVICE_NAMES,
              bsNames.stream().distinct().collect(Collectors.joining(",")));
        } else {
          Map<String, String> instanceMetadata = new HashMap<>(2);
          instanceMetadata.put(REGIONAL_LOAD_BALANCER_NAMES, loadBalancerName);
          instanceMetadata.put(REGION_BACKEND_SERVICE_NAMES, backendService.getName());
          templateOpMap.put("instanceMetadata", instanceMetadata);
        }

        AtomicOperationConverter converter =
            atomicOperationsRegistry.getAtomicOperationConverter(
                "modifyGoogleServerGroupInstanceTemplateDescription", "gce");
        AtomicOperation templateOp = converter.convertOperation(templateOpMap);
        orchestrationProcessor.process(
            "gce", new ArrayList<>(Arrays.asList(templateOp)), UUID.randomUUID().toString());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public AtomicOperationsRegistry getAtomicOperationsRegistry() {
    return atomicOperationsRegistry;
  }

  public void setAtomicOperationsRegistry(AtomicOperationsRegistry atomicOperationsRegistry) {
    this.atomicOperationsRegistry = atomicOperationsRegistry;
  }

  public OrchestrationProcessor getOrchestrationProcessor() {
    return orchestrationProcessor;
  }

  public void setOrchestrationProcessor(OrchestrationProcessor orchestrationProcessor) {
    this.orchestrationProcessor = orchestrationProcessor;
  }

  public SafeRetry getSafeRetry() {
    return safeRetry;
  }

  public void setSafeRetry(SafeRetry safeRetry) {
    this.safeRetry = safeRetry;
  }
}
