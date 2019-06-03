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

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class is meant to be autowired into any controller or service that needs to read the current
 * halconfig.
 */
@Component
public class ConfigService {
  @Autowired private HalconfigParser halconfigParser;

  public Halconfig getConfig() {
    return halconfigParser.getHalconfig();
  }

  public String getCurrentDeployment() {
    String result = getConfig().getCurrentDeployment();
    if (result == null || result.isEmpty()) {
      throw new IllegalConfigException(
          new ConfigProblemBuilder(Severity.FATAL, "No deployment has been set").build());
    }

    return result;
  }

  public void setCurrentDeployment(String deploymentName) {
    Halconfig config = getConfig();
    boolean found =
        config.getDeploymentConfigurations().stream()
            .anyMatch(c -> c.getName().equals(deploymentName));

    if (!found) {
      config
          .getDeploymentConfigurations()
          .add(new DeploymentConfiguration().setName(deploymentName));
    }

    config.setCurrentDeployment(deploymentName);
  }
}
