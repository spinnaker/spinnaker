package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer;

import static java.lang.String.format;

import com.google.api.client.json.GenericJson;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry;
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException;
import com.netflix.spinnaker.clouddriver.googlecommon.deploy.GoogleApiException;
import groovy.lang.Closure;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

public class DeleteGoogleInternalHttpLoadBalancerAtomicOperation
    extends DeleteGoogleLoadBalancerAtomicOperation {
  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private static void addServicesFromPathMatchers(
      List<String> backendServiceUrls, List<PathMatcher> pathMatchers) {
    if (pathMatchers == null) return;
    for (PathMatcher pathMatcher : pathMatchers) {
      backendServiceUrls.add(pathMatcher.getDefaultService());
      for (PathRule pathRule : pathMatcher.getPathRules()) {
        backendServiceUrls.add(pathRule.getService());
      }
    }
  }

  private static final String BASE_PHASE = "DELETE_INTERNAL_HTTP_LOAD_BALANCER";
  @Autowired private SafeRetry safeRetry;
  @Autowired private GoogleOperationPoller googleOperationPoller;
  private DeleteGoogleLoadBalancerDescription description;

  public DeleteGoogleInternalHttpLoadBalancerAtomicOperation(
      DeleteGoogleLoadBalancerDescription description) {
    this.description = description;
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": { "credentials":
   * "my-account-name", "loadBalancerName": "spin-lb", "deleteHealthChecks": false,
   * "loadBalancerType": "HTTP"}} ]' localhost:7002/gce/ops
   */
  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            format(
                "Initializing deletion of Internal HTTP load balancer %s...",
                description.getLoadBalancerName()));

    if (description.getCredentials() == null) {
      throw new IllegalArgumentException(
          format(
              "Unable to resolve credentials for Google account '%s'.",
              description.getAccountName()));
    }

    Compute compute = description.getCredentials().getCompute();
    String project = description.getCredentials().getProject();
    String region = description.getRegion();
    String forwardingRuleName = description.getLoadBalancerName();

    // First we look everything up. Then, we call delete on all of it. Finally, we wait (with
    // timeout) for all to complete.
    // Start with the forwarding rule.
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Retrieving forwarding rule " + forwardingRuleName + " in " + region + "...");

    List<ForwardingRule> projectForwardingRules = null;
    try {
      projectForwardingRules =
          timeExecute(
                  compute.forwardingRules().list(project, region),
                  "compute.forwardingRules.list",
                  TAG_SCOPE,
                  SCOPE_REGIONAL,
                  TAG_REGION,
                  region)
              .getItems();

      ForwardingRule forwardingRule =
          projectForwardingRules.stream()
              .filter(f -> f.getName().equals(forwardingRuleName))
              .findFirst()
              .orElse(null);
      if (forwardingRule == null) {
        GCEUtil.updateStatusAndThrowNotFoundException(
            "Forwarding rule " + forwardingRuleName + " not found in " + region + " for " + project,
            getTask(),
            BASE_PHASE);
      }

      String targetProxyName = GCEUtil.getLocalName(forwardingRule.getTarget());
      // Target HTTP(S) proxy.
      getTask().updateStatus(BASE_PHASE, "Retrieving target proxy " + targetProxyName + "...");

      GenericJson retrievedTargetProxy =
          (GenericJson)
              GCEUtil.getRegionTargetProxyFromRule(
                  compute, project, region, forwardingRule, BASE_PHASE, safeRetry, this);

      if (retrievedTargetProxy == null) {
        GCEUtil.updateStatusAndThrowNotFoundException(
            "Target proxy " + targetProxyName + " not found for " + project + " in " + region,
            getTask(),
            BASE_PHASE);
      }

      final String urlMapName = GCEUtil.getLocalName((String) retrievedTargetProxy.get("urlMap"));

      final List<String> listenersToDelete = new ArrayList<String>();
      for (ForwardingRule rule : projectForwardingRules) {
        if (!rule.getLoadBalancingScheme().equals("INTERNAL_MANAGED")) continue;

        try {
          GenericJson proxy =
              (GenericJson)
                  GCEUtil.getRegionTargetProxyFromRule(
                      compute,
                      project,
                      region,
                      rule,
                      BASE_PHASE,
                      getSafeRetry(),
                      DeleteGoogleInternalHttpLoadBalancerAtomicOperation.this);
          if (GCEUtil.getLocalName((proxy == null ? null : (String) proxy.get("urlMap")))
              .equals(urlMapName)) {
            listenersToDelete.add(rule.getName());
          }
        } catch (GoogleOperationException e) {
          // 404 is thrown if the target proxy does not exist.
          // We can ignore 404's here because we are iterating over all forwarding rules and some
          // other process may have
          // deleted the target proxy between the time we queried for the list of forwarding rules
          // and now.
          // Any other exception needs to be propagated.
          if (!(e.getCause() instanceof GoogleApiException.NotFoundException)) {
            throw e;
          }
        }
      }

      // URL map.
      getTask().updateStatus(BASE_PHASE, "Retrieving URL map " + urlMapName + "...");

      // NOTE: This call is necessary because we cross-check backend services later.
      UrlMapList mapList =
          timeExecute(
              compute.regionUrlMaps().list(project, region),
              "compute.regionUrlMaps.list",
              TAG_SCOPE,
              SCOPE_REGIONAL);
      List<UrlMap> projectUrlMaps = mapList.getItems();
      UrlMap urlMap =
          projectUrlMaps.stream()
              .filter(u -> u.getName().equals(urlMapName))
              .findFirst()
              .orElseThrow(
                  () -> new IllegalStateException(format("urlMap %s not found.", urlMapName)));
      projectUrlMaps.removeIf(u -> u.getName().equals(urlMapName));

      List<String> backendServiceUrls = new ArrayList<>();
      backendServiceUrls.add(urlMap.getDefaultService());
      addServicesFromPathMatchers(backendServiceUrls, urlMap.getPathMatchers());
      backendServiceUrls = ImmutableSet.copyOf(backendServiceUrls).asList();

      // Backend services. Also, get health check URLs.
      Set<String> healthCheckUrls = new HashSet<>();
      for (String backendServiceUrl : backendServiceUrls) {
        final String backendServiceName = GCEUtil.getLocalName(backendServiceUrl);
        getTask()
            .updateStatus(
                BASE_PHASE,
                "Retrieving backend service " + backendServiceName + " in " + region + "...");

        BackendService backendService =
            safeRetry.doRetry(
                new Closure<BackendService>(this, this) {
                  @Override
                  public BackendService call() {
                    try {
                      return timeExecute(
                          compute.regionBackendServices().get(project, region, backendServiceName),
                          "compute.regionBackendServices.get",
                          TAG_SCOPE,
                          SCOPE_REGIONAL);
                    } catch (IOException e) {
                      throw new UncheckedIOException(e);
                    }
                  }
                },
                "Region Backend service " + backendServiceName,
                getTask(),
                ImmutableList.of(400, 403, 412),
                new ArrayList<>(),
                ImmutableMap.of(
                    "action",
                    "get",
                    "phase",
                    BASE_PHASE,
                    "operation",
                    "compute.backendServices.get",
                    TAG_SCOPE,
                    SCOPE_REGIONAL,
                    TAG_REGION,
                    region),
                getRegistry());

        if (backendService == null) continue;

        if (backendService.getBackends() != null && backendService.getBackends().size() > 0) {
          getTask()
              .updateStatus(
                  BASE_PHASE,
                  "Server groups still associated with Internal Http(s) load balancer "
                      + description.getLoadBalancerName()
                      + ". Failing...");
          throw new IllegalStateException(
              "Server groups still associated with Internal Http(s) load balancer: "
                  + description.getLoadBalancerName()
                  + ".");
        }

        healthCheckUrls.addAll(backendService.getHealthChecks());
      }

      final Long timeoutSeconds = description.getDeleteOperationTimeoutSeconds();

      for (String ruleName : listenersToDelete) {
        getTask()
            .updateStatus(BASE_PHASE, "Deleting listener " + ruleName + " in " + region + "...");

        Operation operation =
            GCEUtil.deleteRegionalListener(
                compute,
                project,
                region,
                ruleName,
                BASE_PHASE,
                getSafeRetry(),
                DeleteGoogleInternalHttpLoadBalancerAtomicOperation.this);

        googleOperationPoller.waitForRegionalOperation(
            compute,
            project,
            region,
            operation.getName(),
            timeoutSeconds,
            getTask(),
            "listener " + ruleName,
            BASE_PHASE);
      }

      getTask()
          .updateStatus(BASE_PHASE, "Deleting URL map " + urlMapName + " in " + region + "...");
      Operation deleteUrlMapOperation =
          safeRetry.doRetry(
              new Closure<Operation>(this, this) {
                @Override
                public Operation call() {
                  try {
                    return timeExecute(
                        compute.regionUrlMaps().delete(project, region, urlMapName),
                        "compute.regionUrlMaps.delete",
                        TAG_SCOPE,
                        SCOPE_REGIONAL,
                        TAG_REGION,
                        region);
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                }
              },
              "Url map " + urlMapName,
              getTask(),
              ImmutableList.of(400, 403, 412),
              ImmutableList.of(404),
              ImmutableMap.of(
                  "action",
                  "delete",
                  "phase",
                  BASE_PHASE,
                  "operation",
                  "compute.regionUrlMaps.delete",
                  TAG_SCOPE,
                  SCOPE_REGIONAL,
                  TAG_REGION,
                  region),
              getRegistry());

      googleOperationPoller.waitForRegionalOperation(
          compute,
          project,
          region,
          deleteUrlMapOperation.getName(),
          timeoutSeconds,
          getTask(),
          "Regional url map " + urlMapName,
          BASE_PHASE);

      // We make a list of the delete operations for backend services.
      List<BackendServiceAsyncDeleteOperation> deleteBackendServiceAsyncOperations =
          new ArrayList<>();
      for (String backendServiceUrl : backendServiceUrls) {
        final String backendServiceName = GCEUtil.getLocalName(backendServiceUrl);
        Operation deleteBackendServiceOp =
            GCEUtil.deleteIfNotInUse(
                new Closure<Operation>(this, this) {
                  @Override
                  public Operation call() {
                    try {
                      return timeExecute(
                          compute
                              .regionBackendServices()
                              .delete(project, region, backendServiceName),
                          "compute.regionBackendServices.delete",
                          TAG_SCOPE,
                          SCOPE_REGIONAL,
                          TAG_REGION,
                          region);
                    } catch (IOException e) {
                      throw new UncheckedIOException(e);
                    }
                  }
                },
                "Backend service " + backendServiceName,
                project,
                getTask(),
                ImmutableMap.of(
                    "action",
                    "delete",
                    "operation",
                    "compute.regionBackendServices.delete",
                    "phase",
                    BASE_PHASE,
                    TAG_SCOPE,
                    SCOPE_REGIONAL,
                    TAG_REGION,
                    region),
                safeRetry,
                this);
        if (deleteBackendServiceOp != null) {
          deleteBackendServiceAsyncOperations.add(
              new BackendServiceAsyncDeleteOperation(
                  backendServiceName, deleteBackendServiceOp.getName()));
        }
      }

      // Wait on all of these deletes to complete.
      for (BackendServiceAsyncDeleteOperation asyncOperation :
          deleteBackendServiceAsyncOperations) {
        googleOperationPoller.waitForRegionalOperation(
            compute,
            project,
            region,
            asyncOperation.getOperationName(),
            timeoutSeconds,
            getTask(),
            "Region backend service " + asyncOperation.getBackendServiceName(),
            BASE_PHASE);
      }

      // Now make a list of the delete operations for health checks if description says to do so.
      if (description.getDeleteHealthChecks()) {
        List<HealthCheckAsyncDeleteOperation> deleteHealthCheckAsyncOperations = new ArrayList<>();
        for (String healthCheckUrl : healthCheckUrls) {
          final String healthCheckName = GCEUtil.getLocalName(healthCheckUrl);
          Operation deleteHealthCheckOp =
              GCEUtil.deleteIfNotInUse(
                  new Closure<Operation>(this, this) {
                    @Override
                    public Operation call() {
                      try {
                        return timeExecute(
                            compute.regionHealthChecks().delete(project, region, healthCheckName),
                            "compute.regionHealthChecks.delete",
                            TAG_SCOPE,
                            SCOPE_REGIONAL,
                            TAG_REGION,
                            region);
                      } catch (IOException e) {
                        throw new UncheckedIOException(e);
                      }
                    }
                  },
                  "Region Http health check " + healthCheckName,
                  project,
                  getTask(),
                  ImmutableMap.of(
                      "action",
                      "delete",
                      "operation",
                      "compute.regionHealthChecks.delete",
                      "phase",
                      BASE_PHASE,
                      TAG_SCOPE,
                      SCOPE_REGIONAL,
                      TAG_REGION,
                      region),
                  safeRetry,
                  this);
          if (deleteHealthCheckOp != null) {
            deleteHealthCheckAsyncOperations.add(
                new HealthCheckAsyncDeleteOperation(
                    healthCheckName, deleteHealthCheckOp.getName()));
          }
        }

        // Finally, wait on all of these deletes to complete.
        for (HealthCheckAsyncDeleteOperation asyncOperation : deleteHealthCheckAsyncOperations) {
          googleOperationPoller.waitForRegionalOperation(
              compute,
              project,
              region,
              asyncOperation.getOperationName(),
              timeoutSeconds,
              getTask(),
              "region health check " + asyncOperation.getHealthCheckName(),
              BASE_PHASE);
        }
      }

      getTask()
          .updateStatus(
              BASE_PHASE,
              "Done deleting internal http load balancer "
                  + description.getLoadBalancerName()
                  + " in "
                  + region
                  + ".");
      return null;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public SafeRetry getSafeRetry() {
    return safeRetry;
  }

  public void setSafeRetry(SafeRetry safeRetry) {
    this.safeRetry = safeRetry;
  }

  public static class HealthCheckAsyncDeleteOperation {
    public HealthCheckAsyncDeleteOperation(String healthCheckName, String operationName) {
      this.healthCheckName = healthCheckName;
      this.operationName = operationName;
    }

    public String getHealthCheckName() {
      return healthCheckName;
    }

    public String getOperationName() {
      return operationName;
    }

    private String healthCheckName;
    private String operationName;
  }

  public static class BackendServiceAsyncDeleteOperation {
    public BackendServiceAsyncDeleteOperation(String backendServiceName, String operationName) {
      this.backendServiceName = backendServiceName;
      this.operationName = operationName;
    }

    public String getBackendServiceName() {
      return backendServiceName;
    }

    public String getOperationName() {
      return operationName;
    }

    private String backendServiceName;
    private String operationName;
  }
}
