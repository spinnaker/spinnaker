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

package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentEnvironmentService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/deploymentEnvironment")
public class DeploymentEnvironmentController {
  private final HalconfigParser halconfigParser;
  private final DeploymentEnvironmentService deploymentEnvironmentService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, DeploymentEnvironment> getDeploymentEnvironment(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<DeploymentEnvironment>builder()
        .getter(() -> deploymentEnvironmentService.getDeploymentEnvironment(deploymentName))
        .validator(() -> deploymentEnvironmentService.validateDeploymentEnvironment(deploymentName))
        .description("Get the deployment environment")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setDeploymentEnvironment(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody DeploymentEnvironment deploymentEnvironment) {
    return GenericUpdateRequest.<DeploymentEnvironment>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(d -> deploymentEnvironmentService.setDeploymentEnvironment(deploymentName, d))
        .validator(() -> deploymentEnvironmentService.validateDeploymentEnvironment(deploymentName))
        .description("Edit the deployment environment")
        .build()
        .execute(validationSettings, deploymentEnvironment);
  }
}
