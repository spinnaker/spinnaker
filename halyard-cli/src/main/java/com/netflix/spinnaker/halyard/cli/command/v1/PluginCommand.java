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

package com.netflix.spinnaker.halyard.cli.command.v1;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.plugins.*;
import com.netflix.spinnaker.halyard.cli.command.v1.repositories.PluginRepositoryCommand;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class PluginCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "plugins";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Show Spinnaker's configured plugins.";

  public PluginCommand() {
    registerSubcommand(new PluginRepositoryCommand());
    registerSubcommand(new AddPluginCommand());
    registerSubcommand(new DeletePluginCommand());
    registerSubcommand(new ListPluginsCommand());
    registerSubcommand(new PluginEnableDisableCommandBuilder().setEnable(true).build());
    registerSubcommand(new PluginEnableDisableCommandBuilder().setEnable(false).build());
    registerSubcommand(new PluginDownloadingEnableDisableCommandBuilder().setEnable(true).build());
    registerSubcommand(new PluginDownloadingEnableDisableCommandBuilder().setEnable(false).build());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
