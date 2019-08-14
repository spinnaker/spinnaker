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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;

/** This is the base command, where we will register all the subcommands. */
@Parameters(separators = "=")
public class HalCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "hal";

  @Parameter(
      names = "--print-bash-completion",
      description =
          "Print bash command completion. This is used during the installation of Halyard.")
  private boolean printBashCompletion;

  @Parameter(
      names = {"--version", "-v"},
      description = "Version of Halyard.")
  private boolean version;

  @Parameter(
      names = "--ready",
      description =
          "Check if Halyard is up and running. Will exit with non-zero return code when it isn't.")
  private boolean healthy;

  @Parameter(names = "--docs", description = "Print markdown docs for the hal CLI.")
  private boolean docs;

  public HalCommand() {
    registerSubcommand(new AdminCommand());
    registerSubcommand(new BackupCommand());
    registerSubcommand(new ConfigCommand());
    registerSubcommand(new DeployCommand());
    registerSubcommand(new ShutdownCommand());
    registerSubcommand(new TaskCommand());
    registerSubcommand(new VersionCommand());
    registerSubcommand(new SpinCommand());
    registerSubcommand(new PluginCommand());
  }

  static String getVersion() {
    return Optional.ofNullable(HalCommand.class.getPackage().getImplementationVersion())
        .orElse("Unknown");
  }

  @Override
  public String getShortDescription() {
    return "A tool for configuring, installing, and updating Spinnaker.\n\n"
        + "If this is your first time using Halyard to install Spinnaker we recommend that you skim "
        + "the documentation on https://spinnaker.io/reference/halyard/ for some familiarity with the product. If at any "
        + "point you get stuck using 'hal', every command can be suffixed with '--help' for usage "
        + "information.\n";
  }

  @Override
  protected void executeThis() {
    if (docs) {
      System.out.println(generateDocs());
    }

    if (version) {
      System.out.println(getVersion());
    }

    if (printBashCompletion) {
      System.out.println(commandCompletor());
    }

    if (healthy) {
      System.exit(Daemon.isHealthy() ? 0 : -1);
    }

    if (!version && !printBashCompletion && !docs) {
      showHelp();
    }
  }
}
