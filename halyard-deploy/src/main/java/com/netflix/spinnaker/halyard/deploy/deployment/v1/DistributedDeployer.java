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
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.ResolvedConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.*;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService.Orca;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService.Orca.ActiveExecutions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

@Component
public class DistributedDeployer {
  @Autowired
  OrcaRunner orcaRunner;

  @Value("${deploy.maxRemainingServerGroups:2}")
  private Integer MAX_REMAINING_SERVER_GROUPS;

  public <T extends Account> RemoteAction deploy(DeployableServiceProvider<T> serviceProvider,
      AccountDeploymentDetails<T> deploymentDetails,
      ResolvedConfiguration resolvedConfiguration) {
    Set<String> deployed = new HashSet<>();

    DaemonTaskHandler.newStage("Deploying Spinnaker");
    // First deploy all services not owned by Spinnaker
    for (DeployableService deployableService : serviceProvider.getPrioritizedDeployableServices()) {
      SpinnakerService service = deployableService.getService();
      ServiceSettings settings = resolvedConfiguration.getServiceSettings(service);
      if (!settings.isEnabled()) {
        continue;
      }

      boolean safeToUpdate = service.isSafeToUpdate();
      RunningServiceDetails runningServiceDetails = deployableService.getRunningServiceDetails(deploymentDetails);
      boolean notDeployed = runningServiceDetails.getInstances().isEmpty() && !runningServiceDetails.getLoadBalancer().isExists();

      if (deployableService.isRequiredToBootstrap() || !safeToUpdate || notDeployed) {
        DaemonTaskHandler.log("Manually deploying " + deployableService.getName());
        List<ConfigSource> configs = deployableService.stageProfiles(deploymentDetails, resolvedConfiguration);
        deployableService.ensureRunning(deploymentDetails, resolvedConfiguration, configs, safeToUpdate);
      } else {
        Orca orca = serviceProvider
            .getDeployableService(SpinnakerService.Type.ORCA_BOOTSTRAP, Orca.class)
            .connect(deploymentDetails, resolvedConfiguration);
        DaemonTaskHandler.log("Upgrading " + deployableService.getName() + " via Spinnaker red/black");
        deployService(deploymentDetails, resolvedConfiguration, orca, deployableService);
      }
    }

    reapOrcaServerGroups(deploymentDetails, resolvedConfiguration, serviceProvider.getDeployableService(SpinnakerService.Type.ORCA));

    RemoteAction result = new RemoteAction();

    String deckConnection = serviceProvider
        .getDeployableService(SpinnakerService.Type.DECK)
        .connectCommand(deploymentDetails, resolvedConfiguration);
    String gateConnection = serviceProvider
        .getDeployableService(SpinnakerService.Type.GATE)
        .connectCommand(deploymentDetails, resolvedConfiguration);
    result.setScript("#!/bin/bash\n" + deckConnection + "&\n" + gateConnection);
    result.setAutoRun(false);
    return result;
  }

  private <T extends Account> void deployService(AccountDeploymentDetails<T> details,
      ResolvedConfiguration resolvedConfiguration,
      Orca orca,
      DeployableService deployableService) {
    DaemonTaskHandler.newStage("Deploying " + deployableService.getName());
    RunningServiceDetails runningServiceDetails = deployableService.getRunningServiceDetails(details);
    Supplier<String> idSupplier;
    if (!runningServiceDetails.getLoadBalancer().isExists()) {
      Map<String, Object> task = deployableService.buildUpsertLoadBalancerTask(details, resolvedConfiguration);
      idSupplier = () -> orca.submitTask(task).get("ref");
      orcaRunner.monitorTask(idSupplier, orca);
    }

    List<String> configs = deployableService.stageProfiles(details, resolvedConfiguration);
    Map<String, Object> pipeline = deployableService.buildDeployServerGroupPipeline(details, resolvedConfiguration, configs);
    idSupplier = () -> orca.orchestrate(pipeline).get("ref");
    orcaRunner.monitorPipeline(idSupplier, orca);
  }

  private <T extends Account> void reapOrcaServerGroups(AccountDeploymentDetails<T> details,
      ResolvedConfiguration resolvedConfiguration,
      DeployableService<Orca, T> orcaService) {
    Orca orca = orcaService.connect(details, resolvedConfiguration);
    Map<String, ActiveExecutions> executions = orca.getActiveExecutions();
    RunningServiceDetails orcaDetails = orcaService.getRunningServiceDetails(details);

    Map<String, Integer> executionsByInstance = new HashMap<>();

    executions.forEach((s, e) -> {
      String instanceId = s.split("@")[1];
      executionsByInstance.put(instanceId, e.getCount());
    });

    Map<String, Integer> executionsByServerGroup = new HashMap<>();

    orcaDetails.getInstances().forEach((s, is) -> {
      int count = is.stream().reduce(0,
          (c, i) -> c + executionsByInstance.getOrDefault(i.getId(), 0),
          (a, b) -> a + b);
      executionsByServerGroup.put(s, count);
    });

    // Omit the last deployed orcas from being deleted, since they are kept around for rollbacks.
    List<String> allOrcas = new ArrayList<>(executionsByServerGroup.keySet());
    allOrcas.sort(String::compareTo);

    int orcaCount = allOrcas.size();
    if (orcaCount <= MAX_REMAINING_SERVER_GROUPS) {
      return;
    }

    allOrcas = allOrcas.subList(0, orcaCount - MAX_REMAINING_SERVER_GROUPS);
    for (String orcaVersion : allOrcas) {
      // TODO(lwander) consult clouddriver to ensure this orca isn't enabled
      if (executionsByServerGroup.get(orcaVersion) == 0) {
        DaemonTaskHandler.log("Reaping old orca instance " + orcaVersion);
        orcaService.deleteVersion(details, orcaVersion);
      }
    }
  }
}
