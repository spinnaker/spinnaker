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
 *
 */

package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake.BakeService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.bake.BakeServiceProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class BakeDeployer implements Deployer<BakeServiceProvider, DeploymentDetails> {
  @Override
  public RemoteAction deploy(
      BakeServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      List<SpinnakerService.Type> serviceTypes,
      boolean waitForCompletion,
      Optional<Integer> waitForCompletionTimeoutMinutes) {
    List<BakeService> enabledServices =
        serviceProvider.getPrioritizedBakeableServices(serviceTypes).stream()
            .filter(
                i -> {
                  ServiceSettings serviceSettings =
                      resolvedConfiguration.getServiceSettings(i.getService());
                  return serviceSettings != null && serviceSettings.getEnabled();
                })
            .collect(Collectors.toList());

    Map<String, String> installCommands =
        enabledServices.stream()
            .reduce(
                new HashMap<>(),
                (commands, installable) -> {
                  String command =
                      String.join(
                          "\n",
                          installable.installArtifactCommand(deploymentDetails),
                          installable.stageStartupScripts(
                              deploymentDetails, resolvedConfiguration));
                  commands.put(installable.getService().getCanonicalName(), command);
                  return commands;
                },
                (m1, m2) -> {
                  m1.putAll(m2);
                  return m1;
                });

    String startupCommand =
        String.join(
            "\n",
            enabledServices.stream()
                .map(BakeService::getStartupCommand)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));

    String installCommand =
        serviceProvider.getInstallCommand(
            deploymentDetails, resolvedConfiguration, installCommands, startupCommand);
    RemoteAction result = new RemoteAction();
    result.setAutoRun(true);
    result.setScript(installCommand);
    return result;
  }

  @Override
  public void rollback(
      BakeServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    throw new HalException(
        Problem.Severity.FATAL, "This type of deployment cannot be rolled back.");
  }

  @Override
  public void collectLogs(
      BakeServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    throw new HalException(
        Problem.Severity.FATAL,
        "This type of deployment does not generate logs that can be collected.");
  }

  @Override
  public RemoteAction connectCommand(
      BakeServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    throw new HalException(
        Problem.Severity.FATAL, "This type of deployment cannot be run or connected to.");
  }

  @Override
  public void flushInfrastructureCaches(
      BakeServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings) {
    throw new HalException(
        Problem.Severity.FATAL, "This type of deployment does not have an active redis to flush.");
  }
}
