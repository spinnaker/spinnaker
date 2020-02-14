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

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class DeletePluginCommand extends AbstractHasPluginCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Delete a plugin";

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    Plugin plugin = getPlugin();
    String name = plugin.getId();

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to delete plugin " + name + ".")
        .setSuccessMessage("Successfully deleted plugin " + name + ".")
        .setOperation(Daemon.deletePlugin(currentDeployment, name, !noValidate))
        .get();
  }
}
