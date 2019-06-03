/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.deploy.sizing;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.CustomSizing;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractComponentSizingUpdateCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName;

  @Getter(AccessLevel.PUBLIC)
  protected SpinnakerService.Type spinnakerService;

  public AbstractComponentSizingUpdateCommand(
      SpinnakerService.Type spinnakerService, String commandName) {
    this.spinnakerService = spinnakerService;
    this.commandName = commandName;
  }

  @Override
  protected void executeThis() {
    String serviceName = spinnakerService.getCanonicalName();
    String currentDeployment = getCurrentDeployment();
    DeploymentEnvironment deploymentEnvironment =
        new OperationHandler<DeploymentEnvironment>()
            .setFailureMesssage("Failed to get component sizing for service " + serviceName + ".")
            .setOperation(Daemon.getDeploymentEnvironment(currentDeployment, false))
            .get();

    CustomSizing customSizing = deploymentEnvironment.getCustomSizing();
    int originalHash = customSizing.hashCode();

    update(customSizing);

    if (originalHash == customSizing.hashCode()) {
      AnsiUi.failure("Nothing to do.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to " + commandName + " custom component sizings for " + serviceName + ".")
        .setSuccessMessage(
            "Successfully managed to "
                + commandName
                + " the custom component sizings for service "
                + serviceName
                + ".")
        .setOperation(
            Daemon.setDeploymentEnvironment(currentDeployment, !noValidate, deploymentEnvironment))
        .get();
  }

  protected abstract CustomSizing update(CustomSizing customSizing);
}
