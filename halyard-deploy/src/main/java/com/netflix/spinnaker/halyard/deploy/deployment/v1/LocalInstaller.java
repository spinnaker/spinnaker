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
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.InstallableService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.InstallableServiceProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class LocalInstaller {
  public RemoteAction install(DeploymentDetails deploymentDetails, GenerateService.ResolvedConfiguration resolvedConfiguration, InstallableServiceProvider serviceProvider) {
    List<InstallableService> enabledServices = serviceProvider.getInstallableServices()
        .stream()
        .filter(i -> resolvedConfiguration.getServiceSettings(i.getService()).isEnabled())
        .collect(Collectors.toList());

    List<String> installCommands = enabledServices
        .stream()
        .map(i -> String.join("\n", i.installArtifactCommand(deploymentDetails), i.stageProfilesCommand(resolvedConfiguration)))
        .collect(Collectors.toList());

    String installCommand = serviceProvider.getInstallCommand(resolvedConfiguration, installCommands);
    RemoteAction result = new RemoteAction();
    result.setAutoRun(true);
    result.setScript(installCommand);
    return result;
  }
}
