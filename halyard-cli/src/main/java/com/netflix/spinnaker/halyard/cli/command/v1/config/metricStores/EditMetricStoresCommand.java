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

package com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditMetricStoresCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Configure global metric stores properties.";

  @Parameter(
      names = "--period",
      required = true,
      description = "Set the polling period for the monitoring daemon.")
  private Integer period;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    MetricStores metricStores =
        new OperationHandler<MetricStores>()
            .setOperation(Daemon.getMetricStores(currentDeployment, false))
            .setFailureMesssage("Failed to load metric stores.")
            .get();

    int originalHash = metricStores.hashCode();

    metricStores.setPeriod(isSet(period) ? period : metricStores.getPeriod());

    if (originalHash == metricStores.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setMetricStores(currentDeployment, !noValidate, metricStores))
        .setFailureMesssage("Failed to edit metric stores.")
        .setSuccessMessage("Successfully updated metric stores.")
        .get();
  }
}
