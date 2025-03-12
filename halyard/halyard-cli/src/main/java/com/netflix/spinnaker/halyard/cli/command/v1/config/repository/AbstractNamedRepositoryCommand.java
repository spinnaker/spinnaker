/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.repository;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.Repository;

@Parameters(separators = "=")
public abstract class AbstractNamedRepositoryCommand extends AbstractRepositoryCommand {
  @Override
  public String getCommandName() {
    return getRepositoryName();
  }

  @Override
  public String getShortDescription() {
    return "Manage and view Spinnaker configuration for the " + getRepositoryName() + " repository";
  }

  protected AbstractNamedRepositoryCommand() {
    registerSubcommand(
        new RepositoryEnableDisableCommandBuilder()
            .setRepositoryName(getRepositoryName())
            .setEnable(false)
            .build());

    registerSubcommand(
        new RepositoryEnableDisableCommandBuilder()
            .setRepositoryName(getRepositoryName())
            .setEnable(true)
            .build());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String repositoryName = getRepositoryName();
    new OperationHandler<Repository>()
        .setOperation(Daemon.getRepository(currentDeployment, repositoryName, !noValidate))
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .setSuccessMessage("Configured " + repositoryName + " repository: ")
        .setFailureMesssage("Failed to load repository " + repositoryName + ".")
        .get();
  }
}
