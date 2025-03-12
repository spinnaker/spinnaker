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
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService.ResolvedConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerServiceProvider;
import java.util.List;
import java.util.Optional;

public interface Deployer<S extends SpinnakerServiceProvider<D>, D extends DeploymentDetails> {
  RemoteAction deploy(
      S serviceProvider,
      D deploymentDetails,
      ResolvedConfiguration resolvedConfiguration,
      List<SpinnakerService.Type> serviceTypes,
      boolean waitForCompletion,
      Optional<Integer> waitForCompletionTimeoutMinutes);

  void rollback(
      S serviceProvider,
      D deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes);

  void collectLogs(
      S serviceProvider,
      D deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes);

  RemoteAction connectCommand(
      S serviceProvider,
      D deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes);

  default RemoteAction clean(
      S serviceProvider, D deploymentDetails, SpinnakerRuntimeSettings runtimeSettings) {
    return serviceProvider.clean(deploymentDetails, runtimeSettings);
  }

  void flushInfrastructureCaches(
      S serviceProvider, D deploymentDetails, SpinnakerRuntimeSettings runtimeSettings);

  default RemoteAction prep(
      S serviceProvider,
      D deploymentDetails,
      SpinnakerRuntimeSettings runtimeSettings,
      List<SpinnakerService.Type> serviceTypes) {
    RemoteAction result = new RemoteAction();
    result.setAutoRun(true);
    result.setScript("");
    return result;
  }

  default void deleteDisabledServices(
      S serviceProvider,
      D deploymentDetails,
      ResolvedConfiguration resolvedConfiguration,
      List<SpinnakerService.Type> serviceTypes) {}
}
