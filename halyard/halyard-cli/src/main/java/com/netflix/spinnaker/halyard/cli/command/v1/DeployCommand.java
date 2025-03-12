/*
 * Copyright 2017 Google, Inc.
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
import com.netflix.spinnaker.halyard.cli.command.v1.deploy.*;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class DeployCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "deploy";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Manage the deployment of Spinnaker. This includes where it's deployed,"
          + " what the infrastructure footprint looks like, what the currently running deployment looks like, etc...";

  public DeployCommand() {
    registerSubcommand(new ApplyDeployCommand());
    registerSubcommand(new CollectLogsDeployCommand());
    registerSubcommand(new ConnectDeployCommand());
    registerSubcommand(new RollbackDeployCommand());
    registerSubcommand(new DiffDeployCommand());
    registerSubcommand(new DetailsDeployCommand());
    registerSubcommand(new CleanDeployCommand());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
