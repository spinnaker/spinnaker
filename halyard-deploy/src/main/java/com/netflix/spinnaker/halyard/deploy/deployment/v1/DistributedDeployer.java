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
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.ResolvedConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService.Orca;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService.Orca.ActiveExecutions;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedServiceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class DistributedDeployer<T extends Account> implements Deployer<DistributedServiceProvider<T>, AccountDeploymentDetails<T>> {
  @Autowired
  OrcaRunner orcaRunner;

  @Value("${deploy.maxRemainingServerGroups:2}")
  private Integer MAX_REMAINING_SERVER_GROUPS;

  @Override
  public void rollback(DistributedServiceProvider<T> serviceProvider,
      AccountDeploymentDetails<T> deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<String> serviceNames) {
    DaemonTaskHandler.newStage("Rolling back all updatable services");
    for (DistributedService distributedService : serviceProvider.getPrioritizedDistributedServices(serviceNames)) {
      SpinnakerService service = distributedService.getService();
      ServiceSettings settings = runtimeSettings.getServiceSettings(service);
      if (!settings.isEnabled()) {
        continue;
      }

      boolean safeToUpdate = settings.isSafeToUpdate();
      if (distributedService.isRequiredToBootstrap() || !safeToUpdate) {
        // Do nothing, the bootstrapping services should already be running, and the services that can't be updated
        // having nothing to rollback to
      } else {
        Orca orca = serviceProvider
            .getDeployableService(SpinnakerService.Type.ORCA_BOOTSTRAP, Orca.class)
            .connect(deploymentDetails, runtimeSettings);
        DaemonTaskHandler.message("Rolling back " + distributedService.getServiceName() + " via Spinnaker red/black");
        rollbackService(deploymentDetails, orca, distributedService, runtimeSettings.getServiceSettings(service));
      }
    }
  }

  @Override
  public RemoteAction clean(DistributedServiceProvider<T> serviceProvider, AccountDeploymentDetails<T> deploymentDetails, SpinnakerRuntimeSettings runtimeSettings) {
    return null;
  }

  @Override
  public RemoteAction deploy(DistributedServiceProvider<T> serviceProvider,
      AccountDeploymentDetails<T> deploymentDetails,
      ResolvedConfiguration resolvedConfiguration,
      List<String> serviceNames) {
    SpinnakerRuntimeSettings runtimeSettings = resolvedConfiguration.getRuntimeSettings();

    DaemonTaskHandler.newStage("Deploying Spinnaker");
    // First deploy all services not owned by Spinnaker
    for (DistributedService distributedService : serviceProvider.getPrioritizedDistributedServices(serviceNames)) {
      SpinnakerService service = distributedService.getService();
      ServiceSettings settings = resolvedConfiguration.getServiceSettings(service);
      if (!settings.isEnabled()) {
        continue;
      }

      boolean safeToUpdate = settings.isSafeToUpdate();

      if (distributedService.isRequiredToBootstrap() || !safeToUpdate) {
        DaemonTaskHandler.message("Manually deploying " + distributedService.getServiceName());
        List<ConfigSource> configs = distributedService.stageProfiles(deploymentDetails, resolvedConfiguration);
        distributedService.ensureRunning(deploymentDetails, resolvedConfiguration, configs, safeToUpdate);
      } else {
        DaemonResponse.StaticRequestBuilder<Void> builder = new DaemonResponse.StaticRequestBuilder<>();
        builder.setBuildResponse(() -> {
          Orca orca = serviceProvider
              .getDeployableService(SpinnakerService.Type.ORCA_BOOTSTRAP, Orca.class)
              .connect(deploymentDetails, runtimeSettings);
          DaemonTaskHandler.newStage("Deploying " + distributedService.getServiceName() + " via red/black");
          deployService(deploymentDetails, resolvedConfiguration, orca, distributedService);

          return null;
        });
        DaemonTaskHandler.submitTask(builder::build, "Deploy " + distributedService.getServiceName());
      }
    }

    DaemonTaskHandler.message("Waiting on red/black pipelines to complete");
    DaemonTaskHandler.reduceChildren(null, (t1, t2) -> null, (t1, t2) -> null)
        .getProblemSet().throwifSeverityExceeds(Problem.Severity.WARNING);

    DaemonTaskHandler.message("Flushing redis cache");
    try {
      Jedis jedis = (Jedis) serviceProvider
          .getDeployableService(SpinnakerService.Type.REDIS)
          .connect(deploymentDetails, runtimeSettings);
      jedis.del("*clouddriver*");
    } catch (Exception e) {
      throw new HalException(Problem.Severity.FATAL, "Failed to flush redis cache: " + e.getMessage());
    }

    reapOrcaServerGroups(deploymentDetails, runtimeSettings, serviceProvider.getDeployableService(SpinnakerService.Type.ORCA));

    RemoteAction result = new RemoteAction();

    String deckConnection = serviceProvider
        .getDeployableService(SpinnakerService.Type.DECK)
        .connectCommand(deploymentDetails, runtimeSettings);
    String gateConnection = serviceProvider
        .getDeployableService(SpinnakerService.Type.GATE)
        .connectCommand(deploymentDetails, runtimeSettings);
    result.setScript("#!/bin/bash\n" + deckConnection + "&\n" + gateConnection);
    result.setScriptDescription("The generated script will open connections to the API & UI servers using kubectl");
    result.setAutoRun(false);
    return result;
  }

  private <T extends Account> void deployService(AccountDeploymentDetails<T> details,
      ResolvedConfiguration resolvedConfiguration,
      Orca orca,
      DistributedService distributedService) {
    SpinnakerRuntimeSettings runtimeSettings = resolvedConfiguration.getRuntimeSettings();
    ServiceSettings settings = resolvedConfiguration.getServiceSettings(distributedService.getService());
    RunningServiceDetails runningServiceDetails = distributedService.getRunningServiceDetails(details, settings);
    Supplier<String> idSupplier;
    if (!runningServiceDetails.getLoadBalancer().isExists()) {
      Map<String, Object> task = distributedService.buildUpsertLoadBalancerTask(details, runtimeSettings);
      idSupplier = () -> orca.submitTask(task).get("ref");
      orcaRunner.monitorTask(idSupplier, orca);
    }

    List<String> configs = distributedService.stageProfiles(details, resolvedConfiguration);
    Integer maxRemaining = MAX_REMAINING_SERVER_GROUPS;
    boolean scaleDown = true;
    if (distributedService.getService().getArtifact() == SpinnakerArtifact.ORCA) {
      maxRemaining = null;
      scaleDown = false;
    }

    Map<String, Object> pipeline = distributedService.buildDeployServerGroupPipeline(details, runtimeSettings, configs, maxRemaining, scaleDown);
    idSupplier = () -> orca.orchestrate(pipeline).get("ref");
    orcaRunner.monitorPipeline(idSupplier, orca);
  }

  private <T extends Account> void rollbackService(AccountDeploymentDetails<T> details,
      Orca orca,
      DistributedService distributedService,
      ServiceSettings settings) {
    DaemonTaskHandler.newStage("Rolling back " + distributedService.getServiceName());
    Map<String, Object> pipeline = distributedService.buildRollbackPipeline(details, settings);
    Supplier<String> idSupplier = () -> orca.orchestrate(pipeline).get("ref");
    orcaRunner.monitorPipeline(idSupplier, orca);
  }

  private <T extends Account> void reapOrcaServerGroups(AccountDeploymentDetails<T> details,
      SpinnakerRuntimeSettings runtimeSettings,
      DistributedService<Orca, T> orcaService) {
    Orca orca = orcaService.connect(details, runtimeSettings);
    Map<String, ActiveExecutions> executions = orca.getActiveExecutions();
    ServiceSettings orcaSettings = runtimeSettings.getServiceSettings(orcaService.getService());
    RunningServiceDetails orcaDetails = orcaService.getRunningServiceDetails(details, orcaSettings);

    Map<String, Integer> executionsByInstance = new HashMap<>();

    executions.forEach((s, e) -> {
      String instanceId = s.split("@")[1];
      executionsByInstance.put(instanceId, e.getCount());
    });

    Map<Integer, Integer> executionsByServerGroupVersion = new HashMap<>();

    orcaDetails.getInstances().forEach((s, is) -> {
      int count = is.stream().reduce(0,
          (c, i) -> c + executionsByInstance.getOrDefault(i.getId(), 0),
          (a, b) -> a + b);
      executionsByServerGroupVersion.put(s, count);
    });

    // Omit the last deployed orcas from being deleted, since they are kept around for rollbacks.
    List<Integer> allOrcas = new ArrayList<>(executionsByServerGroupVersion.keySet());
    allOrcas.sort(Integer::compareTo);
    System.out.println("orca count = " + allOrcas.size());

    int orcaCount = allOrcas.size();
    if (orcaCount <= MAX_REMAINING_SERVER_GROUPS) {
      return;
    }

    allOrcas = allOrcas.subList(0, orcaCount - MAX_REMAINING_SERVER_GROUPS);
    for (Integer orcaVersion : allOrcas) {
      System.out.println("orca version " + orcaVersion);
      System.out.println("ebsgv " + executionsByServerGroupVersion.get(orcaVersion));
      // TODO(lwander) consult clouddriver to ensure this orca isn't enabled
      if (executionsByServerGroupVersion.get(orcaVersion) == 0) {
        DaemonTaskHandler.message("Reaping old orca instance " + orcaVersion);
        orcaService.deleteVersion(details, orcaSettings, orcaVersion);
      }
    }
  }
}
