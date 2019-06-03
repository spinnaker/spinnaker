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
public class CollectLogsDeployCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "collect-logs";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Collect logs from the specified Spinnaker services.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      "This command collects logs from all Spinnaker services, and depending on how it was deployed, it will "
          + "collect logs from sidecars and startup scripts as well.";

  @Parameter(
      names = "--service-names",
      description = "When supplied, logs from only the specified services will be collected.",
      variableArity = true)
  List<String> serviceNames = new ArrayList<>();

  @Parameter(
      names = "--exclude-service-names",
      description = "When supplied, logs from the specified services will be not collected",
      variableArity = true)
  List<String> excludeServiceNames = new ArrayList<>();

  @Override
  protected void executeThis() {
    new OperationHandler<Void>()
        .setFailureMesssage("Failed to collect logs from Spinnaker.")
        .setSuccessMessage("Successfully collected service logs.")
        .setOperation(
            Daemon.collectLogs(
                getCurrentDeployment(), !noValidate, serviceNames, excludeServiceNames))
        .get();
  }
}
