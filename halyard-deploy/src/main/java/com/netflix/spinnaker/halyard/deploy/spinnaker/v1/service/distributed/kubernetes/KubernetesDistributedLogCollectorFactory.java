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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedLogCollector;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.SidecarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class KubernetesDistributedLogCollectorFactory {
  public <T> DistributedLogCollector build(HasServiceSettings<T> service) {
    return new KubernetesDistributedLogCollector<>(service);
  }

  @Autowired
  HalconfigDirectoryStructure directoryStructure;

  private class KubernetesDistributedLogCollector<T> extends DistributedLogCollector<T, KubernetesAccount> {
    KubernetesDistributedLogCollector(HasServiceSettings<T> service) {
      super(service);
    }

    @Override
    protected HalconfigDirectoryStructure getDirectoryStructure() {
      return directoryStructure;
    }

    @Override
    protected void collectInstanceLogs(
        AccountDeploymentDetails<KubernetesAccount> details,
        SpinnakerRuntimeSettings runtimeSettings,
        File instanceOutputDir,
        String instanceId) {
      ServiceSettings settings = runtimeSettings.getServiceSettings(getService());
      DaemonTaskHandler.newStage("Reading " + getService().getCanonicalName() + " logs");
      DaemonTaskHandler.message("Reading container " + getServiceName() + "'s logs");
      KubernetesProviderUtils.storeInstanceLogs(
          DaemonTaskHandler.getJobExecutor(),
          details,
          settings.getLocation(),
          instanceId,
          getServiceName(),
          instanceOutputDir
      );

      DistributedService service = (DistributedService<T, KubernetesAccount>) getService();

      for (Object rawSidecarService : service.getSidecars(runtimeSettings)) {
        SidecarService sidecarService = (SidecarService) rawSidecarService;
        String sidecarName = sidecarService.getService().getServiceName();
        DaemonTaskHandler.message("Reading container " + sidecarName + "'s logs");
        KubernetesProviderUtils.storeInstanceLogs(
            DaemonTaskHandler.getJobExecutor(),
            details,
            settings.getLocation(),
            instanceId,
            sidecarName,
            instanceOutputDir
        );
      }
    }
  }
}
