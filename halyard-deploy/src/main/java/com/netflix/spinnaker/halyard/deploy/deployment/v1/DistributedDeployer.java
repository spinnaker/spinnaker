/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.ResolvedConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.LogCollector;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService.Orca;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.RedisService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.RoscoService.Rosco;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedServiceProvider;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import retrofit.RetrofitError;
import retrofit.client.Response;

@Component
@Slf4j
public class DistributedDeployer<T extends Account>
    implements Deployer<DistributedServiceProvider<T>, AccountDeploymentDetails<T>> {

  @Autowired OrcaRunner orcaRunner;

  @Value("${deploy.max-remaining-server-groups:2}")
  private Integer MAX_REMAINING_SERVER_GROUPS;

  @Override
  public void rollback(
      DistributedServiceProvider<T> serviceProvider,
      AccountDeploymentDetails<T> deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    DaemonTaskHandler.newStage("Checking if it is safe to roll back all services");
    for (DistributedService distributedService :
        serviceProvider.getPrioritizedDistributedServices(serviceTypes)) {
      SpinnakerService service = distributedService.getService();
      ServiceSettings settings = runtimeSettings.getServiceSettings(service);
      boolean safeToUpdate = settings.getSafeToUpdate();

      if (!settings.getEnabled()
          || distributedService.isRequiredToBootstrap()
          || !safeToUpdate
          || settings.getSkipLifeCycleManagement()) {
        continue;
      }

      RunningServiceDetails runningServiceDetails =
          distributedService.getRunningServiceDetails(deploymentDetails, runtimeSettings);
      if (runningServiceDetails.getInstances().keySet().size() == 1) {
        throw new HalException(
            Problem.Severity.FATAL,
            "Service "
                + service.getCanonicalName()
                + " has only one server group - there is nothing to rollback to.");
      }
    }

    DaemonTaskHandler.newStage("Rolling back all updatable services");
    for (DistributedService distributedService :
        serviceProvider.getPrioritizedDistributedServices(serviceTypes)) {
      SpinnakerService service = distributedService.getService();
      ServiceSettings settings = runtimeSettings.getServiceSettings(service);
      if (!settings.getEnabled() || settings.getSkipLifeCycleManagement()) {
        continue;
      }

      boolean safeToUpdate = settings.getSafeToUpdate();
      if (distributedService.isRequiredToBootstrap() || !safeToUpdate) {
        // Do nothing, the bootstrapping services should already be running, and the services that
        // can't be updated
        // having nothing to rollback to
      } else {
        DaemonResponse.StaticRequestBuilder<Void> builder =
            new DaemonResponse.StaticRequestBuilder<>(
                () -> {
                  Orca orca =
                      serviceProvider
                          .getDeployableService(SpinnakerService.Type.ORCA_BOOTSTRAP, Orca.class)
                          .connectToPrimaryService(deploymentDetails, runtimeSettings);
                  DaemonTaskHandler.message(
                      "Rolling back "
                          + distributedService.getServiceName()
                          + " via Spinnaker red/black");
                  rollbackService(deploymentDetails, orca, distributedService, runtimeSettings);

                  return null;
                });

        DaemonTaskHandler.submitTask(
            builder::build, "Rollback " + distributedService.getServiceName());
      }
    }

    DaemonTaskHandler.message("Waiting on rollbacks to complete");
    DaemonTaskHandler.reduceChildren(null, (t1, t2) -> null, (t1, t2) -> null)
        .getProblemSet()
        .throwifSeverityExceeds(Problem.Severity.WARNING);
  }

  @Override
  public void collectLogs(
      DistributedServiceProvider<T> serviceProvider,
      AccountDeploymentDetails<T> deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    for (DistributedService distributedService :
        serviceProvider.getPrioritizedDistributedServices(serviceTypes)) {
      if (distributedService instanceof LogCollector) {
        ((LogCollector) distributedService).collectLogs(deploymentDetails, runtimeSettings);
      } else {
        log.warn(distributedService.getServiceName() + " cannot have logs collected");
      }
    }
  }

  @Override
  public RemoteAction connectCommand(
      DistributedServiceProvider<T> serviceProvider,
      AccountDeploymentDetails<T> deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    RemoteAction result = new RemoteAction();

    String connectCommands =
        String.join(
            " &\n",
            serviceTypes.stream()
                .map(
                    t ->
                        serviceProvider
                            .getDeployableService(t)
                            .connectCommand(deploymentDetails, runtimeSettings))
                .collect(Collectors.toList()));
    result.setScript("#!/bin/bash\n" + connectCommands);
    result.setScriptDescription(
        "The generated script will open connections to the API & UI servers using ssh tunnels");
    result.setAutoRun(false);
    return result;
  }

  @Override
  public void flushInfrastructureCaches(
      DistributedServiceProvider<T> serviceProvider,
      AccountDeploymentDetails<T> deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings) {
    try {
      Jedis jedis =
          (Jedis)
              serviceProvider
                  .getDeployableService(SpinnakerService.Type.REDIS)
                  .connectToPrimaryService(deploymentDetails, runtimeSettings);
      RedisService.flushKeySpace(jedis, "com.netflix.spinnaker.clouddriver*");
    } catch (Exception e) {
      throw new HalException(
          Problem.Severity.FATAL, "Failed to flush redis cache: " + e.getMessage());
    }
  }

  @Override
  public RemoteAction deploy(
      DistributedServiceProvider<T> serviceProvider,
      AccountDeploymentDetails<T> deploymentDetails,
      ResolvedConfiguration resolvedConfiguration,
      List<SpinnakerService.Type> serviceTypes,
      boolean waitForCompletion,
      Optional<Integer> waitForCompletionTimeoutMinutes) {
    SpinnakerRuntimeSettings runtimeSettings = resolvedConfiguration.getRuntimeSettings();

    DaemonTaskHandler.newStage("Deploying Spinnaker");
    // First deploy all services not owned by Spinnaker
    for (DistributedService distributedService :
        serviceProvider.getPrioritizedDistributedServices(serviceTypes)) {
      SpinnakerService service = distributedService.getService();
      ServiceSettings settings = resolvedConfiguration.getServiceSettings(service);
      if (settings == null || !settings.getEnabled() || settings.getSkipLifeCycleManagement()) {
        continue;
      }

      DaemonTaskHandler.newStage("Determining status of " + distributedService.getServiceName());
      boolean safeToUpdate = settings.getSafeToUpdate();
      RunningServiceDetails runningServiceDetails =
          distributedService.getRunningServiceDetails(deploymentDetails, runtimeSettings);

      if (distributedService.isRequiredToBootstrap() || !safeToUpdate) {
        deployServiceManually(
            deploymentDetails, resolvedConfiguration, distributedService, safeToUpdate);
      } else {
        DaemonResponse.StaticRequestBuilder<Void> builder =
            new DaemonResponse.StaticRequestBuilder<>(
                () -> {
                  if (runningServiceDetails.getLatestEnabledVersion() == null) {
                    DaemonTaskHandler.newStage(
                        "Deploying " + distributedService.getServiceName() + " via provider API");
                    deployServiceManually(
                        deploymentDetails, resolvedConfiguration, distributedService, safeToUpdate);
                  } else {
                    DaemonTaskHandler.newStage(
                        "Deploying " + distributedService.getServiceName() + " via red/black");
                    try {
                      Orca orca =
                          serviceProvider
                              .getDeployableService(
                                  SpinnakerService.Type.ORCA_BOOTSTRAP, Orca.class)
                              .connectToPrimaryService(deploymentDetails, runtimeSettings);
                      deployServiceWithOrca(
                          deploymentDetails, resolvedConfiguration, orca, distributedService);
                    } catch (RetrofitError e) {
                      String message =
                          ((Map<String, String>) e.getBodyAs(Map.class)).get("message");
                      throw new HalException(
                          Problem.Severity.FATAL,
                          "Unable to deploy service with Orca " + e + ": " + message,
                          e);
                    }
                  }

                  return null;
                });
        DaemonTaskHandler.submitTask(
            builder::build, "Deploy " + distributedService.getServiceName());
      }
    }

    DaemonTaskHandler.message("Waiting on deployments to complete");
    DaemonTaskHandler.reduceChildren(null, (t1, t2) -> null, (t1, t2) -> null)
        .getProblemSet()
        .throwifSeverityExceeds(Problem.Severity.WARNING);

    DistributedService<Orca, T> orca =
        serviceProvider.getDeployableService(SpinnakerService.Type.ORCA);
    Set<Integer> unknownVersions = reapOrcaServerGroups(deploymentDetails, runtimeSettings, orca);
    reapRoscoServerGroups(
        deploymentDetails,
        runtimeSettings,
        serviceProvider.getDeployableService(SpinnakerService.Type.ROSCO));

    if (!unknownVersions.isEmpty()) {
      String versions =
          String.join(
              ", ",
              unknownVersions.stream().map(orca::getVersionedName).collect(Collectors.toList()));
      throw new HalException(
          new ProblemBuilder(
                  Problem.Severity.ERROR,
                  "The following orca versions ("
                      + versions
                      + ") could not safely be drained of work.")
              .setRemediation(
                  "Please make sure that no pipelines are running, and manually destroy the server groups at those versions.")
              .build());
    }

    return new RemoteAction();
  }

  private <T extends Account> void deployServiceManually(
      AccountDeploymentDetails<T> details,
      ResolvedConfiguration resolvedConfiguration,
      DistributedService distributedService,
      boolean safeToUpdate) {
    DaemonTaskHandler.message("Manually deploying " + distributedService.getServiceName());
    List<ConfigSource> configs = distributedService.stageProfiles(details, resolvedConfiguration);
    distributedService.ensureRunning(details, resolvedConfiguration, configs, safeToUpdate);
  }

  private <T extends Account> void deployServiceWithOrca(
      AccountDeploymentDetails<T> details,
      ResolvedConfiguration resolvedConfiguration,
      Orca orca,
      DistributedService distributedService) {
    SpinnakerRuntimeSettings runtimeSettings = resolvedConfiguration.getRuntimeSettings();
    RunningServiceDetails runningServiceDetails =
        distributedService.getRunningServiceDetails(details, runtimeSettings);
    Supplier<String> idSupplier;
    if (!runningServiceDetails.getLoadBalancer().isExists()) {
      Map<String, Object> task =
          distributedService.buildUpsertLoadBalancerTask(details, runtimeSettings);
      idSupplier = () -> (String) orca.submitTask(task).get("ref");
      orcaRunner.monitorTask(idSupplier, orca);
    }

    List<String> configs = distributedService.stageProfiles(details, resolvedConfiguration);
    Integer maxRemaining = MAX_REMAINING_SERVER_GROUPS;
    boolean scaleDown = true;
    if (distributedService.isStateful()) {
      maxRemaining = null;
      scaleDown = false;
    }

    Map<String, Object> pipeline =
        distributedService.buildDeployServerGroupPipeline(
            details, runtimeSettings, configs, maxRemaining, scaleDown);
    idSupplier = () -> (String) orca.orchestrate(pipeline).get("ref");
    orcaRunner.monitorPipeline(idSupplier, orca);
  }

  private <T extends Account> void rollbackService(
      AccountDeploymentDetails<T> details,
      Orca orca,
      DistributedService distributedService,
      SpinnakerRuntimeSettings runtimeSettings) {
    DaemonTaskHandler.newStage("Rolling back " + distributedService.getServiceName());
    Map<String, Object> pipeline =
        distributedService.buildRollbackPipeline(details, runtimeSettings);
    Supplier<String> idSupplier = () -> (String) orca.orchestrate(pipeline).get("ref");
    orcaRunner.monitorPipeline(idSupplier, orca);
  }

  private <T extends Account> void reapRoscoServerGroups(
      AccountDeploymentDetails<T> details,
      SpinnakerRuntimeSettings runtimeSettings,
      DistributedService<Rosco, T> roscoService) {
    if (runtimeSettings
        .getServiceSettings(roscoService.getService())
        .getSkipLifeCycleManagement()) {
      return;
    }

    ServiceSettings roscoSettings = runtimeSettings.getServiceSettings(roscoService.getService());
    Rosco.AllStatus allStatus;

    try {
      Rosco rosco = roscoService.connectToPrimaryService(details, runtimeSettings);
      allStatus = rosco.getAllStatus();
    } catch (RetrofitError e) {
      boolean enabled = roscoSettings.getEnabled() != null && roscoSettings.getEnabled();
      if (enabled) {
        Map<String, String> body = (Map<String, String>) e.getBodyAs(Map.class);
        String message;
        if (body != null) {
          message = body.getOrDefault("message", "no message supplied");
        } else {
          message = "no response body";
        }
        throw new HalException(
            Problem.Severity.FATAL,
            "Rosco is enabled, and no connection to rosco could be established: "
                + e
                + ": "
                + message,
            e);
      }

      Response response = e.getResponse();
      if (response == null) {
        throw new IllegalStateException("Unknown connection failure: " + e, e);
      }

      // 404 when the service couldn't be found, 503 when k8s couldn't establish a connection
      if (response.getStatus() == 404 || response.getStatus() == 503) {
        log.info("Rosco is not enabled, and there are no server groups to reap");
        return;
      } else {
        throw new HalException(
            Problem.Severity.FATAL,
            "Rosco is not enabled, but couldn't be connected to for unknown reason: " + e,
            e);
      }
    }
    RunningServiceDetails roscoDetails =
        roscoService.getRunningServiceDetails(details, runtimeSettings);

    Set<String> activeInstances = new HashSet<>();

    allStatus
        .getInstances()
        .forEach(
            (s, e) -> {
              if (e.getStatus().equals(Rosco.Status.RUNNING)) {
                String[] split = s.split("@");
                if (split.length != 2) {
                  log.warn("Unsupported rosco status format");
                  return;
                }

                String instanceId = split[1];
                activeInstances.add(instanceId);
              }
            });

    Map<Integer, Integer> executionsByServerGroupVersion = new HashMap<>();

    roscoDetails
        .getInstances()
        .forEach(
            (s, is) -> {
              int count =
                  is.stream()
                      .reduce(
                          0, (c, i) -> c + (activeInstances.contains(i) ? 1 : 0), (a, b) -> a + b);
              executionsByServerGroupVersion.put(s, count);
            });

    // Omit the last deployed roscos from being deleted, since they are kept around for rollbacks.
    List<Integer> allRoscos = new ArrayList<>(executionsByServerGroupVersion.keySet());
    allRoscos.sort(Integer::compareTo);

    cleanupServerGroups(
        details, roscoService, roscoSettings, executionsByServerGroupVersion, allRoscos);
  }

  private <S, T extends Account> void cleanupServerGroups(
      AccountDeploymentDetails<T> details,
      DistributedService<S, T> service,
      ServiceSettings settings,
      Map<Integer, Integer> workloadByVersion,
      List<Integer> runningVersions) {
    int runningVersionCount = runningVersions.size();

    if (runningVersionCount <= 1) {
      log.info("There are no extra services to cleanup. Running count = " + runningVersionCount);
      return;
    }

    /*
     * To visualize the array slicing below, say we have 5 running
     * server groups, 3 of which we can destroy, one we can scale down,
     * and one that's actively serving traffic, as illustrated here:
     *
     *
     *                 |  running - MAX_REMAINING |  MAX_REMAINING  |
     *                 |      No longer in use    | backup | active |
     *  server groups: |  v001  |  v002  |  v003  |  v004  |  v005  |
     */
    List<Integer> killableVersions =
        runningVersions.subList(0, runningVersionCount - MAX_REMAINING_SERVER_GROUPS);
    List<Integer> shrinkableVersions =
        runningVersions.subList(
            runningVersionCount - MAX_REMAINING_SERVER_GROUPS, runningVersionCount - 1);

    for (Integer version : killableVersions) {
      if (workloadByVersion.get(version) == 0) {
        DaemonTaskHandler.message("Reaping old server group sequence " + version);
        service.deleteVersion(details, settings, version);
      }
    }

    for (Integer version : shrinkableVersions) {
      if (workloadByVersion.get(version) == 0) {
        DaemonTaskHandler.message("Shrinking old server group sequence " + version);
        service.resizeVersion(details, settings, version, 0);
      }
    }
  }

  private <T extends Account> Set<Integer> disableOrcaServerGroups(
      AccountDeploymentDetails<T> details,
      SpinnakerRuntimeSettings runtimeSettings,
      DistributedService<Orca, T> orcaService,
      RunningServiceDetails runningOrcaDetails) {
    Map<Integer, List<RunningServiceDetails.Instance>> instances =
        runningOrcaDetails.getInstances();
    List<Integer> existingVersions = new ArrayList<>(instances.keySet());
    existingVersions.sort(Integer::compareTo);

    Map<String, String> disableRequest = new HashMap<>();
    Set<Integer> result = new HashSet<>();
    disableRequest.put("enabled", "false");
    List<Integer> disabledVersions = existingVersions.subList(0, existingVersions.size() - 1);
    for (Integer version : disabledVersions) {
      try {
        for (RunningServiceDetails.Instance instance : instances.get(version)) {
          log.info("Disabling instance " + instance.getId());
          Orca orca =
              orcaService.connectToInstance(
                  details, runtimeSettings, orcaService.getService(), instance.getId());
          orca.setInstanceStatusEnabled(disableRequest);
        }
        result.add(version);
      } catch (RetrofitError e) {
        Response response = e.getResponse();
        if (response == null) {
          log.warn("Unexpected error disabling orca", e);
        } else if (response.getStatus() == 400
            && ((Map) e.getBodyAs(Map.class)).containsKey("discovery")) {
          log.info("Orca instance is managed by eureka");
          result.add(version);
        } else {
          log.warn("Orca version doesn't support explicit disabling of instances", e);
        }
      }
    }

    Set<Integer> unknownVersions =
        disabledVersions.stream().filter(i -> !result.contains(i)).collect(Collectors.toSet());
    if (unknownVersions.size() > 0) {
      log.warn(
          "There are existing orca server groups that cannot be explicitly disabled, we will have to wait for these to drain work");
    }

    return unknownVersions;
  }

  private <T extends Account> Set<Integer> reapOrcaServerGroups(
      AccountDeploymentDetails<T> details,
      SpinnakerRuntimeSettings runtimeSettings,
      DistributedService<Orca, T> orcaService) {
    if (runtimeSettings.getServiceSettings(orcaService.getService()).getSkipLifeCycleManagement()) {
      return Collections.emptySet();
    }

    RunningServiceDetails runningOrcaDetails =
        orcaService.getRunningServiceDetails(details, runtimeSettings);
    Map<Integer, List<RunningServiceDetails.Instance>> instances =
        runningOrcaDetails.getInstances();
    List<Integer> versions = new ArrayList<>(instances.keySet());
    versions.sort(Integer::compareTo);

    Set<Integer> unknownVersions =
        disableOrcaServerGroups(details, runtimeSettings, orcaService, runningOrcaDetails);
    Map<Integer, Integer> executionsByServerGroupVersion = new HashMap<>();

    for (Integer version : versions) {
      if (unknownVersions.contains(version)) {
        executionsByServerGroupVersion.put(
            version, 1); // we make the assumption that there is non-0 work for the unknown versions
      } else {
        executionsByServerGroupVersion.put(version, 0);
      }
    }

    ServiceSettings orcaSettings = runtimeSettings.getServiceSettings(orcaService.getService());
    cleanupServerGroups(
        details, orcaService, orcaSettings, executionsByServerGroupVersion, versions);

    return unknownVersions;
  }
}
