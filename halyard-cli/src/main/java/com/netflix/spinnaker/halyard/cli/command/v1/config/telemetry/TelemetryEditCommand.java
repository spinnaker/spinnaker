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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.Telemetry;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class TelemetryEditCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit Spinnaker's telemetry settings.";

  @Parameter(names = "--endpoint", description = "Set the endpoint for telemetry metrics.")
  private String endpoint;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    Telemetry telemetry =
        new OperationHandler<Telemetry>()
            .setOperation(Daemon.getTelemetry(currentDeployment, false))
            .setFailureMesssage("Failed to load telemetry settings.")
            .get();

    if (isSet(endpoint)) {
      telemetry.setEndpoint(endpoint);
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setTelemetry(currentDeployment, !noValidate, telemetry))
        .setFailureMesssage("Failed to edit telemetry settings.")
        .setSuccessMessage("Successfully edited telemetry settings.")
        .get();
  }
}
