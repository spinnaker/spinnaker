/*
 * Copyright 2019 Armory, Inc.
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

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Telemetry;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelemetryService {
  private final LookupService lookupService;
  private final ValidateService validateService;
  private final DeploymentService deploymentService;

  public Telemetry getTelemetry(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setTelemetry();

    return lookupService.getSingularNodeOrDefault(
        filter, Telemetry.class, Telemetry::new, n -> setTelemetry(deploymentName, n));
  }

  public void setTelemetry(String deploymentName, Telemetry telemetry) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setTelemetry(telemetry);
  }

  public void setTelemetryEnabled(String deploymentName, boolean validate, boolean enable) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    Telemetry telemetry = deploymentConfiguration.getTelemetry();
    telemetry.setEnabled(enable);
  }

  public ProblemSet validateTelemetry(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setTelemetry();
    return validateService.validateMatchingFilter(filter);
  }
}
