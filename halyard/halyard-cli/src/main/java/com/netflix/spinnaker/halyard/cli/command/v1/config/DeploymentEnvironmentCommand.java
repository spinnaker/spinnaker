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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.deploy.ha.HaServiceCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.deploy.sizing.ComponentSizingCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class DeploymentEnvironmentCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "deploy";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Display the configured Spinnaker deployment.";

  public DeploymentEnvironmentCommand() {
    registerSubcommand(new EditDeploymentEnvironmentCommand());
    registerSubcommand(new HaServiceCommand());
    registerSubcommand(new ComponentSizingCommand());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    new OperationHandler<DeploymentEnvironment>()
        .setFailureMesssage("Failed to get your deployment environment.")
        .setSuccessMessage("Retrieved configured deployment environment.")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .setOperation(Daemon.getDeploymentEnvironment(currentDeployment, !noValidate))
        .get();
  }
}
