/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.telemetry;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.Telemetry;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class TelemetryCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "telemetry";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Show Spinnaker's telemetry settings.";

  public TelemetryCommand() {
    registerSubcommand(new TelemetryEditCommand());
    registerSubcommand(new TelemetryEnableDisableCommandBuilder().setEnable(true).build());
    registerSubcommand(new TelemetryEnableDisableCommandBuilder().setEnable(false).build());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    new OperationHandler<Telemetry>()
        .setOperation(Daemon.getTelemetry(currentDeployment, !noValidate))
        .setFailureMesssage("Failed to load telemetry.")
        .setSuccessMessage("Configured telemetry: ")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .get();
  }
}
