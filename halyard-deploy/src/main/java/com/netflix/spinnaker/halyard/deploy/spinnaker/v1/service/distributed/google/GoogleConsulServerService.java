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
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConsulServerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.consul.ConsulApi;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Component
@Data
public class GoogleConsulServerService extends ConsulServerService
    implements GoogleDistributedService<ConsulApi> {
  @Delegate @Autowired GoogleDistributedServiceDelegate googleDistributedServiceDelegate;

  @Override
  public String getDefaultInstanceType() {
    return "n1-standard-1";
  }

  @Autowired
  public List<String> getScopes() {
    List<String> scopes = GoogleDistributedService.super.getScopes();
    scopes.add("https://www.googleapis.com/auth/compute.readonly");
    return scopes;
  }

  @Override
  public Settings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    Settings settings = new Settings();
    settings
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setLocation("us-central1-f")
        .setEnabled(true);
    return settings;
  }

  @Override
  public List<ConfigSource> stageProfiles(
      AccountDeploymentDetails<GoogleAccount> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration) {
    /* consul server may not stage profiles, it may be a backend for the config server */
    return new ArrayList<>();
  }

  public String getArtifactId(String deploymentName) {
    return GoogleDistributedService.super.getArtifactId(deploymentName);
  }

  final DeployPriority deployPriority = new DeployPriority(9);
  final boolean requiredToBootstrap = true;
}
