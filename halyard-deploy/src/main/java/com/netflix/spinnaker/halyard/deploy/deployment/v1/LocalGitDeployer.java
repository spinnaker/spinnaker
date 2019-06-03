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
 *
 */

package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.LocalServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.git.LocalGitService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.git.LocalGitServiceProvider;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LocalGitDeployer extends LocalDeployer {

  @Override
  public RemoteAction prep(
      LocalServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    LocalGitServiceProvider localGitServiceProvider = (LocalGitServiceProvider) serviceProvider;
    List<LocalGitService> enabledServices =
        localGitServiceProvider.getLocalGitServices(serviceTypes);

    List<String> prepCommands =
        enabledServices.stream()
            .filter(
                i -> {
                  ServiceSettings serviceSettings =
                      runtimeSettings.getServiceSettings(i.getService());
                  return serviceSettings != null && !serviceSettings.getSkipLifeCycleManagement();
                })
            .map(
                s -> {
                  s.commitWrapperScripts();
                  return s.prepArtifactCommand(deploymentDetails);
                })
            .collect(Collectors.toList());

    String prepCommand = localGitServiceProvider.getPrepCommand(deploymentDetails, prepCommands);

    RemoteAction result = new RemoteAction();
    result.setAutoRun(true);
    result.setScript(prepCommand);
    return result;
  }
}
