/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.Halconfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfigs
 * deployments.
 */
@Component
public class DeploymentService {
  @Autowired
  ConfigService configService;

  public DeploymentConfiguration getDeploymentConfiguration(String deployment) {
    Halconfig halconfig = configService.getConfig();
    List<DeploymentConfiguration> matching = halconfig.getDeploymentConfigurations()
        .stream()
        .filter(d -> d.getName().equals(deployment))
        .collect(Collectors.toList());

    switch (matching.size()) {
      case 0:
        throw new IllegalConfigException("Deployment configuration " + deployment + " not found");
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException("Deployment configuration " + deployment + " appears more than once");
    }
  }

  public List<DeploymentConfiguration> getDeploymentConfigurations() {
    return configService.getConfig().getDeploymentConfigurations();
  }
}
