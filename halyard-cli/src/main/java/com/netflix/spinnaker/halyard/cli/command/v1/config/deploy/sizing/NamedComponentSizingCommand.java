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
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.deploy.ha.AbstractHaServiceCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.deploy.ha.ClouddriverHaServiceEditCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.deploy.ha.HaServiceEnableDisableCommandBuilder;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaService;
import com.netflix.spinnaker.halyard.config.model.v1.node.CustomSizing;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.Map;
import java.util.function.Supplier;

import static com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils.Format.STRING;

@Parameters(separators = "=")
public class NamedComponentSizingCommand extends AbstractConfigCommand {

  @Getter(AccessLevel.PUBLIC)
  private SpinnakerService.Type spinnakerService;

  public NamedComponentSizingCommand(SpinnakerService.Type spinnakerService) {
    this.spinnakerService = spinnakerService;
    registerSubcommand(new ComponentSizingEditCommand(spinnakerService));
    registerSubcommand(new ComponentSizingDeleteCommand(spinnakerService));
  }

  @Override
  public String getCommandName() {
    return spinnakerService.getCanonicalName();
  }

  @Override
  protected String getShortDescription() {
    return "Manage and view Spinnaker component sizing configuration for " + spinnakerService.getCanonicalName();
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    new OperationHandler<Map>()
        .setFailureMesssage("Failed to get component sizing for service " + spinnakerService + ".")
        .setSuccessMessage("Successfully got component sizing for service " + spinnakerService + ".")
        .setFormat(STRING)
        .setUserFormatted(true)
        .setOperation(() -> Daemon.getDeploymentEnvironment(currentDeployment, !noValidate).get().getCustomSizing().get(spinnakerService.getServiceName()))
        .get();
  }
}
