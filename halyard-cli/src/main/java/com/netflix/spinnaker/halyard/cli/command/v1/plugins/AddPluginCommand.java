/*
 * Copyright 2019 Armory Inc.
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
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class AddPluginCommand extends AbstractHasPluginCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "add";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Add a plugin";

  @Parameter(
      names = "--manifest-location",
      description = "The location of the plugin's manifest file.",
      required = true)
  private String manifestLocation;

  @Parameter(names = "--enabled", description = "To enable or disable the plugin.")
  private String enabled;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String name = getPluginName();
    Plugin plugin =
        new Plugin()
            .setName(name)
            .setEnabled(isSet(enabled) ? Boolean.parseBoolean(enabled) : false)
            .setManifestLocation(manifestLocation);

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to add plugin: " + name + ".")
        .setSuccessMessage("Successfully added plugin" + name + ".")
        .setOperation(Daemon.addPlugin(currentDeployment, !noValidate, plugin))
        .get();
  }
}
