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

package com.netflix.spinnaker.halyard.cli.command.v1.deploy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class RollbackDeployCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "rollback";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Rollback Spinnaker to the prior version on a selected environment.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      String.join(
          "",
          "This command attempts to rollback Spinnaker to the prior deployed version, depending on how you've configured your deployment. ",
          "Local deployments have their prior packages installed and reconfigured, whereas Distributed deployments are rolled back via a headless ",
          "'bootstrap' deployment of Spinnaker, and don't suffer downtime.");

  @Parameter(
      names = "--service-names",
      description = "When supplied, only install or update the specified Spinnaker services.",
      variableArity = true)
  List<String> serviceNames = new ArrayList<>();

  @Parameter(
      names = "--exclude-service-names",
      description = "When supplied, do not install or update the specified Spinnaker services.",
      variableArity = true)
  List<String> excludeServiceNames = new ArrayList<>();

  @Override
  protected void executeThis() {
    new OperationHandler<Void>()
        .setFailureMesssage("Failed to rollback Spinnaker.")
        .setSuccessMessage("Successfully rolled back Spinnaker.")
        .setOperation(
            Daemon.rollbackDeployment(
                getCurrentDeployment(), !noValidate, serviceNames, excludeServiceNames))
        .get();
  }
}
