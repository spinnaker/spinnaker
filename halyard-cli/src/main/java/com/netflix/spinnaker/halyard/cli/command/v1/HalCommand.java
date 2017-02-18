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
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * This is the base command, where we will register all the subcommands.
 */
public class HalCommand extends NestableCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "hal";

  @Parameter(
      names = "--print-bash-completion",
      description = "Print bash command completion. This is used during the installation of Halyard."
  )
  private boolean printBashCompletion;

  public HalCommand() {
    registerSubcommand(new ConfigCommand());
    registerSubcommand(new DeployCommand());
    registerSubcommand(new VersionsCommand());
  }

  @Override
  public String getDescription() {
    return "A tool for configuring, installing, and updating Spinnaker.\n\n"
        + "If this is your first time using Halyard to install Spinnaker we recommend that you skim "
        + "the documentation on www.spinnaker.io/docs for some familiarity with the product. If at any "
        + "point you get stuck using 'hal', every command can be suffixed with '--help' for usage "
        + "information. Once you are ready, these are the steps you need to follow to get an "
        + "initial configuration of Spinnaker up and running:\n\n"
        + "1. Enable the cloud provider(s) you want to deploy to:\n"
        + "  $ hal config provider $PROVIDER enable\n"
        + "2. Create Spinnaker accounts for the provider(s) you want to use:\n"
        + "  $ hal config provider $PROVIDER account add my-account-name --help\n"
        + "3. Set a storage source for Spinnaker metadata:\n"
        + "  $ hal config storage edit --help\n"
        + "4. (Optional) Set feature flags:\n"
        + "  $ hal config features edit --help\n"
        + "5. (Optional) Configure Spinnaker's image bakery for your provider(s):\n"
        + "  $ hal config provider $PROVIDER bakery --help\n"
        + "6. (Optional) Configure Spinnaker's security settings (authn, authz & ssl):\n"
        + "  $ hal config security edit --help\n"
        + "7. (Optional) Configure Spinnaker's deployment profile:\n"
        + "  $ hal config deploy edit --help\n"
        + "8. (Optional) Generate all of Spinnaker's configuration:\n"
        + "  $ hal config generate\n"
        + "9. Deploy Spinnaker:\n"
        + "  $ hal deploy run\n";
  }

  @Override
  protected void executeThis() {
    if (printBashCompletion) {
      System.out.println(commandCompletor());
    } else {
      showHelp();
    }
  }
}
