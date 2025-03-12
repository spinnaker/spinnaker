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
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import com.netflix.spinnaker.halyard.config.services.v1.PluginService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/plugins")
@RequiredArgsConstructor
public class PluginsController {
  private final PluginService pluginService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final HalconfigParser halconfigParser;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Map<String, Plugin>> getPlugins(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Map<String, Plugin>>builder()
        .getter(() -> pluginService.getPlugins(deploymentName))
        .validator(() -> pluginService.validateAllPlugins(deploymentName))
        .description("Get plugins")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{pluginName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Plugin> getPlugin(
      @PathVariable String deploymentName,
      @PathVariable String pluginName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Plugin>builder()
        .getter(() -> pluginService.getPlugin(deploymentName, pluginName))
        .validator(() -> pluginService.validatePlugin(deploymentName, pluginName))
        .description("Get the " + pluginName + " plugin")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addPlugin(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Plugin plugin) {
    return GenericUpdateRequest.<Plugin>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(t -> pluginService.addPlugin(deploymentName, t))
        .validator(() -> pluginService.validatePlugin(deploymentName, plugin.getId()))
        .description("Add the " + plugin.getId() + " plugin")
        .build()
        .execute(validationSettings, plugin);
  }

  @RequestMapping(value = "/{pluginName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deletePlugin(
      @PathVariable String deploymentName,
      @PathVariable String pluginName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(() -> pluginService.deletePlugin(deploymentName, pluginName))
        .validator(() -> pluginService.validateAllPlugins(deploymentName))
        .description("Delete the " + pluginName + " plugin")
        .build()
        .execute(validationSettings);
  }
}
