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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.google;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ClouddriverBootstrapService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.SidecarService;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class GoogleClouddriverBootstrapService extends ClouddriverBootstrapService
    implements GoogleDistributedService<ClouddriverBootstrapService.Clouddriver> {
  final DeployPriority deployPriority = new DeployPriority(6);
  final boolean requiredToBootstrap = true;

  @Delegate @Autowired GoogleDistributedServiceDelegate googleDistributedServiceDelegate;

  @Override
  public List<SidecarService> getSidecars(SpinnakerRuntimeSettings runtimeSettings) {
    List<SidecarService> result = GoogleDistributedService.super.getSidecars(runtimeSettings);
    result.add(getConsulClientService());
    result.add(getVaultClientService());
    return result;
  }

  @Override
  public Settings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    List<String> profiles = new ArrayList<>();
    profiles.add("bootstrap");
    profiles.add("bootstrap-local");
    Settings settings = new Settings(profiles);
    settings
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setAddress(buildAddress())
        .setLocation("us-central1-f")
        .setEnabled(true);
    return settings;
  }
}
