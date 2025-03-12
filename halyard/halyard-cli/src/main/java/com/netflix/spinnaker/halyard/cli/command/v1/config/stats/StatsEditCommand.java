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

package com.netflix.spinnaker.halyard.cli.command.v1.config.stats;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.Stats;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class StatsEditCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit Spinnaker's stats settings.";

  @Parameter(names = "--endpoint", description = "Set the endpoint for stats metrics.")
  private String endpoint;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    Stats stats =
        new OperationHandler<Stats>()
            .setOperation(Daemon.getStats(currentDeployment, false))
            .setFailureMesssage("Failed to load stats settings.")
            .get();

    if (isSet(endpoint)) {
      stats.setEndpoint(endpoint);
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setStats(currentDeployment, !noValidate, stats))
        .setFailureMesssage("Failed to edit stats settings.")
        .setSuccessMessage("Successfully edited stats settings.")
        .get();
  }
}
