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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ClouddriverService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.RedisService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.LocalService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.LocalServiceProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

@Component
@Slf4j
public class LocalDeployer implements Deployer<LocalServiceProvider, DeploymentDetails> {
  @Override
  public RemoteAction deploy(
      LocalServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      List<SpinnakerService.Type> serviceTypes,
      boolean waitForCompletion,
      Optional<Integer> waitForCompletionTimeoutMinutes) {
    List<LocalService> enabledServices =
        serviceProvider.getLocalServices(serviceTypes).stream()
            .filter(i -> resolvedConfiguration.getServiceSettings(i.getService()) != null)
            .filter(
                i -> {
                  ServiceSettings serviceSettings =
                      resolvedConfiguration.getServiceSettings(i.getService());
                  return serviceSettings != null && serviceSettings.getEnabled();
                })
            .collect(Collectors.toList());

    Map<String, String> installCommands =
        enabledServices.stream()
            .filter(
                i ->
                    !resolvedConfiguration
                        .getServiceSettings(i.getService())
                        .getSkipLifeCycleManagement())
            .reduce(
                new HashMap<>(),
                (commands, installable) -> {
                  String command =
                      String.join(
                          "\n",
                          installable.installArtifactCommand(deploymentDetails),
                          installable.stageProfilesCommand(
                              deploymentDetails, resolvedConfiguration));
                  commands.put(installable.getService().getCanonicalName(), command);
                  return commands;
                },
                (m1, m2) -> {
                  m1.putAll(m2);
                  return m1;
                });

    String installCommand =
        serviceProvider.getInstallCommand(
            deploymentDetails, resolvedConfiguration, installCommands);
    RemoteAction result = new RemoteAction();
    result.setAutoRun(true);
    result.setScript(installCommand);
    return result;
  }

  @Override
  public void rollback(
      LocalServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    throw new HalException(
        Problem.Severity.FATAL, "No support for rolling back local deployments yet.");
  }

  @Override
  public void collectLogs(
      LocalServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    for (LocalService localService : serviceProvider.getLocalServices(serviceTypes)) {
      localService.collectLogs(deploymentDetails, runtimeSettings);
    }
  }

  @Override
  public RemoteAction connectCommand(
      LocalServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    RemoteAction result = new RemoteAction();
    result.setScript(
        String.join(
            "\n",
            "#!/usr/bin/env bash",
            "",
            "echo \"Spinnaker is installed locally on this machine - no work to do.\""));
    return result;
  }

  @Override
  public void flushInfrastructureCaches(
      LocalServiceProvider serviceProvider,
      DeploymentDetails deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings) {
    try {
      Jedis jedis =
          new Jedis(
              runtimeSettings
                  .getServiceSettings(
                      (SpinnakerService)
                          serviceProvider.getLocalService(SpinnakerService.Type.REDIS))
                  .getBaseUrl());

      RedisService.flushKeySpace(jedis, ClouddriverService.REDIS_KEY_SPACE);
    } catch (Exception e) {
      throw new HalException(
          Problem.Severity.FATAL, "Unable to flush key space: " + e.getMessage(), e);
    }
  }
}
