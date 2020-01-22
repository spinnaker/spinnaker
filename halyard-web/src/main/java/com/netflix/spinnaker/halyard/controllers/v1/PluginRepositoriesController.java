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

package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginRepository;
import com.netflix.spinnaker.halyard.config.services.v1.PluginRepositoryService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/pluginRepositories")
@RequiredArgsConstructor
public class PluginRepositoriesController {
  private final PluginRepositoryService pluginRepositoryService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final HalconfigParser halconfigParser;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Map<String, PluginRepository>> getPluginRepositories(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Map<String, PluginRepository>>builder()
        .getter(() -> pluginRepositoryService.getPluginRepositories(deploymentName))
        .validator(() -> pluginRepositoryService.validateAllPluginRepositories(deploymentName))
        .description("Get plugin repositories")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{pluginRepositoryName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, PluginRepository> getPluginRepository(
      @PathVariable String deploymentName,
      @PathVariable String pluginRepositoryName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<PluginRepository>builder()
        .getter(
            () -> pluginRepositoryService.getPluginRepository(deploymentName, pluginRepositoryName))
        .validator(
            () ->
                pluginRepositoryService.validatePluginRepository(
                    deploymentName, pluginRepositoryName))
        .description("Get the " + pluginRepositoryName + " plugin repository")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{pluginRepositoryName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setPluginRepository(
      @PathVariable String deploymentName,
      @PathVariable String pluginRepositoryName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody PluginRepository pluginRepository) {
    return GenericUpdateRequest.<PluginRepository>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(
            t ->
                pluginRepositoryService.setPluginRepository(
                    deploymentName, pluginRepositoryName, t))
        .validator(
            () ->
                pluginRepositoryService.validatePluginRepository(
                    deploymentName, pluginRepositoryName))
        .description("Edit the " + pluginRepositoryName + " plugin repository")
        .build()
        .execute(validationSettings, pluginRepository);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addPluginRepository(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody PluginRepository pluginRepository) {
    return GenericUpdateRequest.<PluginRepository>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(t -> pluginRepositoryService.addPluginRepository(deploymentName, t))
        .validator(
            () ->
                pluginRepositoryService.validatePluginRepository(
                    deploymentName, pluginRepository.getId()))
        .description("Add the " + pluginRepository.getId() + " plugin repository")
        .build()
        .execute(validationSettings, pluginRepository);
  }

  @RequestMapping(value = "/{pluginRepositoryName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deletePluginRepository(
      @PathVariable String deploymentName,
      @PathVariable String pluginRepositoryName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(
            () ->
                pluginRepositoryService.deletePluginRepository(
                    deploymentName, pluginRepositoryName))
        .validator(() -> pluginRepositoryService.validateAllPluginRepositories(deploymentName))
        .description("Delete the " + pluginRepositoryName + " plugin repository")
        .build()
        .execute(validationSettings);
  }
}
