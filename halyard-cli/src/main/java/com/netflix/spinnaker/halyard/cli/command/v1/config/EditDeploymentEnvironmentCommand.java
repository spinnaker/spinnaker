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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.DeploymentTypeConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters()
public class EditDeploymentEnvironmentCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  final private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  final private String description = "Edit Spinnaker's deployment footprint and configuration.";

  @Parameter(
      names = "--account-name",
      description = "The Spinnaker account that Spinnaker will be deployed to, assuming you are running"
          + "a deployment of Spinnaker that requires an active cloud provider."
  )
  private String accountName;

  @Parameter(
      names = "--type",
      description = "Flotilla: Deploy Spinnaker with one server group per microservice, and a single shared Redis.\n"
          + "LocalhostDebian: Download and run the Spinnaker debians on the machine running the Daemon.",
      converter = DeploymentTypeConverter.class
  )
  private DeploymentType type;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    DeploymentEnvironment deploymentEnvironment = Daemon.getDeploymentEnvironment(currentDeployment, !noValidate);

    deploymentEnvironment.setAccountName(isSet(accountName) ? accountName : deploymentEnvironment.getAccountName());
    deploymentEnvironment.setType(type != null ? type : deploymentEnvironment.getType());

    Daemon.setDeploymentEnvironment(currentDeployment, !noValidate, deploymentEnvironment);

    AnsiUi.success("Successfully updated your configured deployment.");
  }
}
