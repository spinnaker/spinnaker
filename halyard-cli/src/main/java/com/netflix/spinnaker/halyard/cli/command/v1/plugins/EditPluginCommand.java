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

package com.netflix.spinnaker.halyard.cli.command.v1.plugins;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginExtension;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditPluginCommand extends AbstractHasPluginCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit a plugin";

  @Parameter(
      names = "--ui-resource-location",
      description = "The location of the plugin's ui resource.",
      required = false)
  private String uiResourceLocation;

  @Parameter(names = "--version", description = "The plugin version to use", required = false)
  private String version;

  @Parameter(names = "--enabled", description = "To enable or disable the plugin.")
  private String enabled;

  @Parameter(names = "--extensions", description = "A comma separated list of extensions to create")
  private String extensions;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    Plugin plugin = getPlugin();

    plugin.setEnabled(isSet(enabled) ? Boolean.parseBoolean(enabled) : plugin.getEnabled());
    plugin.setUiResourceLocation(
        isSet(uiResourceLocation) ? uiResourceLocation : plugin.getUiResourceLocation());
    plugin.setVersion(isSet(version) ? version : plugin.getVersion());

    for (String pluginExtensionName : extensions.split(",")) {
      plugin
          .getExtensions()
          .putIfAbsent(pluginExtensionName, new PluginExtension().setId(pluginExtensionName));
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit plugin " + plugin.getId() + ".")
        .setSuccessMessage("Successfully edited plugin " + plugin.getId() + ".")
        .setOperation(Daemon.setPlugin(currentDeployment, plugin.getId(), !noValidate, plugin))
        .get();
  }
}
