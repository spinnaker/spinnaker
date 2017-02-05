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
      description = "Print bash command completion."
  )
  private boolean printBashCompletion;

  public HalCommand() {
    registerSubcommand(new ConfigCommand());
    registerSubcommand(new DeployCommand());
    registerSubcommand(new VersionsCommand());
  }

  @Override
  public String getDescription() {
    return "Manage Spinnaker's configuration and updates";
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
