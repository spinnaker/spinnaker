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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DefaultLogCollector;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import java.io.File;

public abstract class DistributedLogCollector<T, A extends Account>
    extends DefaultLogCollector<T, AccountDeploymentDetails<A>> {
  public DistributedLogCollector(HasServiceSettings<T> service) {
    super(service);
  }

  protected abstract HalconfigDirectoryStructure getDirectoryStructure();

  @Override
  public void collectLogs(
      AccountDeploymentDetails<A> details, SpinnakerRuntimeSettings runtimeSettings) {
    DistributedService<T, A> distributedService = (DistributedService<T, A>) getService();
    RunningServiceDetails runningServiceDetails =
        distributedService.getRunningServiceDetails(details, runtimeSettings);
    runningServiceDetails
        .getInstances()
        .values()
        .forEach(
            is ->
                is.stream()
                    .filter(RunningServiceDetails.Instance::isRunning)
                    .forEach(
                        i -> {
                          File outputDir =
                              getDirectoryStructure()
                                  .getServiceLogsPath(
                                      details.getDeploymentName(),
                                      i.getId(),
                                      getService().getCanonicalName())
                                  .toFile();
                          collectInstanceLogs(details, runtimeSettings, outputDir, i.getId());
                        }));
  }

  protected abstract void collectInstanceLogs(
      AccountDeploymentDetails<A> details,
      SpinnakerRuntimeSettings runtimeSettings,
      File instanceOutputDir,
      String instanceId);
}
