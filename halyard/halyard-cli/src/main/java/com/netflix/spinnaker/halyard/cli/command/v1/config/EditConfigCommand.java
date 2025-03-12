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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditConfigCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Configure top-level, global configuration parameters.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      "Configure top-level, global configuration parameters. The properties edited here affect all "
          + "Spinnaker subcomponents.";

  @Parameter(
      names = "--timezone",
      description =
          "The timezone your Spinnaker instance runs in. This affects what the UI will display as well as how CRON triggers are run.")
  private String timezone;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    DeploymentConfiguration deploymentConfiguration =
        new OperationHandler<DeploymentConfiguration>()
            .setOperation(Daemon.getDeploymentConfiguration(currentDeployment, false))
            .setFailureMesssage("Failed to get your deployment configuration for edits.")
            .get();

    int hash = deploymentConfiguration.hashCode();

    deploymentConfiguration.setTimezone(
        isSet(timezone) ? timezone : deploymentConfiguration.getTimezone());

    if (deploymentConfiguration.hashCode() == hash) {
      AnsiUi.error("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(
            Daemon.setDeploymentConfiguration(
                currentDeployment, !noValidate, deploymentConfiguration))
        .setFailureMesssage("Failed to apply edits to your deployment configuration")
        .setSuccessMessage("Successfully edited your deployment configuration")
        .get();
  }
}
