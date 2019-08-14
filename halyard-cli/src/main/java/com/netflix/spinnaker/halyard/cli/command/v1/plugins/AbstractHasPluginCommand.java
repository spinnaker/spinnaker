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
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import java.util.ArrayList;
import java.util.List;

/** An abstract definition for commands that accept plugins as a main parameter */
@Parameters(separators = "=")
public abstract class AbstractHasPluginCommand extends AbstractConfigCommand {
  @Parameter(description = "The name of the plugin to operate on.", arity = 1)
  private List<String> plugins = new ArrayList<>();

  @Override
  public String getMainParameter() {
    return "plugin";
  }

  public Plugin getPlugin() {
    return new OperationHandler<Plugin>()
        .setFailureMesssage("Failed to get plugin")
        .setOperation(Daemon.getPlugin(getCurrentDeployment(), plugins.get(0), false))
        .get();
  }

  public String getPluginName() {
    switch (plugins.size()) {
      case 0:
        throw new IllegalArgumentException("No plugin supplied");
      case 1:
        return plugins.get(0);
      default:
        throw new IllegalArgumentException("More than one plugin supplied");
    }
  }
}
