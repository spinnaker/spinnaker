/*
 * Copyright 2016 Google, Inc.
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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.Setter;

import java.util.Map;

abstract class NestableCommand {
  @Setter
  protected JCommander commander;
  private GlobalOptions globalOptions;

  @Parameter(names = { "-h", "--help" }, help = true)
  private boolean help;

  public NestableCommand(GlobalOptions globalOptions) {
    this.globalOptions = globalOptions;
  }

  /**
   * This recusively walks the chain of subcommands, until it finds the last in the chain, and runs executeThis.
   *
   * @see NestableCommand#executeThis()
   */
  public void execute() {
    String subCommand = commander.getParsedCommand();
    if (subCommand == null) {
      executeThis();
    } else {
      getSubcommands().get(subCommand).execute();
    }
  }

  abstract protected void executeThis();
  abstract protected Map<String, NestableCommand> getSubcommands();
  abstract protected String getCommandName();

  /**
   * Register all subcommands with this class's commander, and then recursively set the subcommands.
   */
  protected void configureSubcommands() {
    for (NestableCommand subCommand: getSubcommands().values()) {
      commander.addCommand(subCommand.getCommandName(), subCommand);
      // We need to provide the subcommand with its own commander before recursively populating its subcommands, since
      // they need to be registered with this subcommander we retrieve here.
      JCommander subCommander = commander.getCommands().get(subCommand.getCommandName());
      subCommand.setCommander(subCommander);
      subCommand.configureSubcommands();
    }
  }
}
