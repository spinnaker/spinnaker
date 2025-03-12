/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.controllers;

import com.netflix.spinnaker.orca.capabilities.CapabilitiesService;
import com.netflix.spinnaker.orca.capabilities.models.ExpressionCapabilityResult;
import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorCapabilities;
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentMonitorDefinition;
import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller intended for querying various capabilities of orca */
@RestController
public class CapabilitiesController {
  private CapabilitiesService orcaCapabilities;
  private DeploymentMonitorCapabilities deploymentMonitorCapabilities;

  public CapabilitiesController(
      CapabilitiesService orcaCapabilities,
      Optional<DeploymentMonitorCapabilities> deploymentMonitorCapabilities) {
    this.deploymentMonitorCapabilities = deploymentMonitorCapabilities.orElse(null);
    this.orcaCapabilities = orcaCapabilities;
  }

  @GetMapping("/capabilities/deploymentMonitors")
  public List<DeploymentMonitorDefinition> getDeploymentMonitors() {
    if (deploymentMonitorCapabilities == null) {
      return Collections.emptyList();
    }

    return deploymentMonitorCapabilities.getDeploymentMonitors();
  }

  @GetMapping("/capabilities/expressions")
  public ExpressionCapabilityResult getExpressionCapabilities() {
    return orcaCapabilities.getExpressionCapabilities();
  }
}
