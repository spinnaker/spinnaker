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

import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.LocalService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.LocalServiceProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LocalDeployer implements Deployer<LocalServiceProvider, DeploymentDetails> {
  @Override
  public RemoteAction deploy(
      LocalServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      List<String> serviceNames) {
    List<LocalService> enabledServices = serviceProvider.getInstallableServices(serviceNames)
        .stream()
        .filter(i -> resolvedConfiguration.getServiceSettings(i.getService()).isEnabled())
        .collect(Collectors.toList());

    Map<String, String> installCommands = enabledServices.stream().reduce(new HashMap<>(), (commands, installable) -> {
      String command = String.join("\n",
          installable.installArtifactCommand(deploymentDetails),
          installable.stageProfilesCommand(resolvedConfiguration));
      commands.put(installable.getService().getCanonicalName(), command);
      return commands;
    }, (m1, m2) -> {
      m1.putAll(m2);
      return m1;
    });

    String installCommand = serviceProvider.getInstallCommand(resolvedConfiguration, installCommands);
    RemoteAction result = new RemoteAction();
    result.setAutoRun(true);
    result.setScript(installCommand);
    return result;
  }

  @Override
  public void rollback(LocalServiceProvider serviceProvider, DeploymentDetails deploymentDetails, SpinnakerRuntimeSettings runtimeSettings, List<String> serviceNames) {
    throw new HalException(Problem.Severity.FATAL, "No support for rolling back debian deployments yet.");
  }
}
