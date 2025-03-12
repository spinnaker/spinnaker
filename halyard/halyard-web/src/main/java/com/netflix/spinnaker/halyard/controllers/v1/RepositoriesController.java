/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Repository;
import com.netflix.spinnaker.halyard.config.services.v1.RepositoryService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericEnableDisableRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/repository")
public class RepositoriesController {
  private final HalconfigParser halconfigParser;
  private final RepositoryService repositoryService;

  @RequestMapping(value = "/{repositoryName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Repository> repository(
      @PathVariable String deploymentName,
      @PathVariable String repositoryName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Repository>builder()
        .getter(() -> repositoryService.getRepository(deploymentName, repositoryName))
        .validator(() -> repositoryService.validateRepository(deploymentName, repositoryName))
        .description("Get " + repositoryName + " repository")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{repositoryName:.+}/enabled", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setEnabled(
      @PathVariable String deploymentName,
      @PathVariable String repositoryName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    return GenericEnableDisableRequest.builder(halconfigParser)
        .updater(e -> repositoryService.setEnabled(deploymentName, repositoryName, e))
        .validator(() -> repositoryService.validateRepository(deploymentName, repositoryName))
        .description("Edit " + repositoryName + " settings")
        .build()
        .execute(validationSettings, enabled);
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Repository>> repositories(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<Repository>>builder()
        .getter(() -> repositoryService.getAllRepositories(deploymentName))
        .validator(() -> repositoryService.validateAllRepositories(deploymentName))
        .description("Get all Repositories services")
        .build()
        .execute(validationSettings);
  }
}
